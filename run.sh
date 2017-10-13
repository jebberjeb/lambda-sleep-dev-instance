#! /bin/bash

# TODO can we fetch the arn here, or pass the name as an argument?

result=$(aws stepfunctions start-execution --state-machine-arn arn:aws:states:us-east-1:803068370526:stateMachine:SleepStateMachine-KTQQ1SEEQS2K)

echo "START EXECUTION..."
echo $result

executionarn=$(echo $result | python -c "import json,sys;obj=json.load(sys.stdin);print obj['executionArn'];")

echo "EXECUTION-ARN: $executionarn"

while [[ $result != *"SUCCEEDED"* ]]
do
    result=$(aws stepfunctions describe-execution --execution-arn $executionarn)
    echo -e "EXECUTION RESULTS...\n\n"
    echo $result
    echo -e "\n\n\n"
    sleep 2
done

echo "EXECUTION SUCCEEDED"


