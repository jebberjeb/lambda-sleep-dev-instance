# Sleep Dev Instance Lambda

This project contains several Lambdas, orchestrated by an AWS Step Function
which attempt to shut down an EC2 instance containing a development
environment, when it is not in use, to save money.

It looks for any EC2 instances tagged as dev machines, and tries to shut them
down. If it finds `tmux` running, it aborts shutting the instance down. In
either case, it sends an SNS notification regarding the outcome.

The `WakeDevInstance` Lambda attempts to start any EC2 instance tagged as a dev
machine, and sends an SNS notification regarding the outcome.

# Prerequisites

* Install `aws` cli tool.
* Replace `com-jebbeich` in `template.yml` with an S3 bucket to contain the
CloudWatch artifacts.

# Development

From the command line, use the `deploy.sh` and `run.sh` to deploy the Lambda
and execute it.

`deploy.sh` uses `aws cloudformation` cli command as it allows us to use AWS
CloudFormation to manage the step functions & lambdas, as well as share the
CloudFormation template `template.yml` between the local development
environment & AWS cloud CI environment.
