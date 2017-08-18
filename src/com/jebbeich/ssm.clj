(ns com.jebbeich.ssm
  (:gen-class)
  (:import
    (com.amazonaws.services.simplesystemsmanagement
      AWSSimpleSystemsManagementClientBuilder)
    (com.amazonaws.services.simplesystemsmanagement.model
      GetCommandInvocationRequest
      SendCommandRequest
      SendCommandResult)))

(defn client
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
