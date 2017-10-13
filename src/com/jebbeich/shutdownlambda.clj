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
  ;; Don't actually shut it down for now, but we should have the instance-id
  ;; in the request
  "success")
