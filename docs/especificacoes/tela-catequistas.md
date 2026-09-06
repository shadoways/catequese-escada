# Tela: Consultar Catequistas

**Situação:** implementada (aguardando `./gradlew compileKotlin` local — o Gradle não
roda neste sandbox) — **e** aguardando a migração
`sql/conhecimentos/MIGRACAO_CONHECIMENTOS_EXIGIDOS.sql` rodar no banco (ver §4).

**Aba:** `data-tab="catequistas"` · **Arquivo:** `catequistas.js`

## 1. Para que serve

**Ver o histórico de formação de um catequista** — o que ele já cursou, o que ainda
falta, se está dentro do mínimo de conhecimento exigido para ser catequista, e (desde
a segunda rodada deste pedido) **quais conhecimentos de conteúdo ele já possui** e o
**detalhe completo, filtrável, de cada formação em que já esteve inscrito**.

Até esta tela existir, essa pergunta não tinha lugar nenhum. A Chamada tinha ganhado,
por engano, uma seção de Eventos que misturava chamada de turma com chamada de
formação — foi removida (ver `tela-chamada.md` §7). O lugar certo para "este
catequista foi nas formações que precisava, e sabe o que precisa saber?" é este, novo,
separado de qualquer chamada do dia a dia: quem abre esta tela vem **conferir um
histórico e um checklist**, não marcar presença.

Quem abre esta tela vem fazer uma destas coisas:

- **o coordenador**, olhando a lista da própria comunidade: quem está em dia, quem
  precisa ser lembrado de ir às próximas formações, e o que cada um já sabe;
- **o coordenador paroquial**, com a mesma pergunta sobre a paróquia inteira, e é
  também quem ajusta o mínimo/prazo de formação (Configurações, §4) **e** quem decide
  o que entra na lista de conhecimentos exigidos (Configurações, §4) e marca quem já
  tem cada um (aba Conhecimentos, §3 regra 11).

O detalhe de um catequista virou **três abas** (Resumo / Conhecimentos / Formações) —
ver §3 regras 11 a 13 e §5.

## 2. Quem usa

| Papel | O que pode fazer aqui |
|---|---|
| Catequista | **Não tem esta tela no menu.** A pergunta que ela responde ("estou cumprindo o mínimo? tenho os conhecimentos?") é do coordenador acompanhar; foi decisão explícita deixar de fora do catequista comum. O backend continua aceitando que um catequista consulte os **próprios** dados por id (`EscopoAcessoService.catequistasPermitidos()` sempre inclui o próprio id, em curriculo/formações/conhecimentos) — só não existe hoje botão nem aba que leve lá. |
| Coordenador | Vê a lista e as três abas do detalhe dos catequistas que atuam em turma **da comunidade dele** — mesmo recorte da Chamada (`turma.idComunidade`), não o dos matriculados. Só visualização em tudo: não edita nada nesta tela, nem o checklist de conhecimentos, nem a configuração do mínimo/prazo/catálogo. |
| Coordenador paroquial | Vê a lista e o detalhe de todos os catequistas; é o único que pode **marcar o checklist de conhecimentos** de qualquer catequista (aba Conhecimentos), e o único que altera o mínimo agregado/prazo de formação **e** o catálogo de conhecimentos exigidos — os dois em **Configurações**, não nesta tela (ver §4). |

Isto é uma tela de leitura sobre dado que já existia (formação, inscrição, presença) —
**mais uma tela de escrita nova e pequena** (o checklist de conhecimentos, que não
existia antes desta segunda rodada). Ver §4 sobre a única migração de banco que este
pedido precisou.

## 3. Regras

1. **O recorte de quem vê quem é de dados, não de tela** — mesmo princípio da
   Chamada. `/api/catequistas` (o CRUD antigo) continua sem nenhum
   `EscopoAcessoService`: qualquer usuário logado pode listar todos os catequistas
   da paróquia, telefone e endereço incluídos. Esta tela **não** reusa esse
   endpoint — usa rotas próprias (`/api/catequistas/curriculo`,
   `/api/catequistas/{id}/curriculo`, `/api/catequistas/{id}/formacoes`,
   `/api/catequistas/{id}/conhecimentos`), com escopo próprio. O CRUD antigo fica
   como está: mexer nele é fora deste pedido.
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
   restrita a `COORDENADOR_PAROQUIAL` pela regra geral de configuração em
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
   setembro), no mesmo painel de Configurações.
7. **O checkbox "participou e tem o conhecimento" (aba Resumo) já existia como
   dado — não é campo novo.** Por formação, é `inscrito nela` **e** `atingiu o
   percentual mínimo daquela formação especificamente` (o que
   `FrequenciaFormacaoService.calcular` já devolvia em `atingiuMinimo`) — não o
   mínimo agregado da tela, e **não é o mesmo checkbox** da aba Conhecimentos
   (regra 11): este mede presença numa formação; aquele mede um conhecimento de
   conteúdo, marcado à mão. Aqui, marcar à mão não faz sentido: o checkbox é o
   retrato de uma chamada que já foi feita.
8. **Cada encontro (aba Resumo e aba Formações) mostra o que aconteceu, não só a
   data.** Pedido explícito: "deve ficar descrito que não participou, ou por
   falta, ou se deu justificativa". Por isso todo encontro realizado vira um selo
   (Presente / Faltou / Justificada, com o motivo no `title` quando houver), em
   vez de resumir a uma única "data do último encontro".
9. **Todas as inscrições do catequista no nível aparecem, sem duplicar.** Se ele
   estiver (raro, mas o modelo permite) em duas formações do mesmo nível no mesmo
   ano, as duas aparecem — a mesma formação nunca se repete na lista
   (`distinctBy { idFormacao }`, além da restrição `uk_formacao_catequista` já no
   banco). A mesma regra vale para o histórico da aba Formações
   (`CurriculoCatequistaService.historico`).
10. **`ConhecimentoCatequista` (a entidade antiga) não entra nesta tela.** É uma
    entidade separada (área de conhecimento em texto livre, sem catálogo comum,
    ligada a UM catequista) que existe no código mas não é referenciada em
    nenhuma tela nem em nenhum outro serviço — parece um mecanismo antigo ou
    nunca terminado. Fica como está, fora do escopo. **A aba "Conhecimentos"
    desta tela (regra 11) NÃO é essa entidade** — é um catálogo novo,
    `RequisitoConhecimento`/`tb_requisito_conhecimento`, com nome
    deliberadamente diferente para não confundir as duas (ver a armadilha em
    `CLAUDE.md` sobre nome de tipo parecido).
11. **A aba "Conhecimentos" é um checklist do que a paróquia exige de conteúdo**
    — pedido novo (segunda rodada): "Kerigma, Artigos do Credo, Pai Nosso, 10
    Mandamentos, Cristologia, Mariologia, Mandamentos da Igreja" foram dados como
    exemplo e entraram como sugestão inicial no banco (a migração, §4). O
    catálogo (`tb_requisito_conhecimento`) é da PARÓQUIA, não por catequista: o
    coordenador paroquial cadastra/renomeia/inativa em Configurações, e o mesmo
    catálogo aparece no checklist de todo catequista. Uma marca por par
    catequista+conhecimento (`tb_requisito_conhecimento_marcado`) guarda
    `possui` + quem/quando marcou por último (regra 4 do CLAUDE.md). Inativar um
    conhecimento tira ele do checklist de todo mundo **sem apagar** as marcações
    já feitas (regra 3 do CLAUDE.md) — reativar traz tudo de volta.
    **Só o coordenador paroquial marca** (mesma regra 2 desta tela, estendida à
    aba nova); o coordenador de comunidade só visualiza, com os checkboxes
    desabilitados. Quem decide isso é o SERVIDOR
    (`RequisitoConhecimentoService.checklistDoCatequista` devolve `podeEditar =
    escopo.ehAdmin()` pronto) — a tela não recalcula permissão a partir do tipo
    de usuário (mesmo padrão já usado em `FormacaoService.resumo`, campo
    `podeEditar`).
12. **A aba "Formações" é o histórico COMPLETO, não só o ano corrente.** Ao
    contrário do Resumo (regra 2, sempre do ano corrente), aqui existe
    justamente para o filtro de ano/mês pedido ter o que filtrar — uma linha por
    ENCONTRO realizado, de QUALQUER ano em que o catequista já esteve inscrito
    (`CurriculoCatequistaService.historico`, endpoint
    `GET /api/catequistas/{id}/formacoes`). Carrega tudo de uma vez e filtra no
    navegador (mesmo padrão da busca por nome da lista): o volume por catequista
    é pequeno, e evita ida e volta a cada troca de filtro.
13. **Os filtros ficam em grupos SEPARADOS, pedido explícito de usabilidade** —
    "os filtros devem ser separados por grupo visando sempre a usabilidade pra
    que a tela não fique confusa":
    - **Lista de catequistas**: busca por nome + filtro de comunidade, os dois no
      mesmo grupo (são a mesma pergunta — "qual catequista" — vista por dois
      campos), comunidade calculada a partir da própria lista já carregada, sem
      chamada nova à API (mesma lógica da busca por nome).
    - **Aba Formações**: SITUAÇÃO (chip — Todas/Presente/Faltou/Justificada,
      componente `.agenda-chip` já usado na Agenda) num grupo, e PERÍODO (ano +
      mês, dois `<select>`) noutro — são perguntas diferentes ("o que aconteceu"
      vs. "quando"), e misturar os dois numa fileira só ficaria confuso com
      cinco a mais controles na mesma linha.

## 4. Dados

**Entidades que já existiam e são só lidas** (Resumo e Formações, nenhuma migração
nova): `Catequista`, `Formacao`, `FormacaoInscrito`, `PresencaFormacao`, `Evento`
(para a data de cada encontro), `Turma`/`TurmaCatequista` (para o recorte por
comunidade, igual à Chamada).

**Duas tabelas NOVAS, só para a aba Conhecimentos** — migração
`sql/conhecimentos/MIGRACAO_CONHECIMENTOS_EXIGIDOS.sql`, ainda não rodada no banco
(ninguém rodou até o fechamento desta especificação — sem ela, `RequisitoConhecimento`
não tem tabela e a aba Conhecimentos erra 500 em produção):

| Tabela | Para quê |
|---|---|
| `tb_requisito_conhecimento` | O catálogo — id, nome, `ativo` (soft delete, nunca `DELETE`), quem/quando criou e alterou por último. |
| `tb_requisito_conhecimento_marcado` | Uma linha por par catequista+conhecimento: `possui` (booleano), quem/quando marcou por último. `UNIQUE (id_requisito, id_catequista)` — marcar de novo atualiza, nunca duplica. |

A migração também insere os 7 conhecimentos de exemplo pedidos pelo Gabriel (Kerigma,
Artigos do Credo, Pai Nosso, 10 Mandamentos, Cristologia, Mariologia, Mandamentos da
Igreja) — só uma sugestão inicial; o coordenador paroquial ajusta a lista depois, em
Configurações.

**`tb_configuracao` ganhou três linhas** (sem migração de tabela — é chave/valor,
mesmo mecanismo de `frequencia.aviso.percentual`, sem relação com as duas tabelas
acima):

| Chave | Padrão | Para quê |
|---|---|---|
| `formacao.minimo.agregado` | `80` | Percentual agregado (todas as formações do ano, somadas) exigido do catequista. |
| `formacao.fechamento.mes` | `11` (novembro) | Mês em que o ano de formação "fecha" — a partir dele, abaixo do mínimo vira vermelho. |
| `formacao.alerta.meses_antes` | `2` | Quantos meses antes do fechamento o amarelo começa a aparecer. |

Lidas/gravadas por `ConfiguracaoService.minimoAgregadoFormacao()` /
`fechamentoMesFormacao()` / `alertaMesesAntesFormacao()` / `definirConfigFormacao(...)`,
expostas em `GET`/`PUT /api/config/formacao` (`ConfiguracaoController`).

**Endpoints:**

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `GET /api/catequistas/curriculo?ano=` | Lista dos catequistas visíveis ao usuário logado, com nome, comunidade, aproveitamento agregado do ano e o estado | Coordenador (da comunidade), coordenador paroquial (todos) |
| `GET /api/catequistas/{id}/curriculo?ano=` | Aba Resumo: formações por nível (diocesana/regional/paroquial), cada uma com seus encontros, checkbox de "atingiu o mínimo daquela formação" e o aproveitamento agregado do ano | idem |
| `GET /api/catequistas/{id}/formacoes` | Aba Formações: histórico COMPLETO (todos os anos), uma linha por encontro | idem |
| `GET /api/catequistas/{id}/conhecimentos` | Aba Conhecimentos: catálogo ativo + `possui` de cada item, e `podeEditar` já resolvido | idem |
| `PUT /api/catequistas/{id}/conhecimentos/{idRequisito}` | Marca/desmarca um conhecimento (`{ "possui": true/false }`) | Só coordenador paroquial (`SecurityConfig`) |
| `GET /api/conhecimentos-exigidos` | O catálogo inteiro (ativos e inativos), para a tela de gestão em Configurações | Qualquer logado |
| `POST /api/conhecimentos-exigidos` | Cria um conhecimento (`{ "nome": "..." }`) | Só coordenador paroquial |
| `PUT /api/conhecimentos-exigidos/{id}` | Renomeia e/ou (re)ativa (`{ "nome": "...", "ativo": true/false }`) | Só coordenador paroquial |
| `GET /api/config/formacao` | O mínimo agregado e o prazo, para a tela colorir | Qualquer logado |
| `PUT /api/config/formacao` | Altera os três valores do prazo/mínimo | Só coordenador paroquial |

Todos os endpoints de catequista usam `EscopoAcessoService.catequistasPermitidos()` /
`podeVerCatequista(id)` (`null` = todos, paroquial; para coordenador, os catequistas
com turma na comunidade dele; para catequista, só o próprio id) e
`comunidadeDoCatequista(idCatequista)` (a comunidade da turma em que ele atua, para
exibição/filtro).

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
| Busca/filtro sem resultado | "Nenhum catequista com estes filtros." |
| Erro de conexão | Mensagem de erro, com o motivo quando o backend manda um |
| Preenchido | Uma linha por catequista: nome, comunidade, ano, aproveitamento do ano, selo de cor |

**Detalhe — cabeçalho e aba Resumo:**

| Estado | O que aparece |
|---|---|
| Sem nenhuma inscrição no ano | "Você não está inscrito em nenhuma formação este ano — fale com a coordenação." (mesmo texto de `tela-formacao.md`) |
| Inscrito numa formação, mas nenhum encontro realizado ainda | Naquela formação: "Nenhum encontro realizado ainda.", sem quebrar as outras colunas |
| Preenchido | Três colunas (Diocesana/Regional/Paroquial), cada formação com seus encontros, checkbox e percentual próprio; aproveitamento agregado do ano e selo de cor no topo (ACIMA da subnav — vale para as três abas) |

**Aba Conhecimentos:**

| Estado | O que aparece |
|---|---|
| Carregando | "Carregando…" |
| Catálogo vazio | "Nenhum conhecimento cadastrado ainda — cadastre em Configurações." |
| Coordenador de comunidade (só visualiza) | Checklist com todos os checkboxes desabilitados + aviso "Somente o coordenador paroquial pode alterar este checklist." |
| Erro ao marcar | A marcação volta ao estado anterior (o clique é desfeito) + mensagem de erro |

**Aba Formações:**

| Estado | O que aparece |
|---|---|
| Carregando | "Carregando…" |
| Sem nenhum encontro em nenhum ano | "Nenhum encontro de formação registrado para este catequista." |
| Filtro sem resultado | "Nenhum encontro com estes filtros." |
| Preenchido | Uma linha por encontro: formação, nível, ano, data e selo Presente/Faltou/Justificada |

## 6. Componentes

`.panel`, `.row`, `.result-list`/`.result-item` (lista clicável — `.result-item`
ganhou `box-shadow: none`/`font: inherit` porque aqui é `<button>`, não `<a>`),
`.status` com `ok`/`warning`/`error`/`neutro` (as quatro cores de
verde/amarelo/vermelho/neutro, sem paleta nova). Os selos por encontro reusam
`.status` compacto (`.cat-encontros .status`, `.cat-historico-linha .status`).

**Reusados desta rodada, sem CSS novo:**
- `.adm-subnav`/`.adm-subnav-btn` (a sub-navegação de Turmas, Inscrições/
  Transferências) — vira a subnav Resumo/Conhecimentos/Formações. "É o mesmo
  gesto — trocar de assunto dentro de uma tela", como o próprio CSS já
  documentava; duplicar o estilo faria as duas divergirem na primeira mudança.
- `.agenda-filtros`/`.agenda-chip`/`.agenda-filtro-rot` (o filtro de nível/tipo
  da Agenda) — vira o filtro de situação da aba Formações, single-select com
  "Todas" incluso, mesmo padrão de `agenda.js`.
- `.ind-filtro` (rótulo + campo, usado em toda tela com filtro) — vira o filtro
  de comunidade da lista e os selects de ano/mês da aba Formações.

**Novos:** `.cat-conhecimentos-lista`/`.cat-conhecimento-item` (o checklist —
nome de classe deliberadamente diferente de `.cat-conhecimento`, o rótulo "Tem o
conhecimento" já existente na aba Resumo: são coisas diferentes na mesma tela) e
`.cat-historico`/`.cat-historico-linha` (as linhas da aba Formações).

## 7. O que fica de fora

- **Criar formação, inscrever catequista, fazer a chamada de um encontro de
  formação** — continua sendo `tela-formacao.md`, ainda rascunho. Esta tela só lê
  formação/presença (a aba Conhecimentos é a única escrita nova, e é um
  checklist, não uma chamada).
- **Certificado ou currículo em PDF.** Entendido como a tela em si, não um
  documento para imprimir/exportar.
- **Corrigir a inconsistência do indicador de Formação** (achado na §4) — decisão
  registrada de deixar como está.
- **Renomear/apagar de verdade um conhecimento exigido.** Só existe
  criar/renomear/(re)ativar-inativar — nunca `DELETE`, por causa da regra 3 do
  CLAUDE.md (nada é apagado de verdade) e porque inativar já resolve o pedido
  ("poder exigir mais ou menos conhecimentos").
- **Histórico de marcação de conhecimento** (quando cada um foi marcado, por
  quem, ao longo do tempo). `tb_requisito_conhecimento_marcado` guarda só o
  estado ATUAL (quem/quando da última marcação) — a pergunta que a tela faz é
  "ele tem, hoje?", não "quando passou a ter". Se um dia for pedido um
  histórico, é outra tabela.

## 8. Decisões em aberto

As seis levantadas na especificação original (mínimo agregado configurável, prazo
configurável, currículo descrevendo falta/justificativa, inscrições duplicadas sem
duplicar exibição, indicador de Formação não corrigido, tela restrita a
coordenador/paroquial) já foram respondidas pelo Gabriel e estão refletidas acima.

Desta segunda rodada (filtro de comunidade, tirar a palavra "Currículo", aba
Conhecimentos, aba Formações detalhada), duas decisões foram tomadas por EXTENSÃO de
regras já confirmadas, e não por pergunta nova — registradas aqui para o Gabriel
corrigir se a extensão não for o que ele quis:

1. **Quem marca o checklist de Conhecimentos** foi resolvido como "só o
   coordenador paroquial", estendendo a frase já dada sobre a tela inteira
   ("coordenadores de comunidade apenas como visualização e... coordenadores
   paroquiais podem visualizar e alterar se necessário"). Se a intenção era
   deixar o coordenador de comunidade marcar também (ele lida mais de perto com
   o catequista no dia a dia), é uma troca pequena: tirar a restrição de
   `SecurityConfig` e a checagem de `podeEditar` vira só recorte de catequista.
2. **Onde gerenciar o catálogo** (cadastrar/inativar conhecimentos) foi colocado
   em Configurações, junto do "Conhecimento mínimo do catequista" — mesmo padrão
   já usado nesta tela para ajustes do coordenador paroquial. Não foi pedido
   lugar diferente.
3. **"Tire a palavra currículo"** foi entendido como o TEXTO que a pessoa lê —
   trocado no título do detalhe (agora só o nome) e na frase de introdução da
   lista. Os identificadores internos (rota `/curriculo`,
   `CurriculoCatequistaService`, ids `cat-curriculo-*`) continuam com o nome
   antigo de propósito: mudar rota e classe Kotlin só por causa do rótulo na
   tela seria um refactor grande sem ganho para quem usa.

## 9. Como verificar

- [x] Coordenador vê a lista recortada pela comunidade da TURMA do catequista
      (`turma.idComunidade`), não pela comunidade de quem ele matriculou.
- [x] Catequista comum não vê o botão da aba nem no menu lateral nem no card inicial.
- [x] Encontro `REALIZADO` sem marcação conta como falta.
- [x] Catequista sem nenhum encontro realizado no ano aparece `NEUTRO`, nunca
      `VERMELHO` nem `AMARELO`.
- [x] Acima do mínimo: verde, em qualquer época do ano.
- [x] Cada encontro (Resumo e Formações) mostra Presente, Faltou ou Justificada —
      nunca só a data.
- [x] O checkbox de uma formação (Resumo) bate com `atingiuMinimo` DAQUELA
      formação específica, não com o agregado do ano.
- [x] Sem inscrição no ano mostra o aviso "fale com a coordenação", não três
      colunas vazias.
- [x] Busca por nome e filtro de comunidade filtram a lista já carregada, sem
      nova chamada à API — e funcionam juntos.
- [x] O título do detalhe é só o nome, sem a palavra "Currículo".
- [x] As três abas (Resumo/Conhecimentos/Formações) trocam sem recarregar a
      página; o aproveitamento do ano (acima da subnav) aparece nas três.
- [x] Aba Conhecimentos: checkbox marcado bate com `possui` da API; coordenador
      paroquial edita, coordenador de comunidade só visualiza (`podeEditar`
      resolvido no servidor, não recalculado na tela).
- [x] Marcar um conhecimento chama o PUT certo e desfaz o clique se o servidor
      recusar.
- [x] Aba Formações: mostra encontros de TODOS os anos (não só o corrente); os
      filtros de situação (chip) e de ano/mês (select) ficam em grupos
      separados e funcionam em conjunto.
- [x] Layout em 1280 / 760 / 400px, nas três abas do detalhe.
- [ ] `./gradlew compileKotlin` local (o Gradle não roda neste sandbox) — os
      arquivos novos/alterados desta rodada: `RequisitoConhecimento.kt`,
      `RequisitoConhecimentoRepositories.kt`, `RequisitoConhecimentoService.kt`,
      `RequisitoConhecimentoController.kt` (novos);
      `CurriculoCatequistaController.kt`, `CurriculoCatequistaService.kt`,
      `CurriculoCatequistaDTO.kt`, `SecurityConfig.kt` (alterados).
- [ ] Rodar `sql/conhecimentos/MIGRACAO_CONHECIMENTOS_EXIGIDOS.sql` no banco —
      sem isso a aba Conhecimentos erra 500 (tabela não existe).
- Script de regressão: `docs/regressao-catequistas.py`. Falta ainda o teste de
  permissão em nível de API (backend, verificado por leitura porque o Gradle não
  roda aqui): um catequista chamando `/api/catequistas/{outro-id}/curriculo`,
  `/formacoes` ou `/conhecimentos` deve receber 403; um coordenador de comunidade
  chamando `PUT /api/catequistas/{id}/conhecimentos/{idRequisito}` deve receber
  403 (restrito a `COORDENADOR_PAROQUIAL` em `SecurityConfig`).
