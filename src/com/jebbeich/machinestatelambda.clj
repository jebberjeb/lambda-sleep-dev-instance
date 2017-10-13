(ns com.jebbeich.machinestatelambda
  (:gen-class
   :methods [^:static [handler [Object com.amazonaws.services.lambda.runtime.Context] Object]])
  (:require
    [com.jebbeich.ec2 :as ec2]
    [com.jebbeich.ssm :as ssm]
    [com.jebbeich.state :as state])
  (:import
    (com.amazonaws.services.lambda.runtime
      Context)))

(def fsm
  {nil                 {:init 'initializing}
   'initializing       {:success 'finding-instance}
   'finding-instance   {:found 'getting-state
                        :not-found 'report-not-found}
   'getting-state      {:stopped  'report-not-running
                        :stopping 'report-not-running
                        ;; TODO enumerate other non-running states
                        :running  'getting-tmux-state}
   'getting-tmux-state {:stopped             'report-running-no-tmux
                        :running             'report-tmux-running
                        :failed-or-timed-out 'report-failed-or-timed-out}})

;; ***** States *****

(defn initializing
  [{:keys [data current-state]}]
  {:data (assoc data
                :client (ec2/client)
                :ssm-client (ssm/client))
   :current-state current-state
   :transition :success})

(defn finding-instance
  [{:keys [data current-state]}]
  (let [client (:client data)
        dev-instance (first (ec2/dev-instances client))]
    {:data (assoc data :dev-instance dev-instance)
     :current-state current-state
     :transition (if dev-instance :found :not-found)}))

(defn getting-state
  [{:keys [data current-state]}]
  {:data data
   :current-state current-state
   :transition (-> data :dev-instance .getState .getName keyword)})

(defn getting-tmux-state
  [{:keys [data current-state]}]
  (let [{:keys [ssm-client client dev-instance]} data
        {:keys [output error]} (ssm/run-command-results
                                 ssm-client (.getInstanceId dev-instance)
                                 "sudo su - ubuntu -c \"tmux list-sessions\"")]
    {:data data
     :current-state current-state
     :transition (cond error
                       error

                       (empty? output)
                       :stopped

                       :else
                       :running)}))

(defn report-something
  [machine-state {:keys [data] :as context}]
  {:transition nil ;;terminal state
   :data {:machine-state machine-state
          :instance-id (.getInstanceId (:dev-instance data))}})

(def report-not-found (partial report-something :machine-not-found))
(def report-not-running (partial report-something :not-running))
(def report-running-no-tmux (partial report-something :running-no-tmux))
(def report-failed-or-timed-out (partial report-something
                                         :running-tmux-unknown))
(def report-tmux-running (partial report-something :running-with-tmux))

(def next-state
  (partial state/next-state fsm 'com.jebbeich.machinestatelambda))

;; ***** Lambda Handler Function *****

(defn str-keys [m] (reduce (fn [m' [k v]] (assoc m' (name k) v)) {} m))

(defn output
  "Extract the output from the context."
  [{:keys [data]}]
  (-> data
      str-keys
      (update "machine-state" name)))

(defn -handler [^Object req ^Context ctx]
  ;; Run through the states until we find a terminal state
  (let [result (first (drop-while :transition
                                  (iterate next-state {:current-state nil
                                                       :transition :init})))]
    (output result)))
