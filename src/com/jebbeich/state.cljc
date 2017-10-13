(ns com.jebbeich.state)

(defn next-state
  [fsm ns-symbol {:keys [current-state transition] :as context}]
  (let [new-state (get-in fsm [current-state transition])
        new-state-fn (ns-resolve ns-symbol new-state)]
    (println (format "%s -- %s --> %s" transition current-state new-state))
    (when-not new-state (throw (ex-info "new state not found" context)))
    (new-state-fn (-> context
                      (dissoc :transition)
                      (assoc :current-state new-state)))))
