# Checkpoint Frontend - Etapa 1

Status: concluída
Data: 2026-05-12

## Entregas da etapa
- Projeto Flutter criado em `app_flutter/` (nome compatível com Dart package).
- Fundação técnica implementada:
  - tema e tokens visuais
  - roteamento com guardas
  - sessão/autorização básica
  - cliente HTTP com interceptors de segurança
  - shell inicial para área admin e operacional
- Fluxo de cadastro público protegido por token na rota.
- README do frontend com instruções completas para execução local (web, Android, iOS).

## Estrutura inicial implementada
- `lib/app/` bootstrap, router, theme
- `lib/core/` sessão, segurança, rede
- `lib/features/auth/` login
- `lib/features/admin/` home admin
- `lib/features/operational/` home operacional
- `lib/features/public_registration/` cadastro público tokenizado (placeholder)
- `lib/shared/widgets/` shell base

## Segurança aplicada na fundação
- Header `Authorization` automático via interceptor.
- Header `X-Correlation-ID` automático por requisição.
- Logout forçado em respostas HTTP 401.
- Guardas de rota para sessão e perfil.
- Área admin restrita ao perfil administrador.

## Observações
- O nome da pasta é `app_flutter` porque o comando `flutter create` não aceita hífen no nome do pacote.
- Integração real de login com backend será implementada na Etapa 2.

## Próxima etapa sugerida
Etapa 2: autenticação real + shell funcional inicial
- Integrar `POST /api/auth/login` e refresh
- Persistência segura de sessão por plataforma
- Menu lateral com módulos
- Estados de carregamento/erro padronizados
