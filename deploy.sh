#!/bin/bash

lein uberjar
mkdir tmp
cd tmp
cp ../target/sleep-dev-instance-0.1.0-standalone.jar .
# We can't refer to ../template.yaml below, or we'll upload everything in the
# root folder, rather than tmp.
cp ../template.yaml .
jar xf sleep-dev-instance-0.1.0-standalone.jar
# Remove the stuff we don't need.
rm -rf target src buildspec.yaml project.clj pom.xml sleep-dev-instance-0.1.0-standalone.jar
# Why use cloudformation here, rather than lambda directly? Becaues we can
# reuse the build artifacts w/ the CI pipeline.
aws cloudformation package --template-file template.yaml --s3-bucket com-jebbeich --output-template template-output.json
aws cloudformation deploy --template-file template-output.json --stack-name sleep-dev-instance-lambda-stack --capabilities CAPABILITY_IAM

