(ns com.jebbeich.ec2
  "Functions which wrap AWS EC2 API"
  (:gen-class)
  (:import
    (com.amazonaws.services.ec2
      AmazonEC2ClientBuilder)
    (com.amazonaws.services.ec2.model
      InstanceType
      RunInstancesRequest
      StopInstancesRequest
      StartInstancesRequest
      Tag)))

(defn client
  []
  (AmazonEC2ClientBuilder/defaultClient))

(defn reservations
  [client]
  (-> client
      .describeInstances
      .getReservations))

(defn instances
  [client]
  (->> client
       reservations
       (mapcat #(.getInstances %))))

(defn has-tag?
  [key value instance]
  (some (partial = (Tag. key value)) (.getTags instance)))

(defn dev-instances
  [client]
  (->> client
       instances
       (filter (partial has-tag? "type" "dev"))))

(defn stop-instance
  [client instance-id]
  (.stopInstances client
                  (.withInstanceIds (StopInstancesRequest.) [instance-id])))

(defn start-instance
  [client instance-id]
  (.startInstances client
                   (.withInstanceIds (StartInstancesRequest.) [instance-id])))
