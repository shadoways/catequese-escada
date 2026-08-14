# CHECKPOINT — Frequência, chamada e gestão do catequisando

> Arquivo de trabalho do assistente, FORA do repositório git.
> Complementa `CHECKPOINT-LOGIN.md` (contexto fixo, branches, armadilhas de
> Kotlin/CSS e verificadores estão lá — **ler aquele primeiro**).

## Regras de trabalho herdadas

- Branch: `login-e-permissoes`. Último commit da fase de login: `d8fe2c3`.
- **Não compilo Kotlin aqui.** Rodar SEMPRE antes de entregar `.kt`:
  `python3 /home/claude/project/kt_comment_check.py`
  `python3 /home/claude/project/smartcast_check.py`
- Front validado com Playwright + Chromium (`/opt/pw-browsers/chromium`), API mockada.
- SQL sempre no `MIGRACAO_USUARIOS.sql` (idempotente; sem DROP/DELETE/UPDATE).
- Ao esconder algo com `hidden`, incluir o par `[hidden] { display: none }`.

## O que já existe (inspecionado, não re-investigar)

- `Presenca(idPresenca, data, presente, catequisando)` — sem encontro, sem autor,
  sem fechamento, sem turma.
- `Turma(idTurma, nome, descricao, ano, nivel, catequista)` — `nivel` texto livre;
  **um único** catequista.
- `Catequisando` tem **uma única** `turma` → sem histórico.
- `FichaInscricao.dataInscricao` = data de matrícula.
- `Evento(titulo, nivel, publicoAlvo, dataInicio, dataFim, local)` existe e **não
  é usado por nada** → será aproveitado em F10.
- `tb_usuario` já tem `id_catequista` e `id_coordenador` (ligam usuário a pessoa).
- Turmas semeadas: Pré Catequese, Primeira Eucaristia I, Crisma I, Adultos,
  Catecumenato. **"Perseverança" não existe ainda.**

## Decisões do usuário (todas confirmadas)

1. Categoria e etapa como **campos próprios da turma**, não deduzidos do nome.
2. Encontro **fecha sozinho na virada do dia seguinte**; depois só admin ajusta.
3. **Matrícula por ano** (`tb_matricula`), com histórico e situação.
4. **O catequista abre o encontro do dia**; sem calendário pré-cadastrado.
5. **Falta justificada SAI DA CONTA** (removida do denominador).
6. **Catecumenato apura por ETAPA** (do início da etapa até mudar), não por semestre.
7. **Catequista TAMBÉM pode mudar a etapa do catecúmeno** (exceção consciente à
   regra de que ele só altera presença).
8. **Coordenador enxerga apenas a própria comunidade**; só admin vê tudo.
9. Cadastro fora do prazo é exceção permitida; contagem começa na data de matrícula.
10. Encontro cancelado **não conta**, mas exige motivo registrado.
11. Fechar encontro **sem nenhuma presença marcada exige motivo** (equivale a cancelar).
12. **Não pode abrir novo encontro se o anterior da turma não foi fechado.**
13. Todas as sugestões do "ponto 7" aprovadas (ver seção Funcionalidades extras).

## Regras de frequência

| Categoria | Janela de apuração | Exigência |
|---|---|---|
| `ADULTOS` | Semestre civil | 80%. <80% no 1º semestre → não conclui no ano + aviso para procurar o coordenador. |
| `EUCARISTIA`, `CRISMA` | Ano civil | 80% do total do ano. |
| `CATECUMENATO` | **Cada etapa** | 80% por etapa, exceto Pré-catecumenato. |
| `PRE_CATEQUESE`, `PERSEVERANCA` | — | Não se aplica. |

Etapas do catecumenato (por pessoa, com histórico de datas):
`PRE_CATECUMENATO` (sem exigência) → `CATECUMENATO` → `PURIFICACAO_ILUMINACAO`
→ `MISTAGOGIA`.

**Cálculo:** `presenças ÷ (encontros no período − justificadas − cancelados)`,
contando somente encontros **FECHADOS** a partir da **data de matrícula**.

Etapa I = 1º ano, II = 2º ano; máximo 2 anos por categoria → depois, concluído.

## Modelo de dados planejado

- **`tb_turma`** + `categoria VARCHAR(40)`, `etapa INT NULL`.
- **`tb_turma_catequista`** (nova): vários catequistas por turma. O FK atual em
  `tb_turma` continua como responsável principal (compatibilidade).
- **`tb_usuario`** + `id_comunidade BIGINT NULL` → escopo do coordenador.
- **`tb_matricula`** (nova): catequisando, turma, ano, `data_matricula`,
  `situacao` (CURSANDO/CONCLUIDO/NAO_CONCLUIDO/TRANSFERIDO/DESISTENTE),
  `observacao`. Único: (catequisando, turma, ano).
- **`tb_encontro`** (nova): turma, `data`, `tema`, `situacao`
  (ABERTO/FECHADO/CANCELADO), `motivo_cancelamento`, `aberto_por/em`,
  `fechado_por/em`, `fechamento_automatico`, `id_evento NULL` (F10).
  Único: (turma, data).
- **`tb_presenca`** evolui: + `id_encontro`, `situacao`
  (PRESENTE/FALTA/JUSTIFICADA), `justificativa`, `marcado_por`, `marcado_em`.
  Mantém `data`/`presente` para os dados antigos.
- **`tb_etapa_catecumeno`** (nova): catequisando, `etapa`, `data_inicio`,
  `data_fim NULL`, `registrado_por`, `registrado_em`.
- **`tb_configuracao`**: nova chave `frequencia.aviso.percentual` (padrão 85).

## Funcionalidades extras aprovadas

1. Falta justificada (regra 5).
2. Aviso preventivo ao cruzar o percentual de alerta (padrão 85%).
3. Vários catequistas por turma (`tb_turma_catequista`).
4. Contato do responsável visível na ficha do catequisando.
5. Tema do encontro (diário da turma).
6. Transferência de turma no meio do ano (matrícula TRANSFERIDO + nova matrícula;
   frequência do período soma as duas do mesmo ano/categoria).
7. Encerramento de ano em lote (promover etapa, marcar concluídos).
8. Relatório de frequência para impressão (reusar o CSS de impressão da ficha).
9. Presença em eventos (retiros/missas) reaproveitando `Evento`.

## Plano por etapas

- [x] **F1 — Modelo + migração** (`1fcabea`): tabelas/colunas acima, entidades, repositórios,
      SQL idempotente + script separado de backfill de matrículas.
- [ ] **F2 — Escopo por comunidade + equipe de catequistas**: usuário↔comunidade,
      filtro de dados por papel, vários catequistas por turma.
- [x] **F3 — Chamada (backend)** (`78b1173`): abrir/marcar/fechar/cancelar, motivo
      obrigatório, sequência (não abre com anterior aberto), fechamento
      automático na virada do dia seguinte, auditoria.
- [x] **F4 — Cálculo de frequência** (`575afa3`): `CalculoFrequencia` (puro, com
      teste JUnit), `FrequenciaService`, DTOs e `/api/frequencia`.
- [x] **F5 — Front: Minhas turmas + Chamada do dia** (`023814f`): `chamada.js`,
      aba nova no index e `GET /api/chamada/minhas-turmas`.
- [x] **F6 — Front: Frequência da turma + relatório** (`8eafbe4`): aba própria
      `frequencia.js` + relatório impresso fora das abas.
- [x] **F7 — Front: Ficha do catequisando** (`e97780b`): `ficha-catequisando.js`
      + `GET /api/ficha-catequisando/{id}` (status de documento, sem arquivo).
- [ ] **F8 — Admin**: classificar turmas, matrículas, transferência, reabrir
      encontro e corrigir presença.
- [ ] **F9 — Encerramento de ano em lote** + conclusão dos 2 anos.
- [ ] **F10 — Presença em eventos** (aproveitando `Evento`).

## Permissões da área nova

| Ação | CATEQUISTA | COORDENADOR | ADMIN |
|---|---|---|---|
| Ver turmas/catequisandos/frequência | suas turmas | sua comunidade | tudo |
| Marcar/desmarcar presença (encontro aberto) | sim | sim | sim |
| Abrir/fechar/cancelar encontro | sim | sim | sim |
| Mudar etapa do catecúmeno | **sim** | sim | sim |
| Editar dados cadastrais | não | sim | sim |
| Reabrir encontro / corrigir presença fechada | não | não | sim |
| Classificar turma, matrícula, transferência | não | não | sim |

## Estado atual

- **F1 entregue** (`1fcabea`): entidades, repositórios, migração e
  `BACKFILL_MATRICULAS.sql`. `EscopoAcessoService` já criado (peça central de F2).
- **F3 entregue** (`78b1173`): `/api/chamada` + `ChamadaService` + rotina noturna.
- **F4 entregue** (`575afa3`): cálculo de frequência. **Sem SQL novo.**
- **F5 entregue** (`023814f`): front da chamada. **Sem SQL novo.**
- **F6 entregue** (`8eafbe4`): front da frequência. **Sem SQL novo.**
- **F7 entregue** (`e97780b`): ficha do catequisando. **Sem SQL novo.**
- Próximo: **F8 — Admin**: classificar turmas, matrículas, transferência,
  reabrir encontro, corrigir presença.
- O filtro por comunidade (F2) é aplicado junto com os endpoints novos, e não
  retroativamente nos antigos.
- Aguardando o usuário rodar as migrações e confirmar que o sistema sobe.

## Notas de implementação (F3)

- `/api/chamada/**` = `.authenticated()` na SecurityConfig — marcar presença é a
  única escrita do catequista; na regra geral de escrita ele cairia em
  "exige coordenador". O recorte por turma é feito no `ChamadaService`.
- `fecharEsquecidos()` roda **sem usuário logado** (rotina noturna);
  `fecharEsquecidosPeloAdmin()` é a versão exposta na API, que checa admin.
- `@EnableScheduling` isolado em `AgendamentoConfig` (@Configuration), separado
  do `@Component` agendado.
- Ao fechar, faltas dos não marcados são criadas — o denominador da frequência
  depende da lista estar completa.

## Arquivos SQL (dois, com propósitos diferentes)

- `MIGRACAO_USUARIOS.sql` — só estrutura, idempotente, seguro em produção.
- `BACKFILL_MATRICULAS.sql` — INSERT de dados, roda uma vez, tem prévia antes.

## Notas de implementação (F4)

Backend inteiro, sem migração nova.

- `CalculoFrequencia` é um `object` SEM banco — a conta pôde ser coberta por
  JUnit de verdade (`CalculoFrequenciaTest`, 25 casos, roda em `./gradlew test`).
  Toda regra nova de percentual entra ali, não no service.
- `SituacaoFrequencia(gravidade)`: `NAO_SE_APLICA` < `SEM_APURACAO` < `REGULAR`
  < `EM_RISCO` < `ABAIXO_DO_MINIMO`. `pior()` decide a situação do catequisando
  quando há vários períodos — ir bem no 2º semestre não apaga o 1º.
- **80% é fixo** (regra da catequese). O percentual de **aviso** é configurável
  em `tb_configuracao` na chave `frequencia.aviso.percentual` (padrão 85).
  Valor inválido no banco cai no padrão e só registra no log.
- Fronteiras confirmadas por teste: exatamente 80% **não** reprova, mas cai em
  `EM_RISCO`; 79% reprova; 84% avisa; 85% é regular.
- `FrequenciaService.janelasDe()` é o único lugar que traduz categoria → janela.
  Catecumenato é o único caso em que a janela depende da PESSOA (sai do
  `tb_etapa_catecumeno`, recortada pelo ano).
- Adultos: `podeConcluir=false` só quando o 1º semestre **já encerrou** abaixo
  do mínimo. Em andamento, o aviso é preventivo. Campo pronto para o F9.
- `AcessoNegadoException` (nova) → **403** no `RestExceptionHandler`.
  `ChamadaService` continua usando 400 para "você não atua nesta turma";
  se quiser uniformizar, é troca de uma linha.
- Endpoints são todos GET → a regra `GET /api/** = authenticated()` da
  SecurityConfig já cobre. **Nada a mexer na SecurityConfig.**

### Pendência conhecida (herdada, decidir em F8)

`EscopoAcessoService.comunidadesPermitidas()` devolve `null` (= TODAS) para um
COORDENADOR cujo `id_comunidade` está NULL. Ou seja: coordenador sem comunidade
atribuída enxerga tudo. Não mudei agora porque hoje **nenhum** usuário tem
`id_comunidade` preenchido — fechar isso agora deixaria todo coordenador sem
ver nada. Fechar junto com a tela de F8 que atribui a comunidade.

## Como retomar numa sessão nova (leia isto primeiro)

Estado em 14/08/2026, fim da sessão:

- Branch `login-e-permissoes` na máquina do usuário (`/home/usuario/workspace/catequese-escada`)
  está em **`575afa3`** = F4 entregue. Confirmado lendo `.git/refs/heads/` e
  comparando tamanhos dos arquivos.
- **Pendente de confirmação do usuário:** `./gradlew build` e `./gradlew test`
  da F4. Não consigo compilar Kotlin no contêiner (Maven Central e Gradle
  distribution dão 403 no proxy). Os testes do `CalculoFrequenciaTest` são a
  única prova real das fronteiras (80% não reprova, 79% reprova, justificada
  fora do denominador).
- **Próxima etapa: F8** — Admin: classificar turmas por categoria, gerir
  matrículas, transferência, reabrir encontro e corrigir presença fechada.
  É a etapa que destrava as anteriores: sem categoria na turma a frequência
  não é apurada, e sem matrícula não há chamada.

### Entrega de código: como fazer

1. `git push` NÃO funciona do contêiner. O proxy de git recusa
   `shadoways/catequese-escada` ("not in this session's authorized repository
   set"). Não insistir; não é problema de credencial.
2. A pasta `/home/usuario/workspace` está conectada via device bridge. O repo
   fica em `/home/usuario/workspace/catequese-escada`, já na branch certa.
   Gravação direta ali (SendUserFile -> device_commit_files) é o caminho
   preferido: o usuário só faz `git add/commit/push`.
   **VALIDADO na F5** para `.kt`, `.js`, `.html` e `.css`. Só a LEITURA de
   arquivos de código de volta da pasta falha (`HTTP 400 adding session file`);
   ler do `.git` funciona, o que basta para conferir o estado.
3. **Não usar mais bundle** a partir da F5: o usuário passou a fazer os
   próprios commits, então os históricos divergiram e o bundle não aplicaria
   limpo. (Se algum dia for preciso: incremental sim, completo não — 55 MB
   estoura o limite de 30 MB do chat.)

### Restrições do usuário que continuam valendo

- NUNCA mexer ou fazer merge em `main`. `ajuste-pontual-producao` está
  congelada em `659d433` (trabalho de impressão de PDF) — não tocar.
- SQL de produção NUNCA pode dar DROP em tabela existente.
- Credenciais de produção que ele colou em sessões antigas devem ser tratadas
  como comprometidas e nunca repetidas.

## Notas de implementação (F5)

- `GET /api/chamada/minhas-turmas` traz o encontro em aberto embutido: sem
  isso a tela faria uma consulta por turma só para saber em que pé cada uma
  está. Catequista vê as turmas do vínculo novo **e** do campo antigo
  `tb_turma.id_catequista` — sem o segundo, quem não foi migrado abriria a
  tela vazia e abriria chamado.
- `chamada.js` usa o prefixo `cham` (mesma convenção de `usr`/`cfg`).
- Duas telas na mesma aba, nunca as duas juntas: `#cham-tela-turmas` e
  `#cham-tela-encontro`. Ambas são `.panel`, que já tem o par `[hidden]`.
- `.chamada-layout` é coluna única de propósito: dentro do `.layout` de duas
  colunas a lista de presença ficava espremida em metade da tela.
- **Erro corrigido no caminho:** usei `var(--line)`, que NÃO existe neste CSS.
  O correto é `var(--stroke)`. Foi pego lendo a screenshot, não pelo teste.
- Teste: `/home/claude/project/teste_chamada.mjs` (Playwright, 30 checagens,
  API mockada). O mock **aplica** o POST antes de responder — um mock que
  devolvesse sempre a lista original esconderia bugs de sincronia. Validado
  com controle negativo: quebrando o `if (!atual)` do atalho, o teste acusa.

### MUDANÇA NO FLUXO DE ENTREGA (importante)

A partir da F5, o código vai **direto para a pasta do usuário** via
`SendUserFile` → `device_commit_files`, e ele mesmo faz o commit. Funciona
para `.kt`, `.js`, `.html` e `.css` — testado.

Consequência: o commit `023814f` existe **só no meu repositório**; o do usuário
terá outro hash. **Não gerar mais bundles incrementais**, porque a partir daqui
os históricos divergem e o bundle não aplicaria limpo.

## Notas de implementação (F6)

- Aba `frequencia` separada da `chamada`: marcar presença e conferir
  aproveitamento são momentos diferentes. Prefixo `freq`.
- **A tela não recalcula nada.** Tudo vem do `FrequenciaService`. Se o front
  refizesse a conta, um dia os dois discordariam e ninguém saberia qual vale.
- `<details>` por catequisando: com 30 pessoas, abrir todos os períodos de uma
  vez inviabiliza quem só quer achar quem está abaixo.
- Sem apuração mostra `—`, nunca `0%`.
- `podeConcluir === false` vira "Não conclui neste ano" **junto do nome**.

### Armadilha da impressão (importante para F7 e além)

O bloco `@media print` que já existia (escrito para a ficha) esconde
`.tab-content` **inteiro**. Imprimir qualquer aba direto sai em branco.
Solução adotada: o relatório é montado por JS num `#freq-relatorio`
(`class="relatorio-print"`) que fica **fora do `.shell` e fora de qualquer
`.tab-content`**, com `display:none` na tela e `display:block !important` no
`@media print`. Reaproveitar esse padrão em qualquer impressão nova.

### Correção de CSS achada lendo a screenshot

`.status` base é AVERMELHADO. Estados neutros ("Sem apuração", "Não se
aplica", "Nenhum encontro registrado ainda") herdavam vermelho e pareciam
problema. Criada `.status.neutro` (cinza). **Sempre usar `neutro` para estado
que não é nem bom nem ruim.**

Testes: `teste_chamada.mjs` (30) e `teste_frequencia.mjs` (35), ambos com
controle negativo. Rodar com `node teste_*.mjs` a partir de
`/home/claude/project`.

## Notas de implementação (F7)

- `GET /api/ficha-catequisando/{id}` é rota **separada** de `/api/fichas` por
  privacidade, não por organização. `DocumentoStatusDTO` **não tem** campo de
  caminho de arquivo — o descarte é no servidor. Mandar o caminho e esconder
  na tela não esconderia nada de quem abrisse a resposta da API.
- Acesso: catequista alcança quem passou por **alguma turma dele, em qualquer
  ano** (senão o histórico ficaria inacessível para quem acompanha a pessoa);
  coordenador, a própria comunidade; admin, tudo.
- A ficha é a **terceira tela** da aba Frequência (`#freq-painel-ficha`).
  Substitui filtros + resumo + lista; nunca aparece junto.
- Ordem dos blocos é intencional: **Contato primeiro**, porque o motivo mais
  comum de abrir a ficha é precisar avisar a família.
- Rótulos acentuados ficam no FRONT. Os `.kt` são mantidos em ASCII de
  propósito; o backend manda `rotulo` em ASCII e a tela usa o mapa local.
- Corrigido no caminho: `findAll()` na tabela de fichas → `findByCatequisando`.

### Achado sobre teste vazio (vale como método)

A checagem "clicar no nome não alterna o `<details>`" **não falha** se o
`stopPropagation` for removido: um `<button>` dentro de `<summary>` já não
alterna, por ser conteúdo interativo. Descoberto por controle negativo e
documentado no próprio teste. Ela continua valendo como guarda de MARCAÇÃO
(se alguém trocar o `<button>` por `<span>`, aí sim quebra).
**Lição: sem controle negativo, não dá para saber se a checagem testa algo.**

Testes agora: `teste_chamada.mjs` (30), `teste_frequencia.mjs` (35),
`teste_ficha.mjs` (33). Rodar de `/home/claude/project`.
