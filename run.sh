#! /bin/bash

# TODO can we fetch the arn here, or pass the name as an argument?

result=$(aws stepfunctions start-execution --state-machine-arn arn:aws:states:us-east-1:803068370526:stateMachine:SleepStateMachine-S07UMBPKCWLA)

echo "START EXECUTION..."
echo $result

executionarn=$(echo $result | python -c "import json,sys;obj=json.load(sys.stdin);print obj['executionArn'];")

echo "EXECUTION-ARN: $executionarn"

result=$(aws stepfunctions describe-execution --execution-arn $executionarn)

echo "EXECUTION RESULTS..."
echo $result


