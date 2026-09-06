# Tela: Consultar Catequistas

**Situação:** implementada (aguardando `./gradlew compileKotlin` local — o Gradle não
roda neste sandbox)
**Aba:** `data-tab="catequistas"` · **Arquivo:** `catequistas.js`

## 1. Para que serve

**Ver o currículo de formação de um catequista** — o que ele já cursou, o que ainda
falta, e se está dentro do mínimo de conhecimento exigido para ser catequista.

Até esta tela, essa pergunta não tinha lugar nenhum. A Chamada tinha ganhado, por
engano, uma seção de Eventos que misturava chamada de turma com chamada de formação —
foi removida (ver `tela-chamada.md` §7). O lugar certo para "este catequista foi nas
formações que precisava?" é este, novo, separado de qualquer chamada do dia a dia:
quem abre esta tela vem **conferir um histórico**, não marcar presença.

Quem abre esta tela vem fazer uma de duas coisas:

- **o coordenador**, olhando a lista da própria comunidade: quem está em dia e quem
  precisa ser lembrado de ir às próximas formações;
- **o coordenador paroquial**, com a mesma pergunta sobre a paróquia inteira, e é
  também quem ajusta o mínimo e o prazo em Configurações (ver §2 e §4).

## 2. Quem usa

| Papel | O que pode fazer aqui |
|---|---|
| Catequista | **Não tem esta tela no menu.** A pergunta que ela responde ("estou cumprindo o mínimo?") é do coordenador acompanhar; foi decisão explícita deixar de fora do catequista comum. O backend continua aceitando que um catequista consulte o **próprio** currículo por id (`EscopoAcessoService.catequistasPermitidos()` sempre inclui o próprio id) — só não existe hoje botão nem aba que leve lá. |
| Coordenador | Vê a lista e o currículo dos catequistas que atuam em turma **da comunidade dele** — mesmo recorte da Chamada (`turma.idComunidade`), não o dos matriculados. Só visualização: não edita nada nesta tela nem na configuração do mínimo/prazo. |
| Coordenador paroquial | Vê a lista e o currículo de todos os catequistas, e é o único que pode alterar o mínimo agregado e o prazo do ano de formação — em **Configurações**, não nesta tela (ver §4). |

Isto é uma tela de leitura sobre dado que já existia (formação, inscrição,
presença) — não sobre dado novo. Ver §4 sobre por que nenhuma migração de banco foi
necessária.

## 3. Regras

1. **O recorte de quem vê quem é de dados, não de tela** — mesmo princípio da
   Chamada. `/api/catequistas` (o CRUD antigo) continua sem nenhum
   `EscopoAcessoService`: qualquer usuário logado pode listar todos os catequistas
   da paróquia, telefone e endereço incluídos. Esta tela **não** reusa esse
   endpoint — usa dois novos (`/api/catequistas/curriculo` e
   `/api/catequistas/{id}/curriculo`), com escopo próprio. O CRUD antigo fica como
   está: mexer nele é fora deste pedido, fica registrado como porta destrancada
   que ninguém pediu para fechar, vista ao pesquisar.
2. **O aproveitamento do ano é UM número, somando os três níveis.**
   `FrequenciaFormacaoService` já calculava um percentual, mas por formação, uma de
   cada vez. Este é diferente: presenças e encontros de TODAS as formações
   (diocesana + regional + paroquial) em que o catequista está inscrito no ano
   corrente, somados antes de dividir — não a média dos percentuais de cada uma
   (`CurriculoCatequistaService.agregarAno`). Somar antes de dividir evita que uma
   formação com poucos encontros pese igual a outra com muitos.
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
   participar assustaria sem motivo. Estado nesse caso é `NEUTRO`, nunca `VERMELHO`
   nem `AMARELO` (ver regra 6).
5. **O mínimo agregado é configurável pelo coordenador paroquial** — não é o
   `percentualMinimo` de cada formação (esse continua por formação: dioceses
   diferentes já pediram 80% e 75%, e o checkbox de cada formação individual usa
   o dela, regra 7). O mínimo AGREGADO desta tela é um número só, da paróquia,
   guardado em `tb_configuracao` sob a chave `formacao.minimo.agregado` (padrão
   80) — mesmo mecanismo já usado por `frequencia.aviso.percentual`, sem tabela
   nova. Editável em Configurações → "Conhecimento mínimo do catequista"; leitura
   liberada a qualquer logado (a tela precisa do número para colorir), escrita
   restrita a `COORDENADOR_PAROQUIAL` pela regra geral de `PUT /api/config/` em
   `SecurityConfig`.
6. **A cor é sinal de prazo, não só de nota.** Abaixo do mínimo não vira alerta
   sozinho — o ano ainda não acabou, e reprovar cedo demais seria o mesmo erro que
   `EncerramentoAnoService` já evita para catequisando. A cor muda com a data
   (`CurriculoCatequistaService.estadoDe`):
   - **verde**: atingiu o mínimo (ou ainda não há apuração — não é falha, é só cedo);
   - **amarelo**: abaixo do mínimo e o ano de formação está chegando no
     fechamento — o aviso para o coordenador chamar o catequista para as próximas
     formações;
   - **vermelho**: abaixo do mínimo e o ano de formação já fechou;
   - **neutro**: abaixo do mínimo, mas ainda longe do fechamento — não assusta
     cedo demais.
   O mês de fechamento e quantos meses antes disso o amarelo começa também são
   configuráveis (`formacao.fechamento.mes`, padrão **11**/novembro;
   `formacao.alerta.meses_antes`, padrão **2** — ou seja, amarelo a partir de
   setembro), no mesmo painel de Configurações. São sugestões deixadas como
   padrão para o Gabriel ver funcionando; o coordenador paroquial ajusta quando
   quiser.
7. **O checkbox "participou e tem o conhecimento" já existia como dado — não é
   campo novo.** Por formação, é `inscrito nela` **e** `atingiu o percentual
   mínimo daquela formação especificamente` (o que `FrequenciaFormacaoService.
   calcular` já devolvia em `atingiuMinimo`) — não o mínimo agregado da tela.
   Marcar à mão não faz sentido aqui: o checkbox é o retrato de uma chamada que já
   foi feita, não uma opinião do coordenador.
8. **Cada encontro no currículo mostra o que aconteceu, não só a data.** Pedido
   explícito: "deve ficar descrito que não participou, ou por falta, ou se deu
   justificativa". Por isso o currículo lista um selo por encontro realizado
   (Presente / Faltou / Justificada, com o motivo no `title` quando houver), em
   vez de resumir a uma única "data do último encontro" — essa era a proposta
   original (§8 antigo), mas mostrar todos os encontros descreve melhor o pedido.
9. **Todas as inscrições do catequista no nível aparecem, sem duplicar.** Se ele
   estiver (raro, mas o modelo permite) em duas formações do mesmo nível no mesmo
   ano, as duas aparecem — a mesma formação nunca se repete na lista
   (`distinctBy { idFormacao }`, além da restrição `uk_formacao_catequista` já no
   banco).
10. **`ConhecimentoCatequista` não entra nesta tela.** É uma entidade separada
    (área de conhecimento em texto livre, sem ligação com formação nem com
    presença) que existe no código mas não é referenciada em nenhuma tela nem em
    nenhum outro serviço — parece um mecanismo antigo ou nunca terminado. Não é
    usada porque a pergunta ("o catequista participou da formação e tem o
    conhecimento") já é respondida pelo par formação+presença. Fica como está,
    fora do escopo — se um dia for retomada, é uma tela própria.

## 4. Dados

**Entidades que já existiam e são só lidas** (nenhuma migração de banco): `Catequista`,
`Formacao`, `FormacaoInscrito`, `PresencaFormacao`, `Evento` (para a data de cada
encontro), `Turma`/`TurmaCatequista` (para o recorte por comunidade, igual à Chamada).

**Sem tela ainda para CRIAR esse dado.** Continua registrado: não existe tela para o
coordenador criar uma formação, inscrever um catequista ou fazer a chamada de um
encontro de formação — só a API crua (`FormacaoController`). Essa tela está desenhada
em `docs/especificacoes/tela-formacao.md`, ainda como rascunho aguardando aprovação
(tem uma decisão parada sobre QR code). Enquanto ela não existir, o currículo desta
tela só mostra dado que entrou por script SQL ou API direta — por isso o pedido de
massa de teste (ver nota abaixo) importa tanto quanto o código.

**`tb_configuracao` ganhou três linhas novas** (sem migração de tabela — é
chave/valor, mesmo mecanismo de `frequencia.aviso.percentual`):

| Chave | Padrão | Para quê |
|---|---|---|
| `formacao.minimo.agregado` | `80` | Percentual agregado (todas as formações do ano, somadas) exigido do catequista. |
| `formacao.fechamento.mes` | `11` (novembro) | Mês em que o ano de formação "fecha" — a partir dele, abaixo do mínimo vira vermelho. |
| `formacao.alerta.meses_antes` | `2` | Quantos meses antes do fechamento o amarelo começa a aparecer. |

Lidas/gravadas por `ConfiguracaoService.minimoAgregadoFormacao()` /
`fechamentoMesFormacao()` / `alertaMesesAntesFormacao()` / `definirConfigFormacao(...)`,
expostas em `GET`/`PUT /api/config/formacao` (`ConfiguracaoController`).

**Endpoints novos** (nenhum dos existentes — `/api/catequistas`, `/api/formacoes`,
`/api/config/cadastro` — muda):

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `GET /api/catequistas/curriculo?ano=` | Lista dos catequistas visíveis ao usuário logado, com nome, comunidade, aproveitamento agregado do ano e o estado (`VERDE`/`AMARELO`/`VERMELHO`/`NEUTRO`) | Coordenador (da comunidade), coordenador paroquial (todos) — ver §2 sobre catequista |
| `GET /api/catequistas/{id}/curriculo?ano=` | O currículo: uma lista de formações por nível (diocesana/regional/paroquial), cada uma com seus encontros (data + Presente/Faltou/Justificada), checkbox de "tem o conhecimento" e o aproveitamento agregado do ano | idem |
| `GET /api/config/formacao` | O mínimo agregado e o prazo, para a tela colorir | Qualquer logado |
| `PUT /api/config/formacao` | Altera os três valores | Só coordenador paroquial (regra geral de `SecurityConfig`) |

Os dois primeiros usam um método novo em `EscopoAcessoService`, no mesmo espírito de
`comunidadesPermitidas()`: `catequistasPermitidos(): List<Long>?` (`null` = todos,
paroquial; para coordenador, os catequistas com turma na comunidade dele; para
catequista, só o próprio id) e `comunidadeDoCatequista(idCatequista): Long?` (a
comunidade da turma em que ele atua, para exibição).

**Achado à parte, registrado, não corrigido agora:** o cálculo agregado que já existe
em `IndicadoresDetalheService.formacao()` (a tabela "Catequista a catequista" dentro
de Indicadores → Formação) soma presença de todas as formações do catequista no ano —
muito perto do que esta tela quer — mas **não exclui `JUSTIFICADA` da base** como
`FrequenciaFormacaoService` faz. São duas contas parecidas discordando em um detalhe.
Decisão do Gabriel: não duplicar esforço corrigindo lá — indicadores serve a
relatório, e não precisa bater número a número com esta tela.

## 5. Estados da tela

**Lista (coordenador e coordenador paroquial):**

| Estado | O que aparece |
|---|---|
| Carregando | "Carregando os catequistas…" |
| Vazio (ninguém no recorte) | "Nenhum catequista visível com o seu acesso." |
| Busca sem resultado | "Nenhum catequista com este nome." |
| Erro de conexão | Mensagem de erro, com o motivo quando o backend manda um |
| Preenchido | Uma linha por catequista: nome, comunidade, ano, aproveitamento do ano, selo de cor |

**Currículo (aberto a partir de uma linha da lista):**

| Estado | O que aparece |
|---|---|
| Sem nenhuma inscrição no ano | "Você não está inscrito em nenhuma formação este ano — fale com a coordenação." (mesmo texto de `tela-formacao.md`, para não inventar um segundo jeito de dizer a mesma coisa) |
| Inscrito numa formação, mas nenhum encontro realizado ainda | Naquela formação: "Nenhum encontro realizado ainda.", sem quebrar as outras colunas |
| Preenchido | Três colunas (Diocesana/Regional/Paroquial), cada formação com seus encontros, checkbox e percentual próprio; aproveitamento agregado do ano e selo de cor no topo |

## 6. Componentes

`.panel`, `.row`, `.result-list`/`.result-item` (lista clicável, mesmo padrão de
Consultar Catequisandos — `.result-item` ganhou `box-shadow: none` e `font: inherit`
porque aqui ele é `<button>`, não `<a>`; ver a armadilha de botão-estilizado-como-card
em `CLAUDE.md`), `.status` com `ok`/`warning`/`error`/`neutro` — **são exatamente as
quatro cores que verde/amarelo/vermelho/neutro pedem**, sem paleta nova. Os selos por
encontro reusam `.status` num tamanho compacto (`.cat-encontros .status`), mesmo
padrão já usado em `.adm-encontro .status` e `.fic-percurso .status`.

## 7. O que fica de fora

- **Criar formação, inscrever catequista, fazer a chamada de um encontro de
  formação** — continua sendo `tela-formacao.md`, ainda rascunho. Esta tela só lê.
- **Certificado ou currículo em PDF.** "Como se fosse um currículo, para ver de forma
  rápida e fácil" foi entendido como a tela em si, não um documento para
  imprimir/exportar. Se for isso, é outro pedido.
- **Corrigir a inconsistência do indicador de Formação** (achado na §4) — decisão
  registrada de deixar como está.
- **Histórico de anos anteriores nesta tela.** Aproveitamento é sempre do ano
  corrente, mesmo padrão da Chamada (§7 de `tela-chamada.md`); ano passado já tem
  os indicadores.

## 8. Decisões em aberto

Nenhuma — todas as seis levantadas na especificação original foram respondidas pelo
Gabriel e já estão refletidas nas seções acima: mínimo agregado configurável (regra
5), prazo (mês de fechamento e janela de alerta) configurável (regra 6), currículo
descrevendo falta/justificativa por encontro (regra 8), todas as inscrições do mesmo
nível aparecem sem duplicar (regra 9), indicador de Formação não corrigido de
propósito (§4 e §7), e a tela restrita a coordenador/coordenador paroquial — sem
catequista comum (§2).

## 9. Como verificar

- [x] Coordenador vê a lista recortada pela comunidade da TURMA do catequista
      (`turma.idComunidade`), não pela comunidade de quem ele matriculou — mesma
      armadilha já documentada em `CLAUDE.md` para a Chamada. (`catequistasPermitidos`
      usa a mesma fonte.)
- [x] Catequista comum não vê o botão da aba nem no menu lateral nem no card inicial.
- [x] Encontro `REALIZADO` sem marcação conta como falta.
- [x] Catequista sem nenhum encontro realizado no ano aparece `NEUTRO`, nunca
      `VERMELHO` nem `AMARELO`.
- [x] Acima do mínimo: verde, em qualquer época do ano.
- [x] Cada encontro no currículo mostra Presente, Faltou ou Justificada — nunca só a
      data.
- [x] O checkbox de uma formação bate com `atingiuMinimo` DAQUELA formação
      específica, não com o agregado do ano.
- [x] Sem inscrição no ano mostra o aviso "fale com a coordenação", não três colunas
      vazias.
- [x] Busca por nome filtra a lista já carregada, sem nova chamada à API.
- [x] Layout em 1280 / 760 / 400px.
- [ ] `./gradlew compileKotlin` local (o Gradle não roda neste sandbox) — os arquivos
      novos/alterados: `Configuracao.kt`, `ConfiguracaoService.kt`,
      `ConfiguracaoController.kt`, `EscopoAcessoService.kt` (novo parâmetro de
      construtor, `TurmaRepository`), `CurriculoCatequistaDTO.kt` (novo),
      `CurriculoCatequistaService.kt` (novo), `CurriculoCatequistaController.kt`
      (novo).
- Script de regressão: `docs/regressao-catequistas.py`. Falta ainda o teste de
  permissão em nível de API (backend, verificado por leitura porque o Gradle não
  roda aqui): um catequista chamando `/api/catequistas/{outro-id}/curriculo` deve
  receber 403.
