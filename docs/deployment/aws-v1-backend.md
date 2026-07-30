# AWS V1 Backend Deployment

This guide covers the backend side of V1 deployment.

Frontend S3 static hosting is handled in the frontend repository. Restaurant and menu image files use S3, but this backend repository currently does not define a Presigned URL or image upload API contract. For Issue #41, S3 is prepared as an AWS resource, IAM target, environment value, and verification step.

## Target Architecture

```text
GitHub Actions or local machine
  -> Docker image build
  -> Amazon ECR push

EC2
  -> ECR image pull
  -> Spring Boot container
  -> RDS MySQL
  -> Parameter Store
  -> S3 image bucket
  -> CloudWatch Logs
```

## Required AWS Resources

- EC2 instance for the Spring Boot Docker container
- ECR repository for the backend Docker image
- RDS MySQL instance for the production database
- S3 bucket for restaurant and menu images
- Systems Manager Parameter Store path for backend environment values
- CloudWatch Logs log group for Spring Boot container logs
- EC2 IAM role with ECR pull, Parameter Store read, S3 access, and CloudWatch Logs write permissions
- GitHub Actions OIDC IAM role with ECR push permissions

## Security Group Rules

- EC2 inbound:
  - SSH `22` from the maintainer's IP only
  - Application port `8080` from the allowed client range for V1
- RDS inbound:
  - MySQL `3306` from the EC2 security group only
- EC2 outbound:
  - HTTPS `443` for ECR, SSM, S3, and CloudWatch Logs
  - MySQL `3306` to the RDS security group

Do not open the RDS security group to `0.0.0.0/0`.

## Parameter Store

Use a path prefix such as:

```text
/bobfull/prod
```

Required parameters:

```text
/bobfull/prod/DB_URL
/bobfull/prod/DB_USERNAME
/bobfull/prod/DB_PASSWORD
/bobfull/prod/JWT_SECRET
/bobfull/prod/PORTONE_API_SECRET
/bobfull/prod/PORTONE_STORE_ID
```

Optional parameters:

```text
/bobfull/prod/JWT_ACCESS_TOKEN_EXPIRATION_SECONDS
/bobfull/prod/JPA_DDL_AUTO
/bobfull/prod/PORTONE_CHANNEL_KEY
/bobfull/prod/PORTONE_WEBHOOK_SECRET
/bobfull/prod/S3_IMAGE_BUCKET
```

Recommended V1 values:

```text
SPRING_PROFILES_ACTIVE=prod
JPA_DDL_AUTO=update
JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=3600
```

Store secrets as `SecureString`. Do not commit real values to Git.

## EC2 Bootstrap

Copy and run the bootstrap script on EC2:

```bash
bash scripts/aws/bootstrap-ec2-v1.sh
```

Log out and log back in after the script finishes so Docker group membership is applied.

## Manual ECR Push

Run this from a machine that is authenticated to AWS and can access Docker:

```bash
export AWS_REGION=ap-northeast-2
export ECR_REPOSITORY=bobfull-backend
export IMAGE_TAG=$(git rev-parse --short HEAD)

bash scripts/aws/push-image-to-ecr-v1.sh
```

The script prints the pushed ECR image URI.

## Manual EC2 Deploy

Run this on EC2 after the image is pushed to ECR:

```bash
export AWS_REGION=ap-northeast-2
export ECR_IMAGE_URI=<account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/bobfull-backend:<tag>
export PARAMETER_PREFIX=/bobfull/prod
export S3_IMAGE_BUCKET=<image-bucket-name>
export CONTAINER_NAME=bobfull-backend
export HOST_PORT=8080
export CLOUDWATCH_LOG_GROUP=/bobfull/backend

bash scripts/aws/deploy-backend-v1.sh
```

The deploy script:

- reads runtime values from Parameter Store without printing secret values
- logs in to ECR through the EC2 IAM role
- pulls the requested image
- replaces the existing backend container
- runs the container with `SPRING_PROFILES_ACTIVE=prod`
- sends Docker stdout and stderr to CloudWatch Logs through the `awslogs` driver
- verifies that the container reaches the running state

## GitHub Actions Deployment

Workflow:

```text
.github/workflows/deploy-backend-v1.yml
```

Required GitHub Secrets:

```text
AWS_ROLE_TO_ASSUME
EC2_SSH_PRIVATE_KEY
```

Required GitHub Variables:

```text
AWS_REGION
ECR_REPOSITORY
BACKEND_EC2_HOST
BACKEND_EC2_USER
BACKEND_PARAMETER_PREFIX
BACKEND_PUBLIC_BASE_URL
S3_IMAGE_BUCKET
```

Optional GitHub Variables:

```text
BACKEND_HOST_PORT
BACKEND_CONTAINER_NAME
BACKEND_CLOUDWATCH_LOG_GROUP
```

Run the workflow manually from GitHub Actions while this PR remains Draft. The workflow performs Gradle tests, builds the Docker image, pushes it to ECR, copies the EC2 deploy script, deploys the selected image, and verifies `GET /api/restaurants` from outside the EC2 instance.

## Verification

From a local machine or EC2:

```bash
export BASE_URL=http://<ec2-public-host>:8080
export AWS_REGION=ap-northeast-2
export PARAMETER_PREFIX=/bobfull/prod
export S3_IMAGE_BUCKET=<image-bucket-name>
export CLOUDWATCH_LOG_GROUP=/bobfull/backend

bash scripts/aws/verify-backend-v1.sh
```

The verification script checks:

- public API response for `GET /api/restaurants`
- running Docker container when executed on EC2
- AWS caller identity
- Parameter Store parameter name access without printing values
- S3 image bucket access
- CloudWatch log group access

## Issue Boundaries

Issue #41 includes backend AWS deployment infrastructure only.

The following work remains outside this backend Issue:

- frontend S3 static hosting
- frontend production API URL configuration
- backend CORS policy for the deployed frontend origin
- S3 Presigned URL API or image upload API implementation
- Issue #42 smoke test result record
