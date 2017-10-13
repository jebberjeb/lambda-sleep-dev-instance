(ns com.jebbeich.notifylambda
  (:gen-class
   :methods [^:static [handler [Object com.amazonaws.services.lambda.runtime.Context] Object]])
  (:require
    [com.jebbeich.sns :as sns])
  (:import
    (com.amazonaws.services.lambda.runtime
      Context)))

;; ***** Lambda Handler Function *****

(defn -handler [^Object req ^Context ctx]
  (sns/notify (sns/client)
              "arn:aws:sns:us-east-1:803068370526:NotifyMe"
              (get req "Message"))
  "success")
