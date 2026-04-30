#!/usr/bin/env bash
set -euo pipefail

# Script unico para validar ambiente e iniciar a aplicacao.
# Uso:
#   ./run.sh                 # usa profile dev por padrao
#   ./run.sh --check         # valida e sai
#   ./run.sh --dev           # forca profile dev
#   ./run.sh --prod          # forca profile prod
#   ./run.sh --prod --check  # valida prod e sai

PROFILE="${SPRING_PROFILES_ACTIVE:-dev}"
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
  --dev    Forca profile dev
  --prod   Forca profile prod
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
  # AuthService exige >= 64 bytes para HS512.
  if [ -n "$secret" ] && [ "${#secret}" -lt 64 ]; then
    echo "❌ JWT_SECRET deve ter pelo menos 64 caracteres."
    return 1
  fi
  return 0
}

validate_dev() {
  export SPRING_PROFILES_ACTIVE="dev"

  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mariadb://localhost:3306/catequese?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-root}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-}"

  if [[ "$SPRING_DATASOURCE_URL" != jdbc:mariadb://* ]]; then
    echo "⚠️ SPRING_DATASOURCE_URL normalmente deve comecar com jdbc:mariadb://"
    echo "   URL atual: $SPRING_DATASOURCE_URL"
  fi

  if [ -z "$SPRING_DATASOURCE_PASSWORD" ]; then
    echo "❌ Senha do banco nao definida para dev."
    echo "   Defina SPRING_DATASOURCE_PASSWORD."
    return 1
  fi

  validate_jwt_secret

  echo "✅ Ambiente DEV validado"
  echo "   Profile: $SPRING_PROFILES_ACTIVE"
  echo "   URL: $SPRING_DATASOURCE_URL"
  echo "   Usuario: $SPRING_DATASOURCE_USERNAME"
  echo "   Senha: ******"
}

validate_prod() {
  export SPRING_PROFILES_ACTIVE="prod"

  require_non_empty SPRING_DATASOURCE_URL
  require_non_empty SPRING_DATASOURCE_USERNAME
  require_non_empty SPRING_DATASOURCE_PASSWORD
  require_non_empty JWT_SECRET

  if [[ "$SPRING_DATASOURCE_URL" != jdbc:mysql://* ]]; then
    echo "❌ Em prod a URL deve comecar com jdbc:mysql://"
    echo "   URL atual: $SPRING_DATASOURCE_URL"
    return 1
  fi

  validate_jwt_secret

  echo "✅ Ambiente PROD validado"
  echo "   Profile: $SPRING_PROFILES_ACTIVE"
  echo "   URL: $SPRING_DATASOURCE_URL"
  echo "   Usuario: $SPRING_DATASOURCE_USERNAME"
  echo "   Senha: ******"
  echo "   JWT_SECRET: ******"
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
./gradlew bootRun --args="--spring.profiles.active=$SPRING_PROFILES_ACTIVE"

