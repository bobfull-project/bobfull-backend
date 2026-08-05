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
required_env BACKEND_EC2_INSTANCE_ID
required_env ECR_IMAGE_URI
required_env PARAMETER_PREFIX

DEPLOY_SCRIPT_PATH="${DEPLOY_SCRIPT_PATH:-scripts/aws/deploy-backend-v1.sh}"
SSM_DOCUMENT_NAME="${SSM_DOCUMENT_NAME:-AWS-RunShellScript}"
SSM_POLL_INTERVAL_SECONDS="${SSM_POLL_INTERVAL_SECONDS:-10}"
SSM_POLL_TIMEOUT_SECONDS="${SSM_POLL_TIMEOUT_SECONDS:-900}"

if ! command -v aws >/dev/null 2>&1; then
  echo "AWS CLI is required on the GitHub Actions runner." >&2
  exit 1
fi

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required on the GitHub Actions runner." >&2
  exit 1
fi

if [ ! -f "${DEPLOY_SCRIPT_PATH}" ]; then
  echo "Deploy script not found: ${DEPLOY_SCRIPT_PATH}" >&2
  exit 1
fi

if deploy_script_b64="$(base64 -w 0 "${DEPLOY_SCRIPT_PATH}" 2>/dev/null)"; then
  :
else
  deploy_script_b64="$(base64 "${DEPLOY_SCRIPT_PATH}" | tr -d '\n')"
fi

command_payload="$(mktemp)"
trap 'rm -f "${command_payload}"' EXIT

export DEPLOY_SCRIPT_B64="${deploy_script_b64}"

python3 - "${command_payload}" <<'PY'
import json
import os
import shlex
import sys

payload_path = sys.argv[1]
deploy_script_b64 = os.environ["DEPLOY_SCRIPT_B64"]

command_env_keys = [
    "AWS_REGION",
    "ECR_IMAGE_URI",
    "PARAMETER_PREFIX",
    "CONTAINER_NAME",
    "HOST_PORT",
    "CONTAINER_PORT",
    "APP_ENV_FILE",
    "CLOUDWATCH_LOG_GROUP",
    "CLOUDWATCH_LOG_STREAM",
    "DOCKER_NETWORK",
    "REDIS_CONTAINER_NAME",
    "REDIS_IMAGE",
    "REDIS_VOLUME",
    "S3_IMAGE_BUCKET",
]

env_prefix = " ".join(
    f"{key}={shlex.quote(value)}"
    for key in command_env_keys
    if (value := os.environ.get(key))
)

commands = [
    "cat > /tmp/bobfull-deploy-backend-v1.sh.b64 <<'BOBFULL_DEPLOY_SCRIPT_B64'\n"
    f"{deploy_script_b64}\n"
    "BOBFULL_DEPLOY_SCRIPT_B64",
    "base64 -d /tmp/bobfull-deploy-backend-v1.sh.b64 > /tmp/bobfull-deploy-backend-v1.sh",
    "chmod 700 /tmp/bobfull-deploy-backend-v1.sh",
    f"{env_prefix} bash /tmp/bobfull-deploy-backend-v1.sh",
]

with open(payload_path, "w", encoding="utf-8") as output:
    json.dump({"commands": commands}, output)
PY

command_id="$(
  aws ssm send-command \
    --region "${AWS_REGION}" \
    --instance-ids "${BACKEND_EC2_INSTANCE_ID}" \
    --document-name "${SSM_DOCUMENT_NAME}" \
    --comment "Deploy bobfull backend ${ECR_IMAGE_URI}" \
    --parameters "file://${command_payload}" \
    --query 'Command.CommandId' \
    --output text
)"

echo "SSM command id: ${command_id}"

deadline=$((SECONDS + SSM_POLL_TIMEOUT_SECONDS))

while true; do
  status="$(
    aws ssm get-command-invocation \
      --region "${AWS_REGION}" \
      --command-id "${command_id}" \
      --instance-id "${BACKEND_EC2_INSTANCE_ID}" \
      --query 'Status' \
      --output text 2>/dev/null || true
  )"

  case "${status}" in
    Success)
      echo "SSM command status: Success"
      break
      ;;
    Pending|InProgress|Delayed|"")
      if [ "${SECONDS}" -ge "${deadline}" ]; then
        echo "SSM command timed out while waiting for completion." >&2
        status="TimedOut"
        break
      fi
      echo "SSM command status: ${status:-Pending}"
      sleep "${SSM_POLL_INTERVAL_SECONDS}"
      ;;
    *)
      echo "SSM command status: ${status}" >&2
      break
      ;;
  esac
done

aws ssm get-command-invocation \
  --region "${AWS_REGION}" \
  --command-id "${command_id}" \
  --instance-id "${BACKEND_EC2_INSTANCE_ID}" \
  --query 'StandardOutputContent' \
  --output text

standard_error="$(
  aws ssm get-command-invocation \
    --region "${AWS_REGION}" \
    --command-id "${command_id}" \
    --instance-id "${BACKEND_EC2_INSTANCE_ID}" \
    --query 'StandardErrorContent' \
    --output text
)"

if [ "${standard_error}" != "None" ] && [ -n "${standard_error}" ]; then
  echo "${standard_error}" >&2
fi

if [ "${status}" != "Success" ]; then
  exit 1
fi
