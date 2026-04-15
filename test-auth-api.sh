#!/bin/bash

# ================================================================
# TESTES DE API - SISTEMA DE AUTENTICAÇÃO
# ================================================================
# Script com exemplos de chamadas curl para testar os endpoints
# Data: 2026-03-03
# ================================================================

BASE_URL="http://localhost:8080/api"

echo "🔐 SISTEMA DE AUTENTICAÇÃO - TESTES DE API"
echo "=========================================="
echo ""

# ================================================================
# 1. HEALTH CHECK
# ================================================================

echo "📋 1. Health Check"
echo "-------------------"
curl -X GET "${BASE_URL}/auth/health" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n\n"

# ================================================================
# 2. LOGIN - ADMIN
# ================================================================

echo "📋 2. Login - Admin"
echo "-------------------"
LOGIN_RESPONSE=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@catequese.com",
    "password": "admin123"
  }')

echo "$LOGIN_RESPONSE" | jq '.'

# Extrair token
TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
echo ""
echo "✅ Token extraído: ${TOKEN:0:50}..."
echo ""

# ================================================================
# 3. VALIDAR TOKEN
# ================================================================

echo "📋 3. Validar Token"
echo "-------------------"
curl -X GET "${BASE_URL}/auth/validate" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n\n"

# ================================================================
# 4. LISTAR USUÁRIOS
# ================================================================

echo "📋 4. Listar Todos os Usuários"
echo "-------------------------------"
curl -X GET "${BASE_URL}/usuarios" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# ================================================================
# 5. BUSCAR USUÁRIO POR ID
# ================================================================

echo "📋 5. Buscar Usuário por ID (1)"
echo "--------------------------------"
curl -X GET "${BASE_URL}/usuarios/1" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# ================================================================
# 6. BUSCAR USUÁRIO POR EMAIL
# ================================================================

echo "📋 6. Buscar Usuário por Email"
echo "-------------------------------"
curl -X GET "${BASE_URL}/usuarios/email/admin@catequese.com" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" | jq '.'
echo ""

# ================================================================
# 7. CRIAR NOVO USUÁRIO
# ================================================================

echo "📋 7. Criar Novo Usuário"
echo "------------------------"
curl -X POST "${BASE_URL}/usuarios" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Teste Usuário",
    "email": "teste@catequese.com",
    "password": "senha123",
    "roles": ["CATEQUISTA"]
  }' | jq '.'
echo ""

# ================================================================
# 8. ATUALIZAR USUÁRIO
# ================================================================

echo "📋 8. Atualizar Usuário (buscar ID primeiro)"
echo "---------------------------------------------"

# Buscar ID do usuário criado
NOVO_USUARIO_ID=$(curl -s -X GET "${BASE_URL}/usuarios/email/teste@catequese.com" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.idUsuario')

echo "ID do novo usuário: $NOVO_USUARIO_ID"

if [ "$NOVO_USUARIO_ID" != "null" ]; then
  curl -X PUT "${BASE_URL}/usuarios/${NOVO_USUARIO_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "nome": "Teste Usuário Atualizado",
      "email": "teste@catequese.com",
      "ativo": true,
      "roles": ["CATEQUISTA", "COORDENADOR_COMUNIDADE"]
    }' | jq '.'
  echo ""
fi

# ================================================================
# 9. TOGGLE ATIVO/INATIVO
# ================================================================

echo "📋 9. Toggle Ativo/Inativo"
echo "--------------------------"

if [ "$NOVO_USUARIO_ID" != "null" ]; then
  curl -X PATCH "${BASE_URL}/usuarios/${NOVO_USUARIO_ID}/toggle-ativo" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" | jq '.'
  echo ""
fi

# ================================================================
# 10. SOLICITAR RESET DE SENHA
# ================================================================

echo "📋 10. Solicitar Reset de Senha"
echo "--------------------------------"
curl -X POST "${BASE_URL}/auth/password-reset/request" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@catequese.com"
  }' | jq '.'
echo ""

# ================================================================
# 11. LOGIN COM CREDENCIAIS INVÁLIDAS (deve falhar)
# ================================================================

echo "📋 11. Login com Senha Inválida (deve retornar 401)"
echo "----------------------------------------------------"
curl -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@catequese.com",
    "password": "senhaerrada"
  }' \
  -w "\nStatus: %{http_code}\n"
echo ""

# ================================================================
# 12. ACESSAR ENDPOINT SEM TOKEN (deve falhar)
# ================================================================

echo "📋 12. Acessar Endpoint sem Token (pode retornar dados se não houver autenticação)"
echo "------------------------------------------------------------------------------------"
curl -X GET "${BASE_URL}/usuarios" \
  -H "Content-Type: application/json" \
  -w "\nStatus: %{http_code}\n"
echo ""

# ================================================================
# 13. DELETAR USUÁRIO
# ================================================================

echo "📋 13. Deletar Usuário de Teste"
echo "--------------------------------"

if [ "$NOVO_USUARIO_ID" != "null" ]; then
  curl -X DELETE "${BASE_URL}/usuarios/${NOVO_USUARIO_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\nStatus: %{http_code}\n"
  echo ""
fi

# ================================================================
# 14. VERIFICAR SE USUÁRIO FOI DELETADO
# ================================================================

echo "📋 14. Verificar se Usuário foi Deletado (deve retornar 404)"
echo "-------------------------------------------------------------"

if [ "$NOVO_USUARIO_ID" != "null" ]; then
  curl -X GET "${BASE_URL}/usuarios/${NOVO_USUARIO_ID}" \
    -H "Authorization: Bearer $TOKEN" \
    -w "\nStatus: %{http_code}\n"
  echo ""
fi

# ================================================================
# RESUMO
# ================================================================

echo "=========================================="
echo "✅ TESTES CONCLUÍDOS"
echo "=========================================="
echo ""
echo "Endpoints testados:"
echo "  ✅ GET  /api/auth/health"
echo "  ✅ POST /api/auth/login"
echo "  ✅ GET  /api/auth/validate"
echo "  ✅ POST /api/auth/password-reset/request"
echo "  ✅ GET  /api/usuarios"
echo "  ✅ GET  /api/usuarios/{id}"
echo "  ✅ GET  /api/usuarios/email/{email}"
echo "  ✅ POST /api/usuarios"
echo "  ✅ PUT  /api/usuarios/{id}"
echo "  ✅ PATCH /api/usuarios/{id}/toggle-ativo"
echo "  ✅ DELETE /api/usuarios/{id}"
echo ""
echo "Token JWT: ${TOKEN:0:50}..."
echo ""

