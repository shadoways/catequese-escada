# Guia de Integracao Frontend

Este guia resume o fluxo recomendado para iniciar o frontend com o contrato atual da API.

## 1. Fluxo base de autenticacao

1. `POST /api/auth/login`
2. Guardar `token` e `refreshToken`.
3. Enviar `Authorization: Bearer <token>` nas rotas protegidas.
4. Quando necessario, renovar em `POST /api/auth/refresh`.

## 2. Fluxo de cadastro e manutencao de catequisando

1. Criar catequisando em `POST /api/catequisandos/`.
2. Consultar detalhes em `GET /api/catequisandos/{id}`.
3. Atualizar dados cadastrais em `PUT /api/catequisandos/{id}`.

## 3. Fluxo de fichas (historico por catequisando)

Novo contrato (subrecurso):

1. `GET /api/catequisandos/{id}/fichas/`
2. `POST /api/catequisandos/{id}/fichas/`
3. `GET /api/catequisandos/{id}/fichas/{idFicha}`
4. `PUT /api/catequisandos/{id}/fichas/{idFicha}`
5. `DELETE /api/catequisandos/{id}/fichas/{idFicha}`

Observacao: endpoints top-level `/api/fichas/*` foram removidos.

## 4. Fluxo de documentos e upload

1. Upload unificado: `POST /api/documentos/upload` (multipart)
2. Comportamento de save/upsert por `idCatequisando + tipoDocumento`.
3. Reenvio do mesmo tipo sobrescreve registro e tenta remover arquivo anterior.

## 5. Presenca (operacao diaria)

1. Registrar em `POST /api/presencas/`
2. Consultar em `GET /api/presencas/` e `GET /api/presencas/{id}`
3. Atualizar em `PUT /api/presencas/{id}`

## 6. Tratamento de erros no frontend

- Ver [API_ERROR_CONTRACT.md](API_ERROR_CONTRACT.md)
- Usar status HTTP + campo `erro` para mensagens.
- Em erro, registrar `X-Correlation-ID` para suporte.

## 7. Collections para teste

- Colecao focada no novo contrato de fichas: [docs/insomnia/catequese-fichas-aninhadas_collection.json](insomnia/catequese-fichas-aninhadas_collection.json)
- Colecao complementar atualizada: [docs/insomnia/catequese-modulos-complementares_collection.json](insomnia/catequese-modulos-complementares_collection.json)
