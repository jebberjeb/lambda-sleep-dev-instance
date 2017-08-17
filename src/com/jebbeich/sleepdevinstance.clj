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
      RunInstancesResult
      Tag)
    (com.amazonaws.services.simplesystemsmanagement
      AWSSimpleSystemsManagementClientBuilder)
    (com.amazonaws.services.simplesystemsmanagement.model
      GetCommandInvocationRequest
      SendCommandRequest
      SendCommandResult)))

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
       (filter (partial has-tag? "type" "dev"))
       (map #(.getInstanceId %))))

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

;; ***** Lambda Handler Function *****

(defn -handler [^Object req ^Context ctx]
  (run-command-results (ssm-client)
                       (first (dev-instances (client)))
                       "sudo su - ubuntu -c \"tmux list-sessions\""))
