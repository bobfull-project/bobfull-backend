#!/usr/bin/env bash
set -euo pipefail

required_env() {
  local key="$1"
  if [ -z "${!key:-}" ]; then
    echo "Missing required environment variable: ${key}" >&2
    exit 1
  fi
}

required_env AWS_REGION
required_env ECR_REPOSITORY

IMAGE_TAG="${IMAGE_TAG:-$(git rev-parse --short HEAD)}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-$(aws sts get-caller-identity --query Account --output text)}"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_URI="${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}"
LATEST_IMAGE_URI="${ECR_REGISTRY}/${ECR_REPOSITORY}:latest"

if ! aws ecr describe-repositories --region "${AWS_REGION}" --repository-names "${ECR_REPOSITORY}" >/dev/null 2>&1; then
  aws ecr create-repository \
    --region "${AWS_REGION}" \
    --repository-name "${ECR_REPOSITORY}" \
    --image-scanning-configuration scanOnPush=true \
    >/dev/null
fi

aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}" >/dev/null

docker build -t "${IMAGE_URI}" .
docker tag "${IMAGE_URI}" "${LATEST_IMAGE_URI}"
docker push "${IMAGE_URI}"
docker push "${LATEST_IMAGE_URI}"

printf '%s\n' "${IMAGE_URI}"
