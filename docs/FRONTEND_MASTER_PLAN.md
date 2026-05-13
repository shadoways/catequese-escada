# Plano Mestre Frontend (Flutter)

## Objetivo
Construir um frontend Flutter para web e mobile no mesmo repositório, separado do backend em estrutura de pastas, com arquitetura preparada para separação futura sem retrabalho alto.

## Diretrizes de Arquitetura
- Monorepo com separação clara por contexto:
  - go-api/ (backend)
  - app-flutter/ (frontend)
- Contratos de API isolados em camada de dados, sem acoplar UI ao backend.
- Navegação protegida por guards de sessão e perfil.
- Controle de acesso por permissões (RBAC) com area admin e area operacional.
- Tema centralizado com tokens de design para facilitar troca de paleta.

## Estrutura proposta do frontend
- app-flutter/
  - lib/
    - app/
      - app.dart
      - router/
      - theme/
      - config/
    - core/
      - auth/
      - security/
      - network/
      - session/
      - widgets/
      - utils/
    - features/
      - auth/
      - admin/
      - catequisandos/
      - fichas/
      - presencas/
      - eventos/
      - usuarios/
      - documentos/
    - shared/
      - models/
      - constants/
      - extensions/
  - test/
  - integration_test/

## Segurança (frontend e integração)
- JWT em memória para acesso corrente e refresh com estratégia segura por plataforma.
- Interceptor HTTP para:
  - anexar Authorization
  - anexar X-Correlation-ID
  - tratar 401 com refresh controlado
- Guardas de rota por autenticação e perfil.
- Logout forçado em sessão inválida.
- Sanitização e validação em formulários antes de envio.
- Nunca interpolar entrada do usuário em queries locais não parametrizadas.
- Logs sem dados sensíveis.

## Sessão e acesso
- Cadastro público não existirá sem token emitido pelo backend.
- Toda navegação de negócio requer sessão válida.
- Mapeamento inicial de perfis:
  - COORDENADOR_PAROQUIAL: acesso total (admin).
  - COORDENADOR_COMUNIDADE: acesso gerencial local.
  - CATEQUISTA: área operacional (consulta, presença, eventos permitidos).

## UX e identidade visual
- Visual clean empresarial com linguagem de CRM moderno.
- Mesma identidade para web e mobile.
- Cores centralizadas em:
  - lib/app/theme/app_colors.dart
  - lib/app/theme/app_theme.dart
- Tipografia, espaçamento e componentes com tokens para ajuste rápido.

## Etapas do projeto
1. Etapa 0: levantamento, arquitetura, segurança e plano de execução. (concluída)
2. Etapa 1: scaffold Flutter e fundação (tema, router, sessão, network, guards).
3. Etapa 2: autenticação e shell da aplicação (layout admin/operacional).
4. Etapa 3: módulo catequisandos + fichas (rotas aninhadas).
5. Etapa 4: presença e eventos.
6. Etapa 5: admin de usuários e permissões.
7. Etapa 6: documentos/upload e fluxos de atualização.
8. Etapa 7: hardening, testes, observabilidade e preparação de deploy.

## Critérios de pronto para cada etapa
- Build local sem erros.
- Fluxos principais da etapa funcionando fim a fim.
- Arquivo de checkpoint atualizado em docs/checkpoints/.
- Revisão de segurança aplicada na etapa.
