# Tela: Formação e listas de presença

**Situação:** rascunho — aguardando aprovação do Gabriel
**Aba:** `data-tab="formacao"` · **Arquivo:** `formacao.js`

Cobre três coisas que nasceram juntas no mesmo pedido: a **tela de Formação** (que
nunca existiu), a **lista de presença por QR code** e a **seção de Formação** dentro dos
indicadores de Eventos.

## 1. Para que serve

**Registrar quem esteve em cada encontro** — de formação, antes de tudo, mas também de
qualquer evento que precise de lista.

Hoje o sistema tem um buraco: o backend de formação está inteiro (`/api/formacoes`,
`tb_formacao`, `tb_formacao_inscrito`, `tb_presenca_formacao`) e **não há nenhuma tela**.
Não dá para criar uma trilha de formação, inscrever catequista nem fazer a chamada — o
que significa que o indicador de formação hoje só pode mostrar zero.

Quem abre esta tela vem fazer uma de três coisas:

- **o coordenador**, antes: criar a formação e inscrever os catequistas;
- **quem chega ao encontro**: confirmar a própria presença, pelo celular;
- **o coordenador, depois**: conferir a lista e corrigir o que faltou.

## 2. Duas coisas diferentes com nomes diferentes

Esta é a decisão que organiza o resto, e ela veio de uma pergunta simples: *quem não
apareceu conta como falta?*

| | **Chamada** | **Lista de presença** |
|---|---|---|
| O que é | Lista **fechada** dos inscritos | Registro **aberto** de quem chegou |
| Estados | `PRESENTE` / `FALTA` / `JUSTIFICADA` | só quem confirmou |
| Quem preenche | quem conduz o encontro | cada pessoa, no próprio celular |
| Serve para | o percentual de 80% | saber quem esteve lá |
| Onde vive | `tb_presenca_formacao` (já existe) | `tb_lista_presenca` + itens (novo) |

**Formação usa as duas, e é aí que elas se encontram:** confirmar na lista por QR
**marca `PRESENTE` na chamada**. A lista é a porta; a chamada é o registro. Assim a
frequência de formação continua com **uma fonte só** — `tb_presenca_formacao`, que é o
que `FrequenciaFormacaoService` já lê —, e ninguém precisa reconciliar dois números.

Um retiro não tem inscritos nem percentual: ali existe só a lista. Por isso ela é um
objeto próprio, ligado ao **evento** e não à formação.

## 3. Quem usa

| Papel | O que pode fazer |
|---|---|
| Catequista | **Confirma a própria presença** (QR + login). Vê as formações em que está inscrito e a própria frequência. |
| Coordenador | Tudo isso, mais: abre e fecha a lista de um evento da sua comunidade, e corrige a chamada. |
| Coordenador paroquial | Tudo, em qualquer nível. Cria formação e inscreve catequista. |

**Mudança de permissão:** hoje `FormacaoService.registrarChamada` exige admin. Passa a
aceitar também o **auto-registro**: qualquer usuário logado pode marcar a **própria**
presença, e só ela, através da lista. Corrigir a chamada de outra pessoa continua sendo
de coordenador — quem confirma presença por conta própria não pode marcar terceiros.

## 4. Regras

1. **A lista pertence ao evento, não à formação.** Qualquer evento pode ter uma. É o que
   permite lista em retiro, sacramento e rito sem duplicar o conceito.
2. **Um evento tem no máximo uma lista.** Duas listas para o mesmo encontro seriam duas
   respostas para "quem esteve lá".
3. **A lista tem dono e hora:** quem abriu, quando abriu, quando fechou. Auditoria é
   invariante do projeto (§6.5 da especificação global).
4. **Lista fechada não aceita confirmação**, e diz isso — em vez de aceitar em silêncio
   e sumir com o registro.
5. **Confirmação é identificada, não anônima.** A pessoa faz login; o registro guarda o
   usuário, o horário e se veio pelo QR ou foi lançada à mão. É o login que dá valor de
   prova, muito mais do que uma assinatura desenhada com o dedo.
6. **Ninguém confirma por outro.** O servidor ignora qualquer id que venha do formulário
   e usa o do usuário autenticado. Sem isso, o link do QR vira um jeito de marcar
   presença para a turma inteira.
7. **Confirmar duas vezes não duplica** — a segunda confirmação encontra a primeira e
   responde "você já está na lista", com o horário.
8. **Em evento de formação, confirmar marca `PRESENTE` na chamada.** Se a pessoa não
   estava inscrita, ela entra na lista mas **não** vira presença de formação, e a tela
   avisa: presença sem inscrição não pode contar num percentual cuja base são os
   inscritos.
9. **Quem não confirmou não é falta automática.** A falta continua sendo do fechamento
   da chamada, feito por quem conduz — porque celular sem bateria não é falta de
   ninguém, mesma lógica de "chuva não vira falta".

## 5. Dados

**Tabela nova** (migração em `sql/formacao/`):

| Campo | Tipo | Observação |
|---|---|---|
| `id_lista` | BIGINT | |
| `id_evento` | BIGINT NOT NULL | único: uma lista por evento (regra 2) |
| `titulo` | VARCHAR | o que aparece no celular de quem confirma |
| `token` | CHAR(8) UNIQUE | o que vai no QR e no código curto |
| `situacao` | ENUM | `ABERTA` / `ENCERRADA` — enum, nunca texto |
| `aberta_por`, `aberta_em`, `encerrada_em` | | auditoria |

| Campo | Tipo | Observação |
|---|---|---|
| `id_item` | BIGINT | |
| `id_lista` | BIGINT NOT NULL | |
| `id_usuario` | BIGINT NOT NULL | único junto com `id_lista` (regra 7) |
| `origem` | ENUM | `QR` / `MANUAL` |
| `confirmado_em` | DATETIME | |

**Endpoints:**

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `POST /api/listas` | Abrir a lista de um evento | coordenador |
| `POST /api/listas/{id}/encerrar` | Fechar | coordenador |
| `GET /api/listas/evento/{idEvento}` | A lista e quem já confirmou | logado |
| `GET /api/listas/token/{token}` | O que a página de check-in mostra | logado |
| `POST /api/listas/token/{token}/confirmar` | Confirmar a **própria** presença | logado |
| `POST /api/listas/{id}/lancar` | Lançar alguém à mão | coordenador |

`tb_presenca_formacao`, `tb_formacao` e `tb_formacao_inscrito` **não mudam**.

## 6. O QR code — o que descobri, e o que proponho

Tentei gerar o QR **no navegador, à mão** (sem biblioteca, porque não há build de JS e
depender de CDN quebraria justamente no salão da paróquia com internet ruim).

Escrevi o codificador inteiro — Reed-Solomon, máscaras, formato, posicionamento — e
montei a verificação certa: desenhar o QR e mandar o **OpenCV ler de volta**, porque um
QR errado é, na tela, indistinguível de um certo.

**Não passou.** Consertei dois defeitos reais que a comparação com o codificador de
referência do OpenCV apontou (a separadora branca do finder estava sendo pintada de
escuro; e a função que recalculava "esta célula é reservada?" era uma segunda
implementação da mesma verdade). A estrutura passou a bater módulo a módulo com a
referência — e mesmo assim **nenhum QR decodifica**: o leitor acha o símbolo e desiste
ao ler a tira de formato.

Apaguei o código. Não entrego codificador que eu provei não funcionar, e insistir nele
custaria mais do que vale.

**O que proponho no lugar:** gerar o QR **no servidor**, com a biblioteca
`com.google.zxing:core` — três linhas no `build.gradle.kts` e uma rota
`GET /api/listas/{id}/qr.svg`. É código testado por muita gente, e some com toda a
classe de bug acima.

**Enquanto isso não estiver decidido, a lista funciona sem QR:** a tela mostra o
**código de 8 caracteres** e o link. Quem chega digita o código. O QR é conveniência
sobre o mesmo token — acrescentá-lo depois não muda nada no modelo de dados nem na
tela.

> **Preciso da sua palavra:** posso acrescentar a dependência do ZXing ao
> `build.gradle.kts`? É a única coisa que trava o QR.

## 7. Estados da tela

| Estado | O que aparece |
|---|---|
| Carregando | "Carregando as formações…" |
| Sem formação cadastrada | "Nenhuma formação em 2026" e, para o coordenador, o botão de criar |
| Catequista sem inscrição | "Você não está inscrito em nenhuma formação este ano — fale com a coordenação" |
| Lista aberta | Código, link, e a contagem de quem já confirmou, atualizada ao recarregar |
| Lista encerrada | A lista fechada, com a hora, e sem o botão de confirmar |
| Já confirmou | "Você confirmou presença às 19h42" — e não um botão que não faz nada |
| Sem permissão | O motivo, e a quem pedir |

## 8. O que fica de fora

- **Assinatura desenhada.** Você mencionou as duas formas; escolhi o botão. O login já
  identifica quem confirmou, com horário e origem — um rabisco feito com o dedo prova
  menos, não mais. Se a diocese exigir assinatura em papel, o caminho é a lista
  impressa, e aí a tela ganha um botão de imprimir.
- **Presença por geolocalização ou por proximidade.** Precisão de GPS num salão fechado
  não decide nada, e recusar presença de quem está lá é pior do que aceitar de quem não
  está.
- **Certificado de conclusão da formação.**
- **Catequisando na lista de presença.** Ela é de catequista. Para catequisando já
  existe o caminho da chamada do encontro, a partir do evento.

## 9. Como verificar

- [ ] Coordenador cria formação, inscreve catequista e vê o encontro na lista
- [ ] Catequista confirma a própria presença e vê o horário de volta
- [ ] Confirmar de novo não duplica: responde que já está na lista
- [ ] Lista encerrada recusa confirmação **com explicação**
- [ ] Um usuário **não** consegue confirmar presença de outro, nem forçando o id
- [ ] Em evento de formação, confirmar marca `PRESENTE` na chamada
- [ ] Quem não é inscrito entra na lista mas **não** vira presença de formação, e a tela
      diz por quê
- [ ] Quem não confirmou **não** vira falta sozinho
- [ ] O indicador de Formação passa a mostrar número diferente de zero
- [ ] Layout em 1280 / 760 / 400px — a página de check-in é usada **no celular**, então
      400px é o tamanho principal dela, não o excepcional
