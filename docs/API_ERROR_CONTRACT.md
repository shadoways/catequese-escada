# Contrato de Erros da API

Este documento congela o contrato minimo de erro para o frontend.

## Envelope base

Para erros gerais, a API responde em JSON com o campo:

```json
{ "erro": "mensagem" }
```

## Erros de validacao

Alguns endpoints (ex.: usuarios) retornam detalhes de validacao:

```json
{
  "erro": "Validacao falhou",
  "detalhes": {
    "campo": "motivo"
  }
}
```

## Erros 500

- Em producao: resposta mascarada.
- Em dev (`APP_ENV=dev`): inclui stackTrace para depuracao.

Exemplo em dev:

```json
{
  "erro": "Erro interno",
  "stackTrace": "..."
}
```

## Status padrao recomendados para o frontend

- `400` requisicao invalida/validacao
- `401` nao autenticado
- `403` sem permissao
- `404` recurso nao encontrado
- `409` conflito de negocio (ex.: documento duplicado/reassociacao indevida)
- `500` erro interno

## Correlation ID

Toda resposta retorna o header canonico:

- `X-Correlation-ID`

A API aceita entrada em:

- `X-Correlation-ID`
- `X-Request-ID` (compatibilidade)

Para auditoria e suporte, o frontend deve registrar o valor de `X-Correlation-ID` quando houver erro.
