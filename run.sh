#! /bin/bash

aws lambda invoke --function-name SleepDevInstance results.json
cat results.json
rm results.json
