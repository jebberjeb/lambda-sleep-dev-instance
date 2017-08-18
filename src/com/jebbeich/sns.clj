(ns com.jebbeich.sns
  "Functions which wrap AWS SNS API"
  (:gen-class)
  (:import
    (com.amazonaws.services.sns
      AmazonSNSClientBuilder)))

(defn client
  []
  (AmazonSNSClientBuilder/defaultClient))

(defn notify
  [sns-client topic-arn message]
  (.publish sns-client topic-arn message))
