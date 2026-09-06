# Tela: Chamada

**Situação:** implementada
**Aba:** `data-tab="chamada"` · **Arquivo:** `chamada.js`

## 1. Para que serve

Marcar a presença de um encontro (aula).

O catequista abre o sistema para fazer uma coisa só, com a turma na frente dele: a
chamada de hoje. Coordenador e coordenador paroquial também passam por aqui — para
corrigir uma chamada encerrada (`AdminCatequeseService`/tela de Turmas e Inscrições) é
preciso primeiro achar o encontro, e é esta tela que lista onde cada turma está.

Este menu é só chamada. Chegou a existir aqui uma seção de Eventos (retiro, missa) —
foi removida a pedido: mesmo relacionada, era uma segunda coisa na tela que o
catequista abre para fazer uma coisa só. A presença em evento continua existindo no
backend (ver §7), só não tem mais lugar nesta tela.

## 2. Quem usa

| Papel | O que pode fazer aqui |
|---|---|
| Catequista | Vê e marca a chamada só das turmas em que atua (vínculo em `tb_turma_catequista`, ou o campo antigo de responsável). |
| Coordenador | Vê e marca a chamada de todas as turmas **da comunidade dele** (`turma.idComunidade`) — inclusive as que ainda não têm ninguém matriculado. |
| Coordenador paroquial | Vê e marca a chamada de todas as turmas de todas as comunidades. |

Quem não atua em nenhuma turma (catequista recém-cadastrado, sem vínculo ainda) não
vê erro: vê o aviso "peça ao coordenador paroquial para vincular você às turmas em que
atua" — diferenciado de uma falha de conexão, porque esse é o caso comum, não uma
exceção.

## 3. Regras

1. **O recorte de quem vê o quê é de dados, não de tela** (`EscopoAcessoService` +
   `ChamadaService.minhasTurmas`/`exigirAcessoATurma`). A tela nunca decide sozinha
   quem pode marcar presença em qual turma — ela só filtra, por cima, o que o backend
   já devolveu.
2. **A comunidade de uma turma é a da turma (`turma.idComunidade`), não a de quem está
   matriculado nela.** O recorte do coordenador usava a comunidade dos catequisandos
   matriculados, e isso é outra pergunta: escondia uma turma nova da comunidade dele
   (ainda sem matrícula) e podia mostrar turma de outra comunidade por causa de um
   aluno de fora. Ver a armadilha em `CLAUDE.md`.
3. **O filtro de comunidade/turma no topo não amplia nem reduz o que o usuário pode
   ver** — as opções vêm das próprias turmas que `/api/chamada/minhas-turmas` já
   devolveu para aquele usuário, nunca de `/api/comunidades` (lista a paróquia
   inteira). É por isso que um catequista só vê a comunidade dele no combo, sem
   nenhuma regra nova aqui: o combo simplesmente não tem de onde tirar outra opção.
4. **Nada aparece antes do "Consultar"** — mesmo padrão da tela de Turmas e
   Inscrições. A lista de turmas vinha toda de uma vez, e quem enxerga mais de uma
   comunidade (coordenador, coordenador paroquial) tinha que descartar o resto no
   olho até achar a turma que queria.
5. **Encontro cancelado não conta como falta de ninguém** — feriado, chuva ou
   catequista doente não podem reprovar quem cumpriu o resto. (Regra do backend,
   `ChamadaService`; citada aqui porque a tela antecipa a pergunta do motivo antes de
   enviar.)

## 4. Dados

**Entidades:** `Turma`, `Encontro`, `Presenca`, `Matricula` (para saber quem está na
turma no ano).

**Endpoints:**

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `GET /api/chamada/minhas-turmas` | Turmas do usuário logado, já recortadas por `EscopoAcessoService` | Catequista, coordenador, admin |
| `GET /api/chamada/turma/{id}/encontros` | Histórico de encontros de uma turma | idem, se tem acesso à turma |
| `GET /api/chamada/encontro/{id}` | A lista de presença de um encontro | idem |
| `POST /api/chamada/abrir` | Abre o encontro do dia | idem |
| `POST /api/chamada/encontro/{id}/marcar` | Grava as marcações (sem encerrar) | idem |
| `POST /api/chamada/encontro/{id}/fechar` | Encerra a chamada | idem |
| `POST /api/chamada/encontro/{id}/cancelar` | Cancela o encontro (motivo obrigatório) | idem |
| `POST /api/chamada/encontro/{id}/corrigir` | Corrige chamada já encerrada | Só coordenador paroquial |
| `POST /api/chamada/encontro/{id}/reabrir` | Reabre encontro encerrado | Só coordenador paroquial |

`TurmaChamadaDTO` ganhou `idComunidade`/`nomeComunidade` nesta revisão — só para a
tela montar o filtro sem outra chamada; não é campo novo no banco.

`GET /api/chamada/eventos` e `POST /api/chamada/evento/abrir` continuam existindo em
`ChamadaService`/`ChamadaController` (`eventosParaChamada`, `abrirEvento`), mas **sem
nenhuma tela que os chame** desde que a seção de Eventos saiu daqui — ver §7.

## 5. Estados da tela

| Estado | O que aparece |
|---|---|
| Carregando (silencioso, ao abrir a aba) | Nada visível ainda muda; os combos de filtro são preenchidos por baixo. |
| Antes do "Consultar" | "Escolha a comunidade e a turma e clique em Consultar." no lugar da lista. |
| Vazio (sem turma nenhuma vinculada) | Aviso: "Nenhuma turma vinculada ao seu usuário. Peça ao coordenador paroquial para vincular você às turmas em que atua." |
| Vazio (filtro sem resultado) | "Nenhuma turma com este filtro." |
| Erro de conexão | Mensagem de erro no lugar da lista, com o motivo quando o backend manda um. |
| Preenchido | Um cartão por turma: categoria, matriculados, estado do último/atual encontro. |

## 6. Componentes

`.panel`, `.row` / `.row.ind-nao-imprime` (a barra de filtro não imprime), `.ind-filtro`
(mesma classe da barra de Turmas e Inscrições e de Indicadores — reusada, não
recriada), `.status` com `ok`/`warning`/`error`/`neutro`, `.turma-chamada-card`.

O título da lista (`#cham-titulo-turmas`) é **"Minha turma"** ou **"Minhas turmas"**
conforme quem está logado — não é texto fixo do HTML, é `chamTituloTurmas()` em
`chamada.js`. Um catequista tem normalmente uma turma só (mais de uma é exceção); já
coordenador e coordenador paroquial enxergam várias de propósito — é para isso que
existe o filtro no topo. Mostrar "Minha turma" para um coordenador com oito cartões na
lista leria como tela quebrada, então o singular vale só para `tipo === 'CATEQUISTA'`.
O botão de voltar da chamada do dia virou só **"Voltar"** — não precisa dizer para onde,
e evita repetir a mesma ambiguidade de número num segundo lugar.

## 7. O que fica de fora

- **Eventos (retiro, missa) saíram desta tela** e ficaram sem nenhum ponto de entrada
  no frontend — o endpoint e o serviço continuam no backend (§4), só não há mais
  tela que os chame. Se a presença em evento voltar a ser necessária, precisa de uma
  tela própria (ou um lugar dentro da Agenda, onde o evento já é cadastrado) — não
  deve voltar a dividir esta tela com a chamada de aula, que foi exatamente o
  problema relatado.
- Filtro por ano: a tela sempre usa o ano corrente (`LocalDate.now().year`); não há
  seletor de ano aqui como há em Turmas e Inscrições. Ninguém pediu ainda, e chamada é
  tarefa do dia — abrir a tela num ano passado não é o caso de uso.
- "Corrigir chamada" continua sem um ponto de entrada próprio nesta tela (item já
  registrado em aberto na spec de Turmas e Inscrições, §7.1).

## 8. Decisões em aberto

Nenhuma. O recorte por papel (catequista = turmas em que atua, coordenador = turmas da
comunidade dele, admin = tudo) já estava definido em `ESPECIFICACAO-GLOBAL.md` §3;
esta revisão corrigiu o coordenador para seguir exatamente essa regra e acrescentou o
filtro visual por cima, sem mudar quem vê o quê.

## 9. Como verificar

- [x] Não existe mais seção de Eventos nesta aba.
- [x] Nada aparece antes de clicar em "Consultar".
- [x] Mudar o filtro não consulta sozinho.
- [x] O combo de comunidade só oferece o que `minhas-turmas` devolveu — testado com uma
      resposta de uma turma/uma comunidade só, simulando um catequista.
- [x] Filtrar por comunidade recorta a lista de turmas.
- [x] 400px: nada estoura o pai da aba, e a página não rola na horizontal.
- [x] O botão "Consultar" divide a base com o campo ao lado.
- [x] Catequista vê "Minha turma" (singular); coordenador e coordenador paroquial veem
      "Minhas turmas" (plural).
- [ ] `./gradlew compileKotlin` local (o Gradle não roda neste sandbox) — a mudança em
      `ChamadaService.minhasTurmas` (recorte por `turma.idComunidade`) e o novo
      construtor de `ChamadaService` (parâmetro `comunidadeRepository`) precisam
      compilar antes do merge.
- Script de regressão: `docs/regressao-chamada.py`.
