# Modelo de especificação de tela

Copie este arquivo para `tela-<nome>.md` e preencha **antes** de escrever código.

O objetivo não é burocracia: é fechar as decisões enquanto elas ainda são baratas.
Refazer modelo de dados depois de a tela existir custa dez vezes mais.

**Como usar:** apague as instruções em itálico conforme preencher. Uma seção que não
se aplica leva "não se aplica" e o motivo — a ausência também é informação. Se você
não souber responder alguma delas, **essa é a pergunta a fazer antes de codar**.

Exemplo preenchido: `tela-agenda.md`.

---

# Tela: _<nome>_

**Situação:** _rascunho / aprovada / implementada_
**Aba:** `data-tab="_<id>_"` · **Arquivo:** `_<nome>_.js`

## 1. Para que serve

_Uma frase. Se precisar de duas, provavelmente são duas telas._

_E, logo abaixo: **o que a pessoa vem fazer aqui**? A tela deve responder isso no
primeiro olhar. A Chamada resolveu assim — o catequista abre o sistema para fazer uma
coisa só, com a turma na frente dele._

## 2. Quem usa

| Papel | O que pode fazer aqui |
|---|---|
| Catequista | |
| Coordenador | |
| Coordenador paroquial | |

_Se um papel **não** enxerga a tela, diga. Se enxerga mas não pode alterar, descreva o
que ele vê no lugar do botão — some, ou aparece desabilitado com explicação?_

## 3. Regras

_As regras de negócio desta tela, numeradas. Para cada uma, **o porquê** — é o porquê
que impede alguém de "simplificar" a regra daqui a seis meses._

_Exemplo do que buscar: "encontro cancelado não conta como falta, porque chuva não
pode virar falta de ninguém"._

## 4. Dados

**Entidades:** _quais tabelas/entidades a tela lê e escreve._

**Endpoints:**

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `GET /api/…` | | |

_Campo novo ou tabela nova? Diga aqui e escreva a migração em `sql/<assunto>/`._

## 5. Estados da tela

_Todos precisam de resposta. Estado mudo é a falha mais comum: "não acontece nada" é
indistinguível de tela quebrada._

| Estado | O que aparece |
|---|---|
| Carregando | |
| Vazio (sem dado nenhum) | |
| Erro de conexão | |
| Sem permissão para agir | _explique **por quê** e **a quem pedir**_ |
| Preenchido | |

## 6. Componentes

_Quais componentes do padrão visual esta tela usa: `.panel`, `.grid`, `.status`,
`.result-item`… **Reusar antes de criar.** Se precisar de um componente novo, diga qual
e por que nenhum existente serve._

_Cores de status: `ok` / `warning` / `error` / `neutro` — nunca fundo sólido saturado
para informação neutra._

## 7. O que fica de fora

_O que foi deliberadamente adiado, e por quê. Sem isso, a próxima pessoa reabre a
discussão do zero — ou pior, implementa achando que foi esquecimento._

## 8. Decisões em aberto

_Perguntas que precisam de resposta humana antes de implementar. Vazio quando tudo
estiver fechado._

## 9. Como verificar

_Além da definição de pronto global (`ESPECIFICACAO-GLOBAL.md` §9), o que é específico
desta tela:_

- [ ] _cenário 1 — ex.: "coordenador sem comunidade vê a explicação, não uma tela muda"_
- [ ] _cenário 2_
- [ ] _script de regressão em `docs/`, se a tela tiver fluxo com mais de dois passos_
