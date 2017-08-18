(defproject sleep-dev-instance "0.1.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [com.amazonaws/aws-lambda-java-core "1.1.0"]
                 [com.amazonaws/aws-java-sdk-ec2 "1.11.173"]
                 [com.amazonaws/aws-java-sdk-ssm "1.11.173"]
                 [com.amazonaws/aws-java-sdk-sns "1.11.173"]]
  :aot :all)
