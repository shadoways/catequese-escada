# Checkpoint Frontend - Etapa 0

Status: concluída
Data: 2026-05-12

## Decisões tomadas
- Frontend Flutter ficará no mesmo repositório, separado do backend por pasta dedicada (app-flutter/).
- Estratégia de monorepo com baixo acoplamento para separação futura simples.
- App único para web + Android + iOS, com identidade visual unificada.
- Controle de acesso por sessão e perfil em todas as telas.
- Área administrativa com gestão de usuários e permissões.
- Área operacional para catequistas (catequisandos, presença, consultas e eventos permitidos).

## Segurança definida para implementação
- Interceptor para Authorization e X-Correlation-ID.
- Guardas de rota por autenticação e RBAC.
- Fluxo de refresh controlado e logout em falha de sessão.
- Validação forte de formulários e redução de exposição de dados sensíveis.

## Dependências de backend mapeadas
- Autenticação JWT e rotas protegidas.
- Endpoints de usuários para administração.
- Endpoints aninhados de fichas por catequisando.
- Upload unificado de documentos.

## Próxima etapa planejada
Etapa 1: criar projeto Flutter base (app-flutter/) com:
- fundação de tema (cores centralizadas)
- navegação com guards
- camada de rede
- gestão de sessão
- shell inicial (admin/operacional)
