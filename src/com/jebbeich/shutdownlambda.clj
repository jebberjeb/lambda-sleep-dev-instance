(ns com.jebbeich.shutdownlambda
  (:gen-class
   :methods [^:static [handler [Object com.amazonaws.services.lambda.runtime.Context] Object]])
  (:require
    [com.jebbeich.ec2 :as ec2])
  (:import
    (com.amazonaws.services.lambda.runtime
      Context)))

;; ***** Lambda Handler Function *****

(defn -handler [^Object req ^Context ctx]
  (ec2/stop-instance (ec2/client) (get req "instance-id"))
  "success")
