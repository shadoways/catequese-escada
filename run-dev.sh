#!/bin/bash

# ═══════════════════════════════════════════════════════════════════════════════
# Script para rodar a aplicação em DESENVOLVIMENTO
# ═══════════════════════════════════════════════════════════════════════════════

echo "🚀 Iniciando aplicação em DESENVOLVIMENTO (MariaDB local)"
echo ""

# Verificar se senha foi passada como parâmetro
if [ -z "$1" ]; then
    echo "❌ ERRO: Você precisa passar a senha do root do MariaDB"
    echo ""
    echo "Uso:"
    echo "  ./run-dev.sh sua_senha_aqui"
    echo ""
    echo "Exemplos:"
    echo "  ./run-dev.sh abacabb123"
    echo "  ./run-dev.sh root"
    echo "  ./run-dev.sh password123"
    echo ""
    exit 1
fi

# Definir variáveis de ambiente
export SPRING_PROFILES_ACTIVE=dev
export DEV_DB_URL="jdbc:mariadb://localhost:3306/catequese?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
export DEV_DB_USER="root"
export DEV_DB_PASSWORD="$1"

echo "📋 Configuração:"
echo "   Profile: $SPRING_PROFILES_ACTIVE"
echo "   URL: $DEV_DB_URL"
echo "   Usuário: $DEV_DB_USER"
echo "   Senha: ••••••"
echo ""

# Testar conexão ao banco antes de rodar
echo "🧪 Testando conexão ao banco de dados..."
if mysql -u root -p"$1" -h localhost -e "SELECT 1;" > /dev/null 2>&1; then
    echo "✅ Conexão ao banco OK!"
    echo ""
    echo "🚀 Iniciando aplicação..."
    echo "   Aguarde até ver: 'Tomcat started on port(s): 8080'"
    echo ""
    ./gradlew bootRun
else
    echo "❌ ERRO: Não consegui conectar ao MariaDB"
    echo "   Verifique se:"
    echo "   1. MariaDB está rodando: sudo systemctl status mariadb"
    echo "   2. A senha está correta"
    echo "   3. O banco 'catequese' existe"
    echo ""
    exit 1
fi

