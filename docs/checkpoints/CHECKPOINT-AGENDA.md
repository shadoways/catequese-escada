# Checkpoint — Agenda da catequese

**Branch:** `login-e-permissoes`
**Situação:** backend completo, tela da Agenda completa, **tela de Formação pendente**.
**Proposta visual:** https://claude.ai/code/artifact/522851d9-ac9a-457d-a8ad-a759d84923d3

---

## A ideia em uma frase

Todo evento responde a **duas perguntas independentes**: *de quem ele é* (`nivel`, decide permissão) e *o que ele é* (`tipo`, decide como aparece e se tem chamada). Antes disso, `tb_evento.nivel` era `VARCHAR` livre sem vínculo nenhum — todo evento era global.

Na tela: a **cor da tarja** é o nível, o **ícone** é o tipo. Codificar os dois na mesma cor era o que deixava a leitura confusa.

---

## Decisões tomadas (todas confirmadas com o Gabriel)

| # | Decisão | Escolha |
|---|---------|---------|
| 1 | Quem marca presença na formação | Chamada feita por quem coordena, não autodeclaração |
| 2 | Coordenador vê outras comunidades | Sim, sem o lápis — conflito de agenda precisa aparecer |
| 3 | Formação obriga quem | Inscrição nominal (`tb_formacao_inscrito`) |
| 4 | Batismo com inscrição/documentos | Não agora — só a data na agenda |
| 5 | Datas do RICA | **Manuais.** Sem geração automática a partir da Páscoa |

---

## O que está PRONTO

### Backend (Kotlin)

**Novos arquivos**

- `model/Agenda.kt` — enums `NivelEvento`, `TipoEvento`, `SituacaoEvento`, `SituacaoFormacao`
- `model/Formacao.kt` — `Formacao`, `FormacaoInscrito`, `PresencaFormacao`
- `repository/AgendaRepositories.kt`
- `service/AgendaPermissaoService.kt` — **o núcleo da feature**
- `service/AgendaService.kt` — listagem e CRUD com permissão
- `service/FrequenciaFormacaoService.kt` — apuração dos 80%
- `service/FormacaoService.kt` — trilhas, inscrições e chamada
- `controller/AgendaController.kt` — `/api/agenda`
- `controller/FormacaoController.kt` — `/api/formacoes`
- `dto/AgendaDTO.kt`

**Alterados**

- `model/Evento.kt` — reformado (tipo, nível enum, vínculos, situação, auditoria)
- `model/Turma.kt` — `+ idComunidade`
- `service/ChamadaService.kt` — exclui eventos de formação da chamada de catequisando
- `config/SecurityConfig.kt` — libera `/api/agenda/**` para catequista autenticado
- `controller/EventoController.kt` e `service/EventoService.kt` — **escrita removida** (ver "furos fechados")
- `dto/UsuarioDTO.kt`, `service/UsuarioAdminService.kt` — `idComunidade`
- `dto/AdminDTO.kt`, `service/AdminCatequeseService.kt` — comunidade da turma
- `controller/TurmaController.kt` — persiste `idComunidade`

### Migração

`sql/agenda/MIGRACAO_AGENDA.sql` — **ainda não foi rodada.** Segura em produção: só cria estrutura e converte o texto livre de `tb_evento.nivel`. Escrita para MariaDB.

### Frontend

- `agenda.js` — tela nova, completa
- `index.html` — item "Agenda" na barra lateral, card na tela inicial, painel da agenda, campo de comunidade no formulário de usuário
- `script.js` — aba registrada (`TABS_PROTEGIDAS`, trilha, `carregarAgenda`)
- `style.css` — bloco da agenda + `label[hidden]` (ver "bug encontrado")
- `usuarios.js` — campo de comunidade, visível só para `COORDENADOR`
- `admin-catequese.js` — `<select>` de comunidade em cada cartão de turma

---

## O que FALTA

### 1. Tela de Formação (a única lacuna funcional)

O backend está inteiro e testável por API, mas **não existe tela**. Falta:

- Listar trilhas (`GET /api/formacoes`) com o resumo que o DTO já devolve: encontros realizados, inscritos em dia, abaixo do mínimo
- Criar/editar trilha (`POST`/`PUT /api/formacoes`) — nome, nível, ano, percentual mínimo
- Inscrever/desinscrever catequista (`POST`/`DELETE /api/formacoes/{id}/inscritos/{idCatequista}`)
- Chamada do encontro (`GET`/`POST /api/formacoes/encontros/{idEvento}/chamada`)
- Detalhe com a lista de inscritos e o percentual de cada um (`GET /api/formacoes/{id}`)

O mockup dessa tela está na proposta (seção "Formação"). A mecânica da chamada é a mesma de `chamada.js`, trocando a lista de catequisandos pela de catequistas inscritos.

**Sem essa tela, não dá para criar uma formação** — e sem formação criada, o campo "Formação" do formulário de evento fica vazio, então eventos de tipo `FORMACAO` não podem ser cadastrados ainda.

### 2. Compilação não verificada

**O Gradle não roda neste sandbox** (o proxy bloqueia `services.gradle.org`, então o wrapper não baixa a distribuição). O Kotlin foi revisado à mão, mas **não foi compilado**. Rodar `./gradlew compileKotlin` localmente antes de subir.

Pontos que eu conferiria primeiro se der erro de compilação:

- `NivelEvento.entries` / `TipoEvento.entries` — exige Kotlin 1.9+ (o projeto está em 1.9.25, deve passar)
- Construtor de `AdminCatequeseService` ganhou `comunidadeRepository` — conferir se algum teste instancia essa classe na mão
- `@JvmOverloads` em `AgendaService.paraDTO`

### 3. Passo humano depois da migração

A migração cria as colunas, mas os dados são preenchidos por tela:

1. **Usuários** → definir a comunidade de cada coordenador
2. **Turmas e matrículas** → definir a comunidade de cada turma

> **Coordenador sem comunidade não consegue cadastrar evento de comunidade** — isso é proposital (ver abaixo), mas vai parecer bug se ninguém preencher.

### 4. Testes automatizados

Não escrevi teste de unidade para `AgendaPermissaoService`. É a parte que mais merece: são 7 linhas de matriz × 3 papéis. Fica como próximo passo natural.

---

## Furos que foram fechados no caminho

### `/api/eventos` ignorava toda a permissão

O CRUD antigo gravava direto pelo repositório. Um coordenador de comunidade podia criar um evento **diocesano** por lá e contornar a regra inteira. Como nenhuma tela usava essa rota (o front usa `/api/agenda` e `/api/chamada/eventos`), removi POST/PUT/DELETE em vez de duplicar a checagem em dois lugares.

### `SecurityConfig` bloqueava o catequista

A regra genérica de escrita exige `COORDENADOR` para todo `POST/PUT/DELETE /api/**`. Sem uma linha explícita, o catequista **não conseguiria criar evento da própria turma** — exatamente o que foi pedido. Liberado `/api/agenda/**` para autenticado; quem limita de verdade é o `AgendaPermissaoService`, evento por evento.

### Coordenador sem comunidade

`EscopoAcessoService.comunidadesPermitidas()` trata comunidade nula como **"vê todas"**. Ali é regra de *leitura*, e abrir demais só mostra dado a mais. Na agenda é regra de *escrita*: replicar isso daria a qualquer coordenador sem vínculo o poder de alterar evento de qualquer comunidade — o oposto do pedido. O `AgendaPermissaoService` **fecha** nesse caso, e o desvio está comentado no código.

> Vale alinhar os dois um dia. Não mexi no `EscopoAcessoService` porque outras telas dependem do comportamento atual.

### Promoção de nível na edição

`AgendaService.atualizar` checa a permissão **antes e depois** de aplicar o formulário. Sem o segundo teste, quem pode editar um evento da própria turma poderia "promovê-lo" a paroquial e passar a mandar num evento que nunca foi dele.

### Apaga-e-reinsere na chamada

Refazer a chamada apaga as marcações e insere as novas na mesma transação. Sem `flush()` explícito o Hibernate pode mandar os inserts antes dos deletes, deixando duas marcações para a mesma pessoa — e a frequência contaria em dobro. Tem `deleteAll` + `flush` no `FormacaoService`, e `UNIQUE (id_evento, id_catequista)` como rede de segurança.

### N+1 na agenda

A frequência é apurada **uma vez por formação**, não uma por encontro. Uma trilha de 8 encontros geraria 8 apurações idênticas.

---

## Bug encontrado pelo teste de tela

O campo "Comunidade" continuava visível mesmo com `hidden` aplicado. Causa: a regra base `label { display: grid }` **vence o atributo `hidden`** — a mesma armadilha já corrigida antes em `.grid`, `.tabs`, `.panel` e no form das telas de senha.

Corrigido com `label[hidden] { display: none; }` em `style.css`. Vale saber que isso vai reaparecer em qualquer campo condicional escondido por JS.

---

## Regras de frequência da formação

Batem com as da frequência de turma, de propósito:

1. **Só encontro `REALIZADO` entra na conta.** Encontro previsto do resto do ano não pode contar como falta, senão todo mundo começa o ano reprovado.
2. **Falta justificada sai da conta**, não conta contra.
3. **Encontro realizado sem marcação conta como falta.** Se contasse como "não apurado", bastaria não fazer a chamada para todo mundo ficar com 100%.
4. **Sem encontro realizado, o percentual é `null`, não 0.** São coisas diferentes; 0% numa formação que não começou assustaria sem motivo.
5. **`percentual_minimo` é coluna, não constante.** Divinópolis exige 80%, Santo André 75% — fixar no código quebraria em qualquer diocese com outro número.

---

## Referências litúrgicas usadas

- Duas entregas oficiais no RICA: **Símbolo** e **Oração do Senhor**. A entrega da Bíblia/Evangelhos é gesto opcional dentro do rito de entrada, não rito próprio. As entregas da catequese infantil brasileira (Mandamentos, Bem-Aventuranças, terço) são adaptação pastoral, não norma.
- Três escrutínios, no 3º, 4º e 5º Domingos da Quaresma. Eleição no 1º. Sacramentos na Vigília Pascal.
- O rótulo do primeiro rito varia por diocese ("Entrada no catecumenato", "Acolhida", "Admissão") — por isso é texto livre, não enum.
- Batismo não tem fluxo único: criança até 7 anos segue o Ritual do Batismo de Crianças; adulto no catecumenato recebe os três sacramentos juntos; quem já foi batizado validamente em outra igreja **não se rebatiza** (recepção na plena comunhão).

---

## Pendência antiga, ainda aberta

Os commits desta branch **não foram enviados**. O `git push` falha neste sandbox com:

```
remote: access denied by the git proxy: shadoways/catequese-escada is not in
this session's authorized repository set
```

É trava do ambiente, não de permissão do repositório. Ou se adiciona o repo às fontes autorizadas da sessão, ou o push sai do terminal local — os arquivos já estão todos gravados em `~/workspace/catequese-escada`.
