# Matriz de Permissoes (Estado Atual)

Este documento descreve o comportamento atual do backend para consumo do frontend.

## Regras globais

- Todas as rotas nao publicas exigem JWT valido.
- Endpoint publicos: login/refresh/reset/health de auth.

## Restricao por role (hoje)

### Usuarios

Rotas em `/api/usuarios/*` exigem role:

- `COORDENADOR_PAROQUIAL`

### Demais modulos

Rotas abaixo estao acessiveis para qualquer usuario autenticado (sem filtro por role no roteador):

- catequisandos
- fichas (agora aninhadas em catequisandos)
- documentos
- upload de documentos
- comunidades
- turmas
- eventos
- presencas
- catequistas
- coordenadores
- conhecimentos
- permissoes

## Recomendacao para frontend (curto prazo)

- Esconder telas de usuarios para perfis sem `COORDENADOR_PAROQUIAL`.
- Para os demais modulos, considerar liberado para usuario autenticado ate a matriz fina de negocio ser implementada no backend.

## Recomendacao de evolucao (proxima fase)

Definir politicas explicitas por perfil em cada modulo:

- leitura
- criacao
- edicao
- exclusao

Quando essa matriz for aplicada no backend, este arquivo deve ser atualizado e versionado junto.
