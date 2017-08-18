(ns com.jebbeich.sleepdevinstance
  (:gen-class
   :methods [^:static [handler [Object com.amazonaws.services.lambda.runtime.Context] Object]])
  (:require
    [com.jebbeich.ec2 :as ec2]
    [com.jebbeich.sns :as sns]
    [com.jebbeich.ssm :as ssm])
  (:import
    (com.amazonaws.services.lambda.runtime
      Context)))

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

;; ***** States *****

(defn initializing
  [{:keys [data current-state]}]
  {:data (assoc data
                :client (ec2/client)
                :ssm-client (ssm/client)
                :sns-client (sns/client)
                ;; TODO move to external config / param
                :topic-arn "arn:aws:sns:us-east-1:803068370526:NotifyMe")
   :current-state current-state
   :transition :success})

(defn finding-instance
  [{:keys [data current-state]}]
  (let [client (:client data)
        dev-instance (first (ec2/dev-instances client))]
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
        output (ssm/run-command-results
                 ssm-client (.getInstanceId dev-instance)
                 "sudo su - ubuntu -c \"tmux list-sessions\"")]
    {:data data
     :current-state current-state
     :transition (if (empty? output) :stopped :running)}))

(defn notifying-cannot-stop
  [{:keys [data current-state]}]
  (let [{:keys [ssm-client sns-client dev-instance topic-arn]} data
        instance-id (.getInstanceId dev-instance)]
    (ssm/message-terminal
      ssm-client instance-id
      "SleepDevInstance Lambda can not shut down while tmux is running!")
    (sns/notify sns-client topic-arn
                (format "Dev instance %s was not shut down." instance-id))
    {:data data
     :current-state current-state
     :transition :success}))

(defn stopping
  [{:keys [data current-state]}]
  (let [{:keys [client dev-instance]} data
        instance-id (.getInstanceId dev-instance)]
    (ec2/stop-instance client instance-id)
    {:data data
     :current-state current-state
     :transition :success}))

(defn notifying-stopped
  [{:keys [data current-state]}]
  (let [{:keys [sns-client dev-instance topic-arn]} data
        instance-id (.getInstanceId dev-instance)]
    (sns/notify sns-client topic-arn
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
