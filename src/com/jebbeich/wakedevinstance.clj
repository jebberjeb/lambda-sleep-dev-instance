(ns com.jebbeich.wakedevinstance
  (:gen-class
   :methods [^:static [handler [Object com.amazonaws.services.lambda.runtime.Context] Object]])
  (:require
    [com.jebbeich.ec2 :as ec2]
    [com.jebbeich.sns :as sns])
  (:import
    (com.amazonaws.services.lambda.runtime
      Context)))

(defn -handler [^Object req ^Context ctx]
  (let [client (ec2/client)
        sns-client (sns/client)
        instance (first (ec2/dev-instances client))
        instance-id (.getInstanceId instance)
        topic-arn "arn:aws:sns:us-east-1:803068370526:NotifyMe"]

    (if (= :stopped (ec2/state instance))

      ;; If stopped, start it, then notify them.
      (do
        ;; TODO this could certainly use exception handling.
        (ec2/start-instance client instance-id)
        ;; TODO actually check to make sure it started.
        (sns/notify sns-client topic-arn
                    (format "Dev instance %s was started." instance-id)))

      ;; If not stopped, then don't try to start it.
      (sns/notify sns-client topic-arn
                  (format "Dev instance %s was not started because it was not
                          in a stopped state" instance-id)) ))
  "success")
