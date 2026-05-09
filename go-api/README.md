# Go API Migration (Incremental)

This folder contains the incremental Go migration of the current Kotlin backend.

## Current Scope

- Modular HTTP service scaffolding
- Environment-based config
- MySQL/MariaDB connection compatible with current project variables
- JWT HS512 foundation (secret >= 64 chars)
- Bcrypt helper functions
- Implemented route coverage for:
  - auth
  - usuarios
  - catequisandos
  - fichas
  - comunidades
  - turmas
  - eventos
  - presencas
  - documentos (including status update)
  - catequistas
  - coordenadores
  - conhecimentos
  - permissoes
  - files upload (`POST /api/files`, `POST /api/files/batch`)
- Hardened API baseline:
  - Public only for auth bootstrap endpoints and health
  - /api/usuarios/** requires COORDENADOR_PAROQUIAL role
  - Remaining `/api/**` protected routes require JWT

## Run

```bash
cd go-api
go mod tidy
go test ./...
go run ./cmd/api
```

## Environment Variables

- PORT (default: 8080)
- JWT_SECRET (must be >= 64 chars)
- JWT_EXPIRATION_MS (default: 900000)
- JWT_REFRESH_EXPIRATION_MS (default: 604800000)
- GCS_BUCKET (default: catequese-escada-storage)
- GOOGLE_APPLICATION_CREDENTIALS_JSON (optional; JSON completo da Service Account)
- UPLOAD_PUBLIC_BASE_URL (default: empty; when set, upload response `url` is generated with this base)
- UPLOAD_MAX_MB (default: 10)

Database options:
- DB_DSN (preferred)
- or Spring-compatible variables:
  - SPRING_DATASOURCE_URL
  - SPRING_DATASOURCE_USERNAME
  - SPRING_DATASOURCE_PASSWORD

## Notes

- No schema changes are performed by this service.
- Upload storage is GCS-only (no local fallback).
- `POST /api/files/batch` is atomic: if any file upload fails, uploaded objects from the same request are rolled back.
- Unknown protected routes now return 404.
