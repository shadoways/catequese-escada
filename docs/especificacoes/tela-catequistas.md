# Tela: Consultar Catequistas

**Situação:** rascunho — aguardando aprovação do Gabriel
**Aba:** `data-tab="catequistas"` · **Arquivo:** `catequistas.js`

## 1. Para que serve

**Ver o currículo de formação de um catequista** — o que ele já cursou, o que ainda
falta, e se está dentro do mínimo de conhecimento exigido para ser catequista.

Hoje essa pergunta não tem tela nenhuma. A Chamada tinha ganhado, por engano, uma
seção de Eventos que misturava chamada de turma com chamada de formação — foi
removida (ver `tela-chamada.md` §7). O lugar certo para "este catequista foi nas
formações que precisava?" é este, novo, separado de qualquer chamada do dia a dia:
quem abre esta tela vem **conferir um histórico**, não marcar presença.

Quem abre esta tela vem fazer uma de duas coisas:

- **o coordenador (ou coordenador paroquial)**, olhando a lista: quem está em dia e
  quem precisa ser lembrado de ir às próximas formações;
- **o próprio catequista**, olhando o seu: confirmar que está cumprindo o mínimo.

## 2. Quem usa

| Papel | O que pode fazer aqui |
|---|---|
| Catequista | Vê **só o próprio** currículo — sem lista, sem comparação com os outros. |
| Coordenador | Vê a lista e o currículo dos catequistas que atuam em turma **da comunidade dele** — mesmo recorte da Chamada (`turma.idComunidade`), não o dos matriculados. |
| Coordenador paroquial | Vê a lista e o currículo de todos os catequistas. |

Isto é uma tela nova de leitura sobre dado que já existe (formação, inscrição,
presença) — não sobre dado novo. Ver §4 sobre por que nenhuma migração de banco é
necessária para o que está pedido aqui.

## 3. Regras

1. **O recorte de quem vê quem é de dados, não de tela** — mesmo princípio da
   Chamada. Hoje `/api/catequistas` é CRUD aberto, sem `EscopoAcessoService`
   nenhum: qualquer usuário logado pode listar todos os catequistas da paróquia,
   telefone e endereço incluídos. Esta tela **não** vai reusar esse endpoint como
   está — usa um novo, com escopo, e o CRUD antigo fica como está (mexer nele é
   fora deste pedido; deixo registrado em §8 porque é uma porta destrancada que
   ninguém pediu para eu fechar, mas que eu vi ao pesquisar).
2. **O aproveitamento do ano é UM número, somando os três níveis.** Hoje
   `FrequenciaFormacaoService` já calcula um percentual, mas por formação, uma de
   cada vez. O que você pediu é diferente: presenças e encontros de TODAS as
   formações (diocesana + regional + paroquial) em que o catequista está inscrito
   no ano corrente, somadas antes de dividir — não a média dos percentuais de cada
   uma. Isso é lógica nova (§4), embora os dados que ela lê já existam todos.
3. **As mesmas três regras da frequência de formação valem aqui, exatamente
   porque já valem lá e as duas contas não podem discordar:**
   - só conta encontro `REALIZADO` — o previsto não pode reprovar ninguém antes
     de acontecer;
   - falta `JUSTIFICADA` sai da conta (nem a favor, nem contra) — chuva não é
     falta de ninguém;
   - encontro realizado sem marcação conta como falta — senão bastaria não fazer
     a chamada para todo mundo ficar em dia.
4. **O percentual agregado é `null`, não `0`, quando ainda não há nenhum encontro
   realizado em nenhuma das formações do catequista no ano** — mesmo raciocínio de
   `FrequenciaFormacaoService`: mostrar 0% para quem ainda não teve chance de
   participar assustaria sem motivo. Estado nesse caso é neutro, não vermelho nem
   amarelo (ver §5).
5. **O mínimo desta tela é 80%, fixo — não é o `percentualMinimo` de cada
   formação.** Cada formação individual pode ter seu próprio mínimo (é por isso
   que `Formacao.percentualMinimo` é campo, não constante — dioceses diferentes já
   pediram 80% e 75%). Mas o "conhecimento mínimo para ser catequista" que você
   descreveu é um patamar único, da paróquia, sobre o total do ano — por isso ele
   não deriva do campo de nenhuma formação específica. **Decisão a confirmar em
   §8**, mas o padrão que proponho é 80% fixo.
6. **A cor é sinal de prazo, não só de nota.** Abaixo de 80% não vira alerta
   sozinho — o ano ainda não acabou, e reprovar cedo demais seria o mesmo erro que
   `EncerramentoAnoService` já evita para catequisando. A cor muda com a data:
   - **verde**: atingiu 80% (ou ainda não há apuração — não é falha, é só cedo);
   - **amarelo**: abaixo de 80% e faltam poucos meses para o fechamento do ano de
     formação — é o aviso para o coordenador chamar o catequista para as próximas
     formações;
   - **vermelho**: abaixo de 80% e o ano de formação já fechou.
   Não existe hoje, no sistema, uma "data de fechamento do ano de formação" — ela
   é diferente do fechamento de matrícula/frequência de turma
   (`EncerramentoAnoService`), que é de catequisando, não de catequista. Proponho
   criar duas chaves em `tb_configuracao` (mesmo padrão de
   `frequencia.aviso.percentual`, sem migração de tabela — ver §4): o mês de
   fechamento (padrão **novembro**, do seu próprio exemplo) e quantos meses antes
   disso o amarelo começa (padrão **2**, ou seja, setembro). **Decisão a confirmar
   em §8.**
7. **O checkbox "participou e tem o conhecimento" já existe como dado — não é
   campo novo.** Por formação, é `inscrito nela` **e** `atingiu o percentual
   mínimo daquela formação especificamente` (o que `FrequenciaFormacaoService.
   calcular` já devolve em `atingiuMinimo`). Marcar à mão não faz sentido aqui:
   o checkbox é o retrato de uma chamada que já foi feita, não uma opinião do
   coordenador.
8. **`ConhecimentoCatequista` não entra nesta tela.** É uma entidade separada
   (área de conhecimento em texto livre, sem ligação com formação nem com
   presença) que já existe no código mas não é referenciada em nenhuma tela nem
   em nenhum outro serviço — parece um mecanismo antigo ou nunca terminado. Não
   uso porque a pergunta que você fez ("o catequista participou da formação e
   tem o conhecimento") já é respondida pelo par formação+presença, sem precisar
   de outro cadastro para o coordenador manter em dia. Fica como está, fora do
   escopo — se um dia ela for retomada, é uma tela própria.

## 4. Dados

**Entidades que já existem e são só lidas** (nenhuma migração de banco para o que
foi pedido): `Catequista`, `Formacao`, `FormacaoInscrito`, `PresencaFormacao`,
`Evento` (para a data de cada encontro), `Turma`/`TurmaCatequista` (para o recorte
por comunidade, igual à Chamada).

**Sem tela ainda para CRIAR esse dado.** É importante deixar registrado: hoje não
existe nenhuma tela para o coordenador criar uma formação, inscrever um catequista
ou fazer a chamada de um encontro de formação — só a API crua
(`FormacaoController`), sem interface. Essa tela está desenhada em
`docs/especificacoes/tela-formacao.md`, mas ainda como **rascunho, aguardando
aprovação** (tem inclusive uma decisão em aberto lá, sobre QR code, que trava a
implementação). Ou seja: **esta tela de Consultar Catequistas vai nascer bonita e
vazia em produção**, porque não há hoje como o coordenador popular os dados que
ela mostra pela interface. Por isso o pedido de um script de dados de teste (ver
`sql/formacao-teste/` abaixo) faz todo sentido — sem ele, não dá nem para olhar a
tela funcionando. Aprovar `tela-formacao.md` continua sendo o passo que falta para
a paróquia usar isso de verdade no dia a dia; não é parte deste pedido, só deixo
marcado porque apareceu direto na pesquisa.

**`tb_configuracao` ganha duas linhas novas** (sem migração de tabela — é
chave/valor, mesmo mecanismo de `frequencia.aviso.percentual`):

| Chave | Padrão | Para quê |
|---|---|---|
| `formacao.fechamento.mes` | `11` (novembro) | Mês em que o ano de formação "fecha" — a partir dele, abaixo de 80% vira vermelho. |
| `formacao.alerta.meses_antes` | `2` | Quantos meses antes do fechamento o amarelo começa a aparecer. |

**Endpoints novos** (nenhum dos dois existentes — `/api/catequistas`,
`/api/formacoes` — muda):

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `GET /api/catequistas/curriculo?ano=` | Lista dos catequistas visíveis ao usuário logado, com nome, comunidade, aproveitamento agregado do ano e o estado (verde/amarelo/vermelho/neutro) | Catequista (só ele mesmo), coordenador (da comunidade), coordenador paroquial (todos) |
| `GET /api/catequistas/{id}/curriculo?ano=` | O currículo de um catequista: uma linha por formação em que está inscrito, agrupada por nível (diocesana/regional/paroquial), com data do último encontro com presença, ano, checkbox de "tem o conhecimento", e o aproveitamento agregado do ano | idem, e só o próprio catequista para o próprio id |

Os dois exigem um método novo em `EscopoAcessoService`, no mesmo espírito de
`comunidadesPermitidas()`: `catequistasPermitidos(): List<Long>?` (`null` = todos,
paroquial; para coordenador, os catequistas com turma na comunidade dele; para
catequista, só o próprio id).

**Achado à parte, não deste pedido:** o cálculo agregado que já existe em
`IndicadoresDetalheService.formacao()` (a tabela "Catequista a catequista" dentro
de Indicadores → Formação) soma presença de todas as formações do catequista no
ano — muito perto do que este pedido quer — mas **não exclui `JUSTIFICADA` da
base** como `FrequenciaFormacaoService` faz. São duas contas parecidas discordando
em um detalhe. Esta tela nova implementa a conta correta (excluindo justificada,
regra 3); fica em aberto se o indicador de Formação deveria ser corrigido para
usar a mesma função (§8) — não fiz isso agora porque não foi pedido e mexeria numa
tela que já está em produção.

## 5. Estados da tela

**Lista (coordenador/paroquial):**

| Estado | O que aparece |
|---|---|
| Carregando | "Carregando os catequistas…" |
| Vazio (ninguém no recorte) | "Nenhum catequista vinculado a uma turma da sua comunidade." (coordenador) |
| Erro de conexão | Mensagem de erro, com o motivo quando o backend manda um |
| Preenchido | Uma linha por catequista: nome, comunidade, aproveitamento do ano, selo de cor |

**Currículo (catequista vendo o próprio, ou aberto a partir da lista):**

| Estado | O que aparece |
|---|---|
| Sem nenhuma inscrição no ano | "Você não está inscrito em nenhuma formação este ano — fale com a coordenação." (mesmo texto de `tela-formacao.md`, para não inventar um segundo jeito de dizer a mesma coisa) |
| Inscrito, mas nenhum encontro realizado ainda | Colunas aparecem vazias ("—"), aproveitamento "sem apuração ainda", selo neutro |
| Preenchido | Colunas Diocesana/Regional/Paroquial (formação, data do último encontro com presença, ano, checkbox), aproveitamento agregado do ano, selo de cor |

## 6. Componentes

`.panel`, `.row`, `.ind-filtro` (busca/filtro da lista, reusando o padrão de
Consultar Catequisandos), `.status` com `ok`/`warning`/`error`/`neutro` — **são
exatamente as quatro cores que verde/amarelo/vermelho/neutro pedem**, sem CSS
novo. Nada de fundo sólido saturado para o estado neutro (padrão visual, já
documentado).

Estrutura de lista + detalhe (lista → clique → currículo) segue o mesmo padrão de
Consultar Catequisandos (`tab-consulta`), reusado em vez de inventado.

## 7. O que fica de fora

- **Criar formação, inscrever catequista, fazer a chamada de um encontro de
  formação** — continua sendo `tela-formacao.md`, ainda rascunho. Esta tela só lê.
- **Certificado ou currículo em PDF.** Você pediu "como se fosse um currículo,
  para ver de forma rápida e fácil" — entendo isso como a tela em si, não um
  documento para imprimir/exportar. Se for isso que você quis dizer, é outro
  pedido (viraria trabalho de skill de PDF).
- **Editar o mínimo de 80% por catequista ou por comunidade.** Fica fixo na
  paróquia inteira (a mesma pergunta que `Formacao.percentualMinimo` já resolve
  por formação, no nível que já existe).
- **Histórico de anos anteriores nesta tela.** Aproveitamento é sempre do ano
  corrente, mesmo padrão da Chamada (§7 de `tela-chamada.md`); ano passado já tem
  os indicadores.

## 8. Decisões em aberto

1. **O mínimo de 80% agregado é fixo para a paróquia, ou devia ser configurável
   (como `frequencia.aviso.percentual` já é)?** Proponho fixo por enquanto — você
   não pediu para ajustar, e "o mínimo de conhecimento para ser catequista" soa
   como regra da paróquia, não ajuste fino. Mas é uma linha a mais em
   `tb_configuracao` se você preferir.
2. **Mês de fechamento do ano de formação (padrão novembro) e quantos meses antes
   o amarelo começa (padrão 2, ou seja, setembro)** — você deu novembro só como
   exemplo do vermelho; não disse quando o amarelo deveria começar. Confirma os
   dois números, ou prefere outro critério (ex.: dia fixo tipo 1º de novembro, em
   vez de mês inteiro)?
3. **"Data de participação" nas colunas Diocesana/Regional/Paroquial** — como
   Formação não tem uma data única (tem vários encontros), proponho mostrar a
   data do **último encontro em que o catequista teve presença marcada** naquela
   formação. Serve?
4. **Catequista inscrito em mais de uma formação do mesmo nível no mesmo ano**
   (não deveria ser comum, mas o modelo permite) — mostro as duas na mesma
   coluna, empilhadas, ou é caso raro o bastante para não valer a pena tratar
   agora (e a tela mostra só a mais recente)?
5. **O indicador de Formação (dentro de Indicadores) tem uma conta parecida que
   não exclui `JUSTIFICADA` da base** (achado em §4) — quer que eu corrija aquele
   indicador para usar a mesma regra desta tela nova, ou isso fica para depois,
   como um pedido à parte?
6. **A tela nova aparece para quem no menu** — igual à Chamada e ao Dashboard
   (`somente-logado`, visível a qualquer papel, cada um vendo seu recorte), ou só
   para coordenador/coordenador paroquial, com o catequista comum vendo o próprio
   currículo por outro caminho (ex.: dentro do próprio perfil)? Proponho
   `somente-logado`, igual à Chamada, para manter um padrão só de "quem entra vê
   o que pode, sem tela escondida".

## 9. Como verificar

- [ ] Catequista vê só o próprio currículo — tentar `/api/catequistas/{outro-id}/
      curriculo` como catequista comum é recusado pelo backend, não só escondido
      na tela.
- [ ] Coordenador vê a lista recortada pela comunidade da TURMA do catequista
      (`turma.idComunidade`), não pela comunidade de quem ele matriculou — mesma
      armadilha já documentada em `CLAUDE.md` para a Chamada.
- [ ] Encontro `PREVISTO` (ainda não aconteceu) não entra na conta.
- [ ] Falta `JUSTIFICADA` não conta nem a favor, nem contra.
- [ ] Encontro `REALIZADO` sem marcação conta como falta.
- [ ] Catequista sem nenhum encontro realizado no ano aparece neutro, nunca
      vermelho nem amarelo.
- [ ] Acima de 80%: verde, em qualquer época do ano.
- [ ] Abaixo de 80%, antes da janela de alerta: neutro (não assusta cedo demais).
- [ ] Abaixo de 80%, dentro da janela de alerta: amarelo.
- [ ] Abaixo de 80%, depois do mês de fechamento: vermelho.
- [ ] O checkbox de uma formação bate com `atingiuMinimo` daquela formação
      específica (não com o agregado do ano).
- [ ] Layout em 1280 / 760 / 400px.
- [ ] Script de regressão: `docs/regressao-catequistas.py` (a criar junto com o
      código, depois da aprovação deste modelo).
