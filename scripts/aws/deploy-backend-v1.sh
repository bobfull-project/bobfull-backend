#!/usr/bin/env bash
set -euo pipefail

required_env() {
  local key="$1"
  if [ -z "${!key:-}" ]; then
    echo "Missing required environment variable: ${key}" >&2
    exit 1
  fi
}

fetch_parameter() {
  local parameter_name="$1"
  aws ssm get-parameter \
    --region "${AWS_REGION}" \
    --name "${PARAMETER_PREFIX}/${parameter_name}" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text
}

append_env_value() {
  local env_key="$1"
  local value="$2"
  printf '%s=%s\n' "${env_key}" "${value}" >> "${APP_ENV_FILE}"
}

append_parameter() {
  local env_key="$1"
  local parameter_name="$2"
  local required="$3"
  local value

  if value="$(fetch_parameter "${parameter_name}" 2>/dev/null)"; then
    append_env_value "${env_key}" "${value}"
    return
  fi

  if [ "${required}" = "true" ]; then
    echo "Missing required Parameter Store value: ${PARAMETER_PREFIX}/${parameter_name}" >&2
    exit 1
  fi
}

required_env AWS_REGION
required_env ECR_IMAGE_URI
required_env PARAMETER_PREFIX

CONTAINER_NAME="${CONTAINER_NAME:-bobfull-backend}"
HOST_PORT="${HOST_PORT:-8080}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"
APP_ENV_FILE="${APP_ENV_FILE:-/opt/bobfull/backend.env}"
CLOUDWATCH_LOG_GROUP="${CLOUDWATCH_LOG_GROUP:-/bobfull/backend}"
CLOUDWATCH_LOG_STREAM="${CLOUDWATCH_LOG_STREAM:-${CONTAINER_NAME}}"
PARAMETER_PREFIX="${PARAMETER_PREFIX%/}"

if ! command -v aws >/dev/null 2>&1; then
  echo "AWS CLI is required on EC2." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required on EC2." >&2
  exit 1
fi

sudo mkdir -p "$(dirname "${APP_ENV_FILE}")"
sudo chown "$USER":"$USER" "$(dirname "${APP_ENV_FILE}")"
umask 077
: > "${APP_ENV_FILE}"

append_env_value SPRING_PROFILES_ACTIVE prod
append_env_value AWS_REGION "${AWS_REGION}"

required_parameters=(
  "DB_URL:db-url"
  "DB_USERNAME:db-username"
  "DB_PASSWORD:db-password"
  "JWT_SECRET:jwt-secret"
  "PORTONE_API_SECRET:portone-api-secret"
  "PORTONE_STORE_ID:portone-store-id"
)

optional_parameters=(
  "JWT_ACCESS_TOKEN_EXPIRATION_SECONDS:jwt-access-token-expiration-seconds"
  "JPA_DDL_AUTO:jpa-ddl-auto"
  "CORS_ALLOWED_ORIGINS:cors-allowed-origins"
  "PORTONE_CHANNEL_KEY:portone-channel-key"
  "PORTONE_WEBHOOK_SECRET:portone-webhook-secret"
  "PAYMENT_EXPIRATION_ENABLED:payment-expiration-enabled"
  "PAYMENT_EXPIRATION_FIXED_DELAY:payment-expiration-fixed-delay"
  "PAYMENT_EXPIRATION_BATCH_SIZE:payment-expiration-batch-size"
  "S3_IMAGE_UPLOAD_URL_EXPIRATION:s3-image-upload-url-expiration"
  "S3_IMAGE_GET_URL_EXPIRATION:s3-image-get-url-expiration"
)

for item in "${required_parameters[@]}"; do
  append_parameter "${item%%:*}" "${item#*:}" true
done

for item in "${optional_parameters[@]}"; do
  append_parameter "${item%%:*}" "${item#*:}" false
done

if [ -n "${S3_IMAGE_BUCKET:-}" ]; then
  append_env_value S3_IMAGE_BUCKET "${S3_IMAGE_BUCKET}"
else
  append_parameter S3_IMAGE_BUCKET s3-image-bucket true
fi

if ! grep -q '^JPA_DDL_AUTO=' "${APP_ENV_FILE}"; then
  append_env_value JPA_DDL_AUTO update
fi

if ! grep -q '^JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=' "${APP_ENV_FILE}"; then
  append_env_value JWT_ACCESS_TOKEN_EXPIRATION_SECONDS 3600
fi

s3_bucket="$(awk -F= '$1 == "S3_IMAGE_BUCKET" { print $2 }' "${APP_ENV_FILE}" | tail -n 1)"
if [ -n "${s3_bucket}" ]; then
  aws s3api head-bucket --region "${AWS_REGION}" --bucket "${s3_bucket}" >/dev/null
fi

aws logs create-log-group \
  --region "${AWS_REGION}" \
  --log-group-name "${CLOUDWATCH_LOG_GROUP}" \
  >/dev/null 2>&1 || true

ecr_registry="${ECR_IMAGE_URI%%/*}"
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ecr_registry}" >/dev/null

docker pull "${ECR_IMAGE_URI}"

if docker ps -a --format '{{.Names}}' | grep -Fx "${CONTAINER_NAME}" >/dev/null 2>&1; then
  docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
  docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true
fi

docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart unless-stopped \
  --env-file "${APP_ENV_FILE}" \
  -p "${HOST_PORT}:${CONTAINER_PORT}" \
  --log-driver=awslogs \
  --log-opt awslogs-region="${AWS_REGION}" \
  --log-opt awslogs-group="${CLOUDWATCH_LOG_GROUP}" \
  --log-opt awslogs-stream="${CLOUDWATCH_LOG_STREAM}" \
  --log-opt awslogs-create-group=true \
  "${ECR_IMAGE_URI}"

sleep 10

if ! docker ps --filter "name=${CONTAINER_NAME}" --filter "status=running" --format '{{.Names}}' \
    | grep -Fx "${CONTAINER_NAME}" >/dev/null; then
  echo "Container did not reach running state. Check docker logs on EC2." >&2
  exit 1
fi

docker ps --filter "name=${CONTAINER_NAME}"
