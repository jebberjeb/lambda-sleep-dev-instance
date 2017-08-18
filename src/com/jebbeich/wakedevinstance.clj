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
        instance-id (.getInstanceId (first (ec2/dev-instances client)))
        topic-arn "arn:aws:sns:us-east-1:803068370526:NotifyMe"]
    (ec2/start-instance client instance-id)

    ;; TODO actually check to make sure it started
    (sns/notify sns-client topic-arn
                (format "Dev instance %s was started." instance-id)))
  "success")
