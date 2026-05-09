#!/usr/bin/env bash
set -euo pipefail

# Script unico para validar ambiente e iniciar a Go API.
# Uso:
#   ./run.sh                 # usa APP_ENV=dev por padrao
#   ./run.sh --check         # valida e sai
#   ./run.sh --dev           # forca APP_ENV=dev
#   ./run.sh --prod          # forca APP_ENV=prod
#   ./run.sh --prod --check  # valida prod e sai

PROFILE="${APP_ENV:-dev}"
CHECK_ONLY="false"

for arg in "$@"; do
  case "$arg" in
    --dev) PROFILE="dev" ;;
    --prod) PROFILE="prod" ;;
    --check) CHECK_ONLY="true" ;;
    -h|--help)
      cat <<'EOF'
Uso:
  ./run.sh [--dev|--prod] [--check]

Opcoes:
  --dev    Forca APP_ENV=dev
  --prod   Forca APP_ENV=prod
  --check  Apenas valida ambiente, sem iniciar app
EOF
      exit 0
      ;;
    *)
      echo "❌ Argumento invalido: $arg"
      echo "Use --help para ver opcoes."
      exit 1
      ;;
  esac
done

require_non_empty() {
  local name="$1"
  local value="${!name:-}"
  if [ -z "$value" ]; then
    echo "❌ Variavel obrigatoria ausente: $name"
    return 1
  fi
  return 0
}

validate_jwt_secret() {
  local secret="${JWT_SECRET:-}"
  if [ -n "$secret" ] && [ "${#secret}" -lt 64 ]; then
    echo "❌ JWT_SECRET deve ter pelo menos 64 caracteres."
    return 1
  fi
  return 0
}

validate_dev() {
  export APP_ENV="dev"

  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mariadb://localhost:3306/catequese?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-root}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"
  export GCS_BUCKET="${GCS_BUCKET:-catequese-escada-storage}"

  if [ -z "$GCS_BUCKET" ]; then
    echo "❌ GCS_BUCKET nao definido para dev."
    return 1
  fi

  validate_jwt_secret

  echo "✅ Ambiente DEV validado"
  echo "   APP_ENV: $APP_ENV"
  echo "   URL: $SPRING_DATASOURCE_URL"
  echo "   Usuario: $SPRING_DATASOURCE_USERNAME"
  echo "   Senha: ******"
  echo "   Bucket: $GCS_BUCKET"
}

validate_prod() {
  export APP_ENV="prod"

  require_non_empty SPRING_DATASOURCE_URL
  require_non_empty SPRING_DATASOURCE_USERNAME
  require_non_empty SPRING_DATASOURCE_PASSWORD
  require_non_empty JWT_SECRET
  require_non_empty GCS_BUCKET

  validate_jwt_secret

  echo "✅ Ambiente PROD validado"
  echo "   APP_ENV: $APP_ENV"
  echo "   URL: $SPRING_DATASOURCE_URL"
  echo "   Usuario: $SPRING_DATASOURCE_USERNAME"
  echo "   Senha: ******"
  echo "   JWT_SECRET: ******"
  echo "   Bucket: $GCS_BUCKET"
}

if [ "$PROFILE" = "dev" ]; then
  validate_dev
elif [ "$PROFILE" = "prod" ]; then
  validate_prod
else
  echo "❌ Profile invalido: $PROFILE (use dev ou prod)"
  exit 1
fi

if [ "$CHECK_ONLY" = "true" ]; then
  echo "✅ Validacao concluida com sucesso."
  exit 0
fi

echo "🚀 Iniciando aplicacao..."
cd go-api
go run ./cmd/api

