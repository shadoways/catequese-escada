#!/usr/bin/env bash
set -euo pipefail

# Staging smoke validation for Go API.
# Required env vars:
#   BASE_URL, LOGIN_EMAIL, LOGIN_PASSWORD

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[ERROR] Required command not found: $1" >&2
    exit 1
  fi
}

require_env() {
  local name="$1"
  if [[ -z "${!name:-}" ]]; then
    echo "[ERROR] Required env var not set: $name" >&2
    exit 1
  fi
}

json_field() {
  local key="$1"
  jq -r "$key"
}

require_cmd curl
require_cmd jq
require_env BASE_URL
require_env LOGIN_EMAIL
require_env LOGIN_PASSWORD

BASE_URL="${BASE_URL%/}"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "[INFO] Starting staging smoke validation"
echo "[INFO] BASE_URL=$BASE_URL"

echo "[STEP] 1/7 Health check"
HEALTH_CODE=$(curl -sS -o "$WORKDIR/health.json" -w "%{http_code}" "$BASE_URL/api/auth/health")
if [[ "$HEALTH_CODE" != "200" ]]; then
  echo "[ERROR] Health check failed with HTTP $HEALTH_CODE"
  cat "$WORKDIR/health.json"
  exit 1
fi
cat "$WORKDIR/health.json" | jq .

echo "[STEP] 2/7 Login"
LOGIN_CODE=$(curl -sS -o "$WORKDIR/login.json" -w "%{http_code}" \
  -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$LOGIN_EMAIL\",\"password\":\"$LOGIN_PASSWORD\"}")
if [[ "$LOGIN_CODE" != "200" ]]; then
  echo "[ERROR] Login failed with HTTP $LOGIN_CODE"
  cat "$WORKDIR/login.json"
  exit 1
fi
cat "$WORKDIR/login.json" | jq '{email, nome, roles, expiresIn, refreshExpiresIn}'
TOKEN=$(cat "$WORKDIR/login.json" | json_field '.token')
REFRESH=$(cat "$WORKDIR/login.json" | json_field '.refreshToken')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "[ERROR] Missing token in login response"
  exit 1
fi
if [[ -z "$REFRESH" || "$REFRESH" == "null" ]]; then
  echo "[ERROR] Missing refreshToken in login response"
  exit 1
fi

echo "[STEP] 3/7 Validate JWT"
VALIDATE_CODE=$(curl -sS -o "$WORKDIR/validate.json" -w "%{http_code}" \
  "$BASE_URL/api/auth/validate" \
  -H "Authorization: Bearer $TOKEN")
if [[ "$VALIDATE_CODE" != "200" ]]; then
  echo "[ERROR] Validate endpoint failed with HTTP $VALIDATE_CODE"
  cat "$WORKDIR/validate.json"
  exit 1
fi
VALID=$(cat "$WORKDIR/validate.json" | json_field '.valid')
if [[ "$VALID" != "true" ]]; then
  echo "[ERROR] Token reported as invalid"
  cat "$WORKDIR/validate.json"
  exit 1
fi
cat "$WORKDIR/validate.json" | jq .

echo "[STEP] 4/7 Protected route without token must fail"
UNAUTH_CODE=$(curl -sS -o "$WORKDIR/unauth.json" -w "%{http_code}" "$BASE_URL/api/usuarios")
if [[ "$UNAUTH_CODE" != "401" ]]; then
  echo "[ERROR] Expected 401 for unauthenticated request, got $UNAUTH_CODE"
  cat "$WORKDIR/unauth.json"
  exit 1
fi
cat "$WORKDIR/unauth.json" | jq .

echo "[STEP] 5/7 Single upload"
echo "staging-single-upload" > "$WORKDIR/single.txt"
UPLOAD_CODE=$(curl -sS -o "$WORKDIR/upload.json" -w "%{http_code}" \
  -X POST "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@$WORKDIR/single.txt" \
  -F "fileType=documentos")
if [[ "$UPLOAD_CODE" != "200" ]]; then
  echo "[ERROR] Single upload failed with HTTP $UPLOAD_CODE"
  cat "$WORKDIR/upload.json"
  exit 1
fi
cat "$WORKDIR/upload.json" | jq .
UPLOAD_PATH=$(cat "$WORKDIR/upload.json" | json_field '.path')
if [[ "$UPLOAD_PATH" != gs://* ]]; then
  echo "[ERROR] Expected gs:// path, got: $UPLOAD_PATH"
  exit 1
fi

echo "[STEP] 6/7 Batch upload happy path"
echo "batch-file-a" > "$WORKDIR/batch-a.txt"
echo "batch-file-b" > "$WORKDIR/batch-b.txt"
BATCH_CODE=$(curl -sS -o "$WORKDIR/batch.json" -w "%{http_code}" \
  -X POST "$BASE_URL/api/files/batch" \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@$WORKDIR/batch-a.txt" \
  -F "files=@$WORKDIR/batch-b.txt" \
  -F "fileTypes=documentos" \
  -F "fileTypes=certidoes")
if [[ "$BATCH_CODE" != "200" ]]; then
  echo "[ERROR] Batch upload failed with HTTP $BATCH_CODE"
  cat "$WORKDIR/batch.json"
  exit 1
fi
cat "$WORKDIR/batch.json" | jq .
BATCH_COUNT=$(cat "$WORKDIR/batch.json" | jq '.files | length')
if [[ "$BATCH_COUNT" -lt 2 ]]; then
  echo "[ERROR] Expected at least 2 files in batch response, got $BATCH_COUNT"
  exit 1
fi

echo "[STEP] 7/7 Batch error path (no files)"
BATCH_ERR_CODE=$(curl -sS -o "$WORKDIR/batch-error.json" -w "%{http_code}" \
  -X POST "$BASE_URL/api/files/batch" \
  -H "Authorization: Bearer $TOKEN")
if [[ "$BATCH_ERR_CODE" -lt 400 ]]; then
  echo "[ERROR] Expected failure on malformed batch request, got HTTP $BATCH_ERR_CODE"
  cat "$WORKDIR/batch-error.json"
  exit 1
fi
cat "$WORKDIR/batch-error.json" | jq .

echo "[OK] Staging smoke validation completed successfully"
