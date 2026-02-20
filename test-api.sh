#!/bin/bash

# =====================================================
# SCRIPT DE TESTE DA API - COMUNIDADES
# =====================================================
# Use este script para testar os endpoints da API

echo "🔍 Testando API de Comunidades..."
echo "=================================="

# Cores para output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BASE_URL="http://localhost:8080"

echo ""
echo -e "${YELLOW}1. Listando todas as comunidades...${NC}"
curl -s "${BASE_URL}/api/comunidades" | python3 -m json.tool
echo ""

echo -e "${YELLOW}2. Buscando comunidade com ID 1...${NC}"
curl -s "${BASE_URL}/api/comunidades/1" | python3 -m json.tool
echo ""

echo -e "${YELLOW}3. Criando nova comunidade...${NC}"
RESPONSE=$(curl -s -X POST "${BASE_URL}/api/comunidades" \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Comunidade Teste",
    "descricao": "Comunidade para teste",
    "ativo": true
  }')
echo "$RESPONSE" | python3 -m json.tool
echo ""

echo -e "${YELLOW}4. Listando catequisandos...${NC}"
curl -s "${BASE_URL}/api/catequisandos" | python3 -m json.tool | head -50
echo ""

echo -e "${YELLOW}5. Buscando catequisando com ID 1...${NC}"
curl -s "${BASE_URL}/api/catequisandos/1" | python3 -m json.tool
echo ""

echo -e "${GREEN}✅ Testes concluídos!${NC}"

