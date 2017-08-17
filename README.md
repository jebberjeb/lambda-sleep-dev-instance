# Sleep Dev Instance Lambda

This lambda function tries to put your dev environment to sleep (any EC2
instance with tag type=dev). If tmux is not running on the machine, it is
shut down, after a console notification is sent. If tmux is running, the
instance is not shut down, but a console notification is sent telling the user
of the Lambda's intent to shut it down. It subsequently retries every few
minutes for an hour. In either case, an email notification is sent regarding
the outcome.

# Prerequisites

* Install `aws` cli tool.
* Replace `com-jebbeich` in `template.yml` with an S3 bucket to contain the
CloudWatch artifacts.

# Development

From the command line, use the `deploy.sh` and `run.sh` to deploy the Lambda
and execute it.

`deploy.sh` uses `aws cloudformation` cli command, as opposed to `aws lambda`
as it allows us to use AWS CloudFormation to manage the lambda, as well as
share the CloudFormation template `template.yml` between the local development
environment & AWS cloud CI environment.

# AWS CodePipeline

Optionally, build & deployment can be pushed to the AWS cloud.

The `buildspec.yml` file can be used to create an AWS CodeBuild. The build can
then be integrated into an AWS CodePipeline using the resulting
`template-output.json` artifact.
