# Staging Validation Guide - Go API

Updated: 2026-05-08

## Goal

Validate in staging that the migrated Go API is healthy, protected routes require JWT, and GCS upload endpoints are operational.

## Prerequisites

- Staging base URL (example: `https://api-staging.example.com`)
- Valid credentials for a user with role `COORDENADOR_PAROQUIAL`
- `curl`, `jq` installed locally
- Optional: temporary test bucket prefix policy for cleanup

## Environment Setup

Export variables before running checks:

```bash
export BASE_URL="https://api-staging.example.com"
export LOGIN_EMAIL="admin@catequese.com"
export LOGIN_PASSWORD="admin123"
```

## Option 1: Run Automated Smoke Script

From repository root:

```bash
cd go-api
bash scripts/staging-smoke.sh
```

What this script validates:

- `GET /api/auth/health` returns 200
- `POST /api/auth/login` returns access + refresh tokens
- `GET /api/auth/validate` returns token valid=true
- Protected route rejects missing token
- `POST /api/files` uploads single file and returns `path`/`url`
- `POST /api/files/batch` happy path uploads 2 files
- Batch error path with malformed payload returns 4xx/5xx and does not return success payload

## Option 2: Manual Guided Validation

### 1. Health

```bash
curl -i "$BASE_URL/api/auth/health"
```

Expected:

- HTTP 200
- body contains `"status":"UP"`

### 2. Login

```bash
LOGIN_RESPONSE=$(curl -sS -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$LOGIN_EMAIL\",\"password\":\"$LOGIN_PASSWORD\"}")

echo "$LOGIN_RESPONSE" | jq .
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
REFRESH=$(echo "$LOGIN_RESPONSE" | jq -r '.refreshToken')
```

Expected:

- `token` not empty
- `refreshToken` not empty

### 3. Validate JWT

```bash
curl -sS "$BASE_URL/api/auth/validate" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

Expected:

- `{"valid": true}`

### 4. Protected Route Without Token

```bash
curl -i "$BASE_URL/api/usuarios"
```

Expected:

- HTTP 401

### 5. Single Upload

```bash
echo "staging-single-upload" > /tmp/staging-single.txt
curl -sS -X POST "$BASE_URL/api/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/tmp/staging-single.txt" \
  -F "fileType=documentos" | jq .
```

Expected:

- HTTP 200
- response contains `filename`, `path`, `url`
- `path` starts with `gs://`

### 6. Batch Upload Happy Path

```bash
echo "batch-file-a" > /tmp/staging-batch-a.txt
echo "batch-file-b" > /tmp/staging-batch-b.txt

curl -sS -X POST "$BASE_URL/api/files/batch" \
  -H "Authorization: Bearer $TOKEN" \
  -F "files=@/tmp/staging-batch-a.txt" \
  -F "files=@/tmp/staging-batch-b.txt" \
  -F "fileTypes=documentos" \
  -F "fileTypes=certidoes" | jq .
```

Expected:

- HTTP 200
- response contains `files` array with 2 entries

### 7. Batch Error Path

```bash
curl -i -X POST "$BASE_URL/api/files/batch" \
  -H "Authorization: Bearer $TOKEN"
```

Expected:

- HTTP 400 with message similar to `Arquivos não enviados`

## Atomic Rollback Validation Note

The endpoint `POST /api/files/batch` is implemented with rollback in code and covered in automated tests. In live staging, proving rollback in the exact mid-batch failure scenario requires fault injection (for example, a non-production toggle that forces failure on the second object write). That hook is not currently exposed by API contract.

Recommended next hardening step for staging-only environments:

1. Add a temporary fault-injection flag (disabled by default) to force failure after first successful upload in batch.
2. Execute one controlled test and verify no leftover objects remain.
3. Remove or disable the flag after validation.

## Evidence to Attach to Release Ticket

- Health check output
- Login + validate output (mask tokens)
- Single upload response (`path`, `url`)
- Batch upload response (2 files)
- Batch error-path response
- Script execution log with timestamp
