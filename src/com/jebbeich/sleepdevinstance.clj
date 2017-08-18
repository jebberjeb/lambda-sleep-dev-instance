(ns com.jebbeich.sleepdevinstance
  (:gen-class
   :methods [^:static [handler [Object com.amazonaws.services.lambda.runtime.Context] Object]])
  (:import
    (com.amazonaws.services.lambda.runtime
      Context)
    (com.amazonaws.services.ec2
      AmazonEC2ClientBuilder)
    (com.amazonaws.services.ec2.model
      InstanceType
      RunInstancesRequest
      StopInstancesRequest
      Tag)
    (com.amazonaws.services.sns
      AmazonSNSClientBuilder)
    (com.amazonaws.services.simplesystemsmanagement
      AWSSimpleSystemsManagementClientBuilder)
    (com.amazonaws.services.simplesystemsmanagement.model
      GetCommandInvocationRequest
      SendCommandRequest
      SendCommandResult)))

(def fsm {nil                    {:init              'initializing}
          'initializing          {:success           'finding-instance}
          'finding-instance      {:found             'getting-state
                                  :not-found         'done}
          'getting-state         {:stopped           'done
                                  :stopping          'done
                                  :running           'getting-tmux-state}
          'getting-tmux-state    {:stopped           'stopping
                                  :running           'notifying-cannot-stop}
          'notifying-cannot-stop {:success           'done}
          'stopping              {:success           'notifying-stopped}
          'notifying-stopped     {:success           'done}})

;; ***** EC2 *****

(defn client
  []
  (AmazonEC2ClientBuilder/defaultClient))

(defn reservations
  [client]
  (-> client
      .describeInstances
      .getReservations))

(defn instances
  [client]
  (->> client
       reservations
       (mapcat #(.getInstances %))))

(defn has-tag?
  [key value instance]
  (some (partial = (Tag. key value)) (.getTags instance)))

(defn dev-instances
  [client]
  (->> client
       instances
       (filter (partial has-tag? "type" "dev"))))

(defn stop-instance
  [client instance-id]
  (.stopInstances client
                  (.withInstanceIds (StopInstancesRequest.) [instance-id])))

;; ***** SSM *****

(defn ssm-client
  []
  (AWSSimpleSystemsManagementClientBuilder/defaultClient))

(defn build-send-command-request
  [instanceids command]
  (-> (SendCommandRequest.)
      (.withInstanceIds instanceids)
      (.withDocumentName "AWS-RunShellScript")
      (.withParameters {"commands" [command]})))

(defn run-command
  [ssm-client send-command-request]
  (->> send-command-request
       (.sendCommand ssm-client)
       .getCommand
       .getCommandId))

(defn build-command-invocation-request
  [instanceid commandid]
  (-> (GetCommandInvocationRequest.)
      (.withInstanceId instanceid)
      (.withCommandId commandid)))

(defn get-command-invocation-output
  [ssm-client command-invocation-request]
  (.getStandardOutputContent
    (.getCommandInvocation ssm-client command-invocation-request)))

(defn wait
  [msec x]
  (Thread/sleep msec)
  x)

(defn run-command-results
  [ssm-client instanceid command]
  (->> (build-send-command-request [instanceid] command)
       (run-command ssm-client)
       (build-command-invocation-request instanceid)
       ;; HACK wait for the commandId to exist. No idea why we have to do this,
       ;; since the commandId has been returned by `run-command`.
       (wait 1000)
       (get-command-invocation-output ssm-client)))

(defn message-terminal
  [ssm-client instanceid message]
  (let [file "/tmp/msg.txt"]
    (run-command-results ssm-client instanceid
                         (format "echo '%s' > %s" message file))
    (run-command-results ssm-client instanceid
                         (format "sudo wall -n %s" file))))

;; ***** SNS *****

(defn sns-client
  []
  (AmazonSNSClientBuilder/defaultClient))

(defn notify
  [sns-client topic-arn message]
  (.publish sns-client topic-arn message))

;; ***** States *****

(defn initializing
  [{:keys [data current-state]}]
  {:data (assoc data
                :client (client)
                :ssm-client (ssm-client)
                :sns-client (sns-client)
                ;; TODO move to external config / param
                :topic-arn "arn:aws:sns:us-east-1:803068370526:NotifyMe")
   :current-state current-state
   :transition :success})

(defn finding-instance
  [{:keys [data current-state]}]
  (let [client (:client data)
        dev-instance (first (dev-instances client))]
    {:data (assoc data :dev-instance dev-instance)
     :current-state current-state
     :transition (if dev-instance :found :not-found)}))

(defn getting-state
  [{:keys [data current-state]}]
  {:data data
   :current-state current-state
   :transition (-> data :dev-instance .getState .getName keyword)})

(defn getting-tmux-state
  [{:keys [data current-state]}]
  (let [{:keys [ssm-client client dev-instance]} data
        output (run-command-results
                 ssm-client (.getInstanceId dev-instance)
                 "sudo su - ubuntu -c \"tmux list-sessions\"")]
    {:data data
     :current-state current-state
     :transition (if (empty? output) :stopped :running)}))

(defn notifying-cannot-stop
  [{:keys [data current-state]}]
  (let [{:keys [ssm-client sns-client dev-instance topic-arn]} data
        instance-id (.getInstanceId dev-instance)]
    (message-terminal
      ssm-client instance-id
      "SleepDevInstance Lambda can not shut down while tmux is running!")
    (notify sns-client topic-arn
            (format "Dev instance %s was not shut down." instance-id))
    {:data data
     :current-state current-state
     :transition :success}))

(defn stopping
  [{:keys [data current-state]}]
  (let [{:keys [client dev-instance]} data
        instance-id (.getInstanceId dev-instance)]
    (stop-instance client instance-id)
    {:data data
     :current-state current-state
     :transition :success}))

(defn notifying-stopped
  [{:keys [data current-state]}]
  (let [{:keys [sns-client dev-instance topic-arn]} data
        instance-id (.getInstanceId dev-instance)]
    (notify sns-client topic-arn
            (format "Dev instance %s was shut down." instance-id))
    {:data data
     :current-state current-state
     :transition :success}))

(defn done
  [context]
  nil)

(defn next-state
  [{:keys [current-state transition] :as context}]
  (let [new-state (get-in fsm [current-state transition])
        new-state-fn (ns-resolve 'com.jebbeich.sleepdevinstance new-state)]
    (println (format "%s -- %s --> %s" transition current-state new-state))
    (when-not new-state (throw (ex-info "new state not found" context)))
    (new-state-fn (-> context
                      (dissoc :transition)
                      (assoc :current-state new-state)))))

;; ***** Lambda Handler Function *****

(defn -handler [^Object req ^Context ctx]
  (doall (take-while some? (iterate next-state {:current-state nil
                                                :transition :init})))
  "success")
