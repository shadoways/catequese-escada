# Checkpoint Frontend - Etapa 2

Status: concluída
Data: 2026-05-14

## Entregas implementadas até aqui
- Autenticação real integrada com backend:
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
- Persistência segura de sessão com `flutter_secure_storage`.
- Bootstrap de sessão com rota de loading e guardas de navegação.
- Interceptor HTTP com:
  - `Authorization: Bearer <token>`
  - `X-Correlation-ID`
  - tentativa de refresh em 401 com retry único.
- Logout assíncrono com limpeza de sessão persistida.

## Módulo operacional entregue nesta etapa
- Fluxo de catequisandos com integração real:
  - lista com busca por nome
  - navegação para detalhe
  - leitura de dados completos do catequisando
- Fluxo de fichas aninhadas por catequisando:
  - listar fichas (`GET /api/catequisandos/{id}/fichas/`)
  - criar ficha (`POST /api/catequisandos/{id}/fichas/`)
  - editar ficha (`PUT /api/catequisandos/{id}/fichas/{idFicha}`)
  - excluir ficha (`DELETE /api/catequisandos/{id}/fichas/{idFicha}`)

## Arquivos principais evoluídos
- `app_flutter/lib/core/auth/auth_api.dart`
- `app_flutter/lib/core/session/session_store.dart`
- `app_flutter/lib/core/session/auth_session_controller.dart`
- `app_flutter/lib/core/network/api_client.dart`
- `app_flutter/lib/app/app.dart`
- `app_flutter/lib/app/router/app_router.dart`
- `app_flutter/lib/features/operational/presentation/operational_home_page.dart`
- `app_flutter/lib/features/catequisandos/data/catequisando_api.dart`
- `app_flutter/lib/features/catequisandos/presentation/catequisando_list_page.dart`
- `app_flutter/lib/features/catequisandos/presentation/catequisando_detail_page.dart`
- `app_flutter/lib/features/fichas/data/ficha_api.dart`

## Validação executada
- `flutter test`: passando.
- `flutter build web`: concluído com sucesso (build gerado em `build/web`).
- Observação: existem avisos de compatibilidade Wasm em dependências web, sem bloquear execução web padrão.

## Retomada e validação do erro 405 (web login)
- Correção aplicada nos testes de integração do router Go para nova assinatura de `New(appEnv, corsAllowedOrigins, ...)`.
- Testes direcionados de autenticação HTTP passando:
  - `TestAuthLoginEndpointSuccess`
  - `TestAuthRefreshLogoutLifecycleViaHTTP`
- Novos testes de CORS/preflight adicionados em `internal/http/middleware/cors_test.go` e passando:
  - preflight localhost permitido em `dev`
  - origem não permitida bloqueada em `prod`
- Validação ampla executada com sucesso:
  - `go test ./...`
  - `go vet ./...`

## Retomada concluída (13/05/2026)
- Paginação simples de fichas implementada na tela de detalhe (carregar mais por lote).
- Validação de data ISO (`YYYY-MM-DD`) aplicada na criação e edição de fichas.
- Testes unitários adicionados para:
  - `CatequisandoApi`
  - `FichaApi` (incluindo `update` e `delete`)
- Suíte Flutter validada com sucesso (`TMPDIR=/home/usuario/tmp flutter test`): +7 testes passando.

## Novo ponto de parada
- Fluxo operacional de catequisandos/fichas avançou com paginação e validação de data.
- Base de testes de integração de API no frontend criada e verde.

## Próximo passo executado (backend)
- Validação ISO de data aplicada no backend de fichas para `create` e `update`.
- Erro de domínio para data inválida mapeado para `400 Bad Request` no handler.
- Testes adicionados e validados:
  - `internal/ficha/service_validation_test.go`
  - `internal/http/handlers/ficha_handler_test.go`
- Validação Go focada passando:
  - `go test ./internal/ficha ./internal/http/handlers ./internal/http/router`

## Evolução de UX no frontend (14/05/2026)
- Tela de detalhe de catequisando passou a exibir a mensagem de erro retornada pela API (campo `erro`) nas ações de ficha:
  - criar ficha
  - editar ficha
  - excluir ficha
- Ganho direto: quando backend retornar `400` por data inválida, o usuário verá a causa real em vez de mensagem genérica.
- Validação executada e verde:
  - `TMPDIR=/home/usuario/tmp flutter test`
  - `go test ./internal/ficha ./internal/http/handlers ./internal/http/router`

## Refatoração de consistência (14/05/2026)
- Extração de erro da API centralizada no util compartilhado `core/network/api_error.dart`.
- `AuthApi` e tela de detalhe de catequisando agora usam o mesmo extrator.
- Benefício: padronização de mensagens de erro e redução de duplicação no frontend.
- Validação executada e verde:
  - `TMPDIR=/home/usuario/tmp flutter test`

## Continuidade do fluxo (14/05/2026)
- Padrão de erro compartilhado expandido para mais telas operacionais:
  - lista de catequisandos
  - resumo operacional (home)
- Erros de carregamento agora exibem mensagem amigável derivada da API (com fallback consistente), evitando `toString()` bruto.
- Validação executada e verde:
  - `TMPDIR=/home/usuario/tmp flutter test`

## Encerramento da Etapa 02 (14/05/2026)
- Cobertura adicional criada para util de erro compartilhado (`core/network/api_error.dart`).
- Validação final executada com sucesso:
  - `TMPDIR=/home/usuario/tmp flutter test` (11 testes passando)
  - `TMPDIR=/home/usuario/tmp flutter build web` (build concluído)
  - `go test ./... && go vet ./...` (verde)
- Conclusão: Etapa 02 finalizada com backend e frontend consistentes, testes automatizados atualizados e build web gerado.

## Ajuste de acesso pós-etapa (14/05/2026)
- Navegação atualizada para permitir que usuários admin também acessem rotas operacionais (remoção do redirecionamento forçado de `/operacional` para `/admin`).
- Cards da home administrativa conectados a rotas reais existentes:
  - painel operacional (`/operacional`)
  - catequisandos/fichas (`/operacional/catequisandos`)
- Validação executada e verde:
  - `TMPDIR=/home/usuario/tmp flutter test`

## Planejamento por telas (execução guiada por validação)
- Estratégia acordada: avançar tela a tela. Só partimos para a próxima quando a tela atual estiver aprovada.
- Tela prioritária atual: **Tela de Dados do Catequisando** (detalhe dentro de `/operacional/catequisandos/:id`).
- Registro de status da tela de dados do catequisando: **acessível e funcional**, porém **precisa de bastante ajustes de UX, fluxo e acabamento**.
- Próxima tela da fila: **Tela de Ficha de Inscrição**.

### Mapa completo de telas (estado atual)
1. `Auth - Loading` (`/loading`): estável.
2. `Auth - Login` (`/login`): estável.
3. `Admin - Home` (`/admin`): acessível, com cards conectados a rotas reais.
4. `Operacional - Home` (`/catequese-escada/operacional`): funcional, com resumo e atalhos.
5. `Operacional - Lista de Catequisandos` (`/catequese-escada/catequisando`): funcional, com busca.
6. `Operacional - Detalhe de Dados do Catequisando` (`/catequese-escada/catequisando/:id/dados`): acessível e funcional, em fase de melhorias.
7. `Operacional - Ficha de Inscrição` (`/catequese-escada/catequisando/:id/ficha-inscricao`): tela separada e acessível.
8. `Cadastro Público por Token` (`/cadastro-publico?token=...`): existente, baixa prioridade no ciclo atual.

### Telas/módulos mapeados para próximos ciclos
1. Usuários e Permissões (admin).
2. Auditoria e Sessão (admin).
3. Cadastros Mestre (turmas/comunidades/conhecimentos/permissões).
4. Presença (operacional).
5. Eventos (operacional).
6. Documentos (operacional).

### Etapas planejadas para a Tela de Dados do Catequisando (atual)
1. Etapa Dados 1: revisão visual e hierarquia de informações principais.
2. Etapa Dados 2: organização dos blocos de contato, responsável, turma/comunidade e documentos.
3. Etapa Dados 3: ajustes de mensagens, estados de carregamento e recuperação de erro.
4. Etapa Dados 4: validação final da tela com checklist funcional.

### Próxima tela após aprovação: Ficha de Inscrição
1. Etapa Ficha 1: revisão visual e hierarquia de informação.
2. Etapa Ficha 2: fluxo de criação/edição/exclusão (feedback, estados de carregamento, prevenção de erro).
3. Etapa Ficha 3: filtros, ordenação e refinamentos de paginação.
4. Etapa Ficha 4: testes de widget e validação final da tela.

## Continuidade por telas (19/05/2026)
- Correção aplicada: a tela de detalhe do catequisando agora contém apenas dados do catequisando.
- A seção de ficha foi removida do detalhe e migrada para tela própria (`/catequese-escada/catequisando/:id/ficha-inscricao`).
- Navegação atualizada para padrão contexto/recurso com URLs hierárquicas.
- Compatibilidade mantida para URLs antigas (`/operacional/...`) via redirecionamento.
- Etapa Dados 1 iniciada: tela de dados do catequisando ajustada para padrão visual em blocos (cabeçalho forte + cards por seção + campos em estilo formulário somente leitura), aproximando do layout de referência enviado.
- Refino aplicado após feedback: campos reorganizados em formulário único vertical (sem bloco separado de "Vínculos e acompanhamento").
- Documentos evoluídos de contagem para itens reais vindos da API (`arquivos.itens`), com visualização direta de imagens na própria tela e ampliação em modal.
- Validação executada e verde:
  - `TMPDIR=/home/usuario/tmp flutter test` (11 testes passando)
