(ns user
  (:require
    #_[com.jebbeich.sleepdevinstance :as core]
    [clojure.stacktrace :as stacktrace]))

#_(defn run
  []
  (core/-handler nil nil))

(defn print-last-ex
  []
  (stacktrace/print-stack-trace *e))
