(ns com.jebbeich.ssm
  (:require
    [clojure.string :as string])
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

(defn succeeded?
  "Returns true if the command succeeded. Retries after a delay."
  ([ssm-client invocation-request]
   (succeeded? ssm-client invocation-request 1000 10))
  ([ssm-client invocation-request delay retries]
   (Thread/sleep delay)
   (let [response (.getCommandInvocation ssm-client invocation-request)]

     (cond (= "SUCCESS" (-> response .getStatusDetails string/upper-case))
           true

           (pos? retries)
           (succeeded? ssm-client invocation-request delay (dec retries))))))

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
  (let [invocation-request
        (->> (build-send-command-request [instanceid] command)
             (run-command ssm-client)
             (build-command-invocation-request instanceid))]
    (if (succeeded? ssm-client invocation-request)
      {:output (get-command-invocation-output ssm-client invocation-request)}
      {:error :failed-or-timed-out})))

(defn message-terminal
  [ssm-client instanceid message]
  (let [file "/tmp/msg.txt"]
    (run-command-results ssm-client instanceid
                         (format "echo '%s' > %s" message file))
    (run-command-results ssm-client instanceid
                         (format "sudo wall -n %s" file))))

;; test for succeeded?
#_(let [c (client)
      i "i-0482ed7606c57c28e"
      command "sleep 4; echo 'foo'"]
  (run-command-results c i command))
