(ns user
  (:require
    [com.jebbeich.sleepdevinstance :as core]
    [clojure.stacktrace :as stacktrace]))

(defn run
  []
  (core/-handler nil nil))

(defn print-last-ex
  []
  (stacktrace/print-stack-trace *e))
