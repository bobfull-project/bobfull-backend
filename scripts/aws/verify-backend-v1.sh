#!/usr/bin/env bash
set -euo pipefail

required_env() {
  local key="$1"
  if [ -z "${!key:-}" ]; then
    echo "Missing required environment variable: ${key}" >&2
    exit 1
  fi
}

required_env BASE_URL

curl --fail --silent --show-error "${BASE_URL%/}/api/restaurants" >/tmp/bobfull-restaurants-response.json
test -s /tmp/bobfull-restaurants-response.json
echo "API /api/restaurants: PASS"

if command -v docker >/dev/null 2>&1; then
  container_name="${CONTAINER_NAME:-bobfull-backend}"
  if docker ps --filter "name=${container_name}" --filter "status=running" --format '{{.Names}}' \
      | grep -Fx "${container_name}" >/dev/null; then
    echo "Docker container ${container_name}: PASS"
  fi
fi

if command -v aws >/dev/null 2>&1 && [ -n "${AWS_REGION:-}" ]; then
  aws sts get-caller-identity --query Account --output text >/dev/null
  echo "AWS caller identity: PASS"

  if [ -n "${PARAMETER_PREFIX:-}" ]; then
    aws ssm get-parameters-by-path \
      --region "${AWS_REGION}" \
      --path "${PARAMETER_PREFIX%/}" \
      --recursive \
      --query 'Parameters[].Name' \
      --output text >/dev/null
    echo "Parameter Store parameter names: PASS"
  fi

  if [ -n "${S3_IMAGE_BUCKET:-}" ]; then
    aws s3api head-bucket --region "${AWS_REGION}" --bucket "${S3_IMAGE_BUCKET}" >/dev/null
    echo "S3 image bucket access: PASS"
  fi

  if [ -n "${CLOUDWATCH_LOG_GROUP:-}" ]; then
    aws logs describe-log-streams \
      --region "${AWS_REGION}" \
      --log-group-name "${CLOUDWATCH_LOG_GROUP}" \
      --max-items 1 >/dev/null
    echo "CloudWatch log group access: PASS"
  fi
fi
