# Catequese CRM - Frontend Flutter

Frontend Flutter do sistema interno de catequese, com base única para Web, Android e iOS.

## Pré-requisitos

- Flutter 3.35+
- Dart 3.9+
- Backend Go em execução (por padrão em `http://localhost:8080`)

## Estrutura (resumo)

- `lib/app/` bootstrap, router e tema
- `lib/core/` sessão, segurança e rede
- `lib/features/` módulos de telas (auth, admin, operacional, etc.)
- `lib/shared/` widgets e utilitários compartilhados

## Como subir localmente para testes

1. Instale dependências:

```bash
cd app_flutter
flutter pub get
```

2. Rode no navegador (Chrome):

```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
```

3. Rode em Android (emulador/dispositivo):

```bash
flutter run -d android --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

4. Rode em iOS (simulador, macOS):

```bash
flutter run -d ios --dart-define=API_BASE_URL=http://localhost:8080
```

## Build Web

```bash
flutter build web --dart-define=API_BASE_URL=https://api.seudominio.com
```

Saída em `build/web`.

## Tema e cores

Para trocar a paleta da paróquia, altere:

- `lib/app/theme/app_colors.dart`

Tema geral e tokens visuais:

- `lib/app/theme/app_theme.dart`

## Segurança (fundação)

- Guardas de rota por sessão e perfil.
- Header de correlação (`X-Correlation-ID`) em todas as requisições.
- Interceptor para `Authorization: Bearer`.
- Logout forçado em `401`.

## Status atual

Etapa 1 implementada: fundação técnica (tema, router, sessão e shell inicial admin/operacional).

