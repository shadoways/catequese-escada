#!/bin/bash

# Script para rodar a aplicação com variáveis de ambiente

export SPRING_DATASOURCE_URL="jdbc:mariadb://localhost:3306/catequese?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export SPRING_DATASOURCE_USERNAME="root"
if [ -z "$DEV_DB_PASSWORD" ]; then
  echo "❌ Defina DEV_DB_PASSWORD antes de rodar este script."
  echo "Exemplo: export DEV_DB_PASSWORD='sua_senha_local'"
  exit 1
fi
export SPRING_DATASOURCE_PASSWORD="$DEV_DB_PASSWORD"
export SPRING_DATASOURCE_DRIVER_CLASS_NAME="org.mariadb.jdbc.Driver"

echo "🚀 Iniciando aplicação com variáveis de ambiente..."
echo "URL: $SPRING_DATASOURCE_URL"
echo "User: $SPRING_DATASOURCE_USERNAME"

./gradlew bootRun

