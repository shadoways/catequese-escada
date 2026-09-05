# Tela: Turmas e inscrições

**Situação:** implementada
**Aba:** `data-tab="admin"` · **Arquivo:** `admin-catequese.js`

## 1. Para que serve

**Cuidar do percurso do catequisando**: em que turma ele está, para onde pode ir, e o
que acontece com ele na virada do ano.

A tela estava confusa porque misturava três coisas sem dizer qual era qual:
classificação de turma, inscrição, e correção de chamada. Agora ela responde uma
pergunta só — *onde está cada pessoa, e para onde ela pode ir?*

> **Vocabulário: "inscrição", não "matrícula".** Matrícula tem ar de escola, e catequese
> não tem vínculo escolar. Decisão do Gabriel.
>
> **Débito conhecido e aceito:** a troca foi feita **só no texto da tela**. No código e
> no banco continua `Matricula` / `tb_matricula` / `/api/admin/matriculas`. Foi escolha
> dele, com o motivo certo: o Gradle não roda no sandbox, e um rename de ~30 arquivos
> mais a tabela, junto com regras de negócio novas, produziria um commit onde uma falha
> de compilação seria difícil de isolar. Fica registrado aqui para não parecer descuido
> — a especificação global manda usar um vocabulário só, e hoje há dois.

## 2. Quem usa

Só o **coordenador paroquial** (a aba é `somente-admin`, e as rotas `/api/admin/**` já
exigem o papel).

## 3. Duas telas: listar e editar

> **Listar responde *onde está cada turma*. Editar responde *o que muda nesta turma*.**

Eram a mesma tela, e por isso ela confundia: a lista trazia três `<select>` dentro de
cada cartão, então quem só queria **conferir** onde uma turma estava tinha campos
editáveis na frente, e o risco de mexer na turma errada era o preço de olhar.

### 3.1 A listagem

**Nada aparece antes do "Consultar".** A tela abria despejando todas as turmas do ano —
quem entrou atrás de uma comunidade precisava descartar o resto no olho. Agora escolhe o
recorte e pede.

São **três** filtros: ano, comunidade e turma. O ano ficava na linha do título, ao lado
do "Encerrar ano", parecendo pertencer àquele botão; ele é filtro e mora com os outros.

> **O ano filtrava só a contagem, não a lista** — a lista vinha de um `findAll()`. Quem
> pedia 2026 recebia junto as turmas de 2025, todas com "0 inscritos", sob um título que
> dizia 2026. Ler aquilo como "a turma esvaziou" era o mais natural do mundo. Corrigido
> em `AdminCatequeseService.turmas`.
>
> **Turma sem ano aparece em qualquer ano**, de propósito: é cadastro antigo, esta é a
> única tela onde ela pode ser classificada, e filtrar por igualdade a tornaria
> inalcançável para sempre. O aviso do topo conta quantas são.

Mudar o filtro **não** consulta: quem monta um recorte mexe em mais de um campo, e
responder no meio disso mostra um resultado que ninguém pediu — às vezes vazio, parecendo
erro. Trocar o ano também derruba a turma escolhida: ela é de outro ano, e um id fora do
recorte devolveria lista vazia sem explicação.

> As turmas do ano são carregadas em silêncio ao entrar na aba, porque o próprio filtro
> de turma e os destinos de transferência precisam da lista. O que o "Consultar" governa
> é o que **aparece**.

**Quatro colunas, nenhuma edição: Turma, Fase, Comunidade, Inscritos.** Antes eram
"Categoria", "Ano do percurso" e "Comunidade", cada uma um campo. A coluna Turma traz o
nome da turma com a categoria abaixo; sem fase, um travessão. Inscritos sai à direita,
com algarismo de largura fixa — contagem se lê comparando ordem de grandeza, e alinhada
à esquerda a coluna vira um serrilhado que obriga a ler dígito por dígito. A linha
inteira é clicável (e alcançável pelo teclado) e leva à edição.

O aviso de turma sem classificação continua: sem categoria a frequência não é apurada, e
essas turmas ganham uma marca na borda.

### 3.2 A tela de edição

Chega-se nela clicando numa linha. Ali ficam a navegação para outra turma, a
classificação da turma atual (Categoria, Fase), e duas abas: **Inscrições** e
**Transferências**.

> **Falha real corrigida: navegação e classificação estavam misturadas na mesma
> barra.** No topo da tela de edição havia dois selects, "Turma" e "Comunidade",
> na mesma posição e com a mesma cara do filtro da listagem — mas escolher outra
> coisa ali não navegava, **gravava uma classificação nova na turma**. Quem só
> queria olhar outra turma acabava mudando o cadastro desta. Relatado assim:
> *"se eu alterar a seleção ele faz alteração no cadastro... essa alteração que
> ele faz não tem sentido, uma turma mudar de comunidade."*
>
> Agora são duas coisas visualmente separadas:
>
> 1. **Navegar** (topo, logo abaixo do título): "Comunidade" filtra, "Ir para a
>    turma" abre outra turma — **nunca grava nada**. Abre já mostrando a
>    comunidade e a turma atuais ("onde estou"); escolher uma comunidade
>    diferente só encolhe a lista de turmas para pular, e escolher uma turma
>    ali é o mesmo que clicar na linha dela na listagem. Voltar a abrir
>    qualquer turma reseta o filtro para a comunidade dela — o filtro vale só
>    para a visita atual, não é um estado que persegue de tela em tela.
> 2. **Classificar** (abaixo do aviso sobre frequência): só **Categoria** e
>    **Fase**. Continuam gravando sozinhas na mudança do select — ver o
>    parágrafo seguinte. **Comunidade saiu daqui**: mudar a comunidade DONA da
>    turma é uma decisão maior (destrava o balde nos Indicadores, muda quem
>    administra os eventos dela na agenda) do que classificar uma categoria, e
>    não é algo a decidir de passagem enquanto se navega. Por ora ela aparece
>    só como informação ("Comunidade desta turma: Matriz"), sem select. Dar a
>    ela uma tela própria — com a decisão que isso implica — fica para depois;
>    é o novo item da §7.

**A classificação grava na mudança do select, sem botão.** O "Salvar" que existia era
lido como "salvar o filtro" — e não era: gravava a **classificação**, que é o que
destrava a comunidade nos Indicadores (o balde "Sem comunidade definida" vem daí).
Apagar sem mais nada tiraria a única forma de classificar turma. Trocar a categoria ou
a fase manda o `PUT` com a **comunidade atual preservada** — como não há mais select
para ela aqui, deixar o campo vazio apagaria a comunidade da turma a cada troca de
categoria, mesmo quando ninguém tocou nisso.

**"Inscrever" saiu.** Era um `<select>` com todos os catequisandos da paróquia mais uma
data — e inscrição não é isso: exige nascimento, responsável, sacramentos e a conferência
de idade do percurso. Quem entrava por esse atalho criava um vínculo sem nada disso. A
porta é a tela de cadastro, que faz as perguntas. `POST /api/admin/matriculas` continua
existindo e é usado por lá.

**Cada catequisando tem um botão só: Salvar.** "Salvar" e "Transferir" lado a lado eram
duas ações de peso muito diferente disputando o mesmo clique — uma corrige a situação de
quem ficou, a outra tira a pessoa da turma. Transferir virou aba, e lá cada linha tem seu
botão. Quem já saiu não aparece nela: a inscrição dele foi encerrada e a ativa está no
destino.

**"Corrigir chamada" saiu do cartão**, como pedido. O painel e o código continuam
inteiros, mas **hoje não há como chegar até ele** — falta decidir onde ele mora. É o
primeiro item da §7.

**"Matrículas" virou "Inscrições"**, aqui e na aba de Indicadores.

## 4. Regras de movimentação

Vivem em `RegrasDeMovimentacao`, um objeto **puro** — sem Spring, sem repositório. Regra
de percurso muda depois de reunião de coordenação; escondida dentro de um serviço com
dez dependências, ninguém consegue conferir nem testar.

### 4.0 Quem tem fase

> **Só Eucaristia e Crisma têm fase.** Pré-catequese, perseverança, adultos e
> catecumenato não se dividem: a pessoa segue no mesmo percurso.

`CategoriaTurma.anosPrevistos` **não** serve de critério — Adultos também dura dois anos,
e durar dois anos não é dividir-se em duas fases. Era esse atalho que oferecia "segunda
fase" numa turma que não tem fase nenhuma, e o valor escolhido ficava gravado.

A regra mora em `RegrasDeMovimentacao.temFases`, e tudo que mostra ou pergunta fase passa
por ela: o combo da edição (que **some** nas categorias sem fase, em vez de ficar
desabilitado — campo desabilitado ainda é uma pergunta na tela), a coluna da listagem, e
a progressão do encerramento do ano.

**Classificar uma turma para uma categoria sem fase apaga a fase**, no servidor. Sem
isso, uma turma que era Eucaristia 2 e virou Adultos guardaria `etapa = 2` — um valor que
nenhuma tela mostra e que ninguém consegue corrigir depois.

O vocabulário também mudou: **"primeira fase" e "segunda fase"**, não "1º ano" e "2º
ano". Percurso de catequese não é série escolar.

> **Falha real corrigida:** turma de Adultos parou de aparecer como destino de
> transferência para outra turma de Adultos. Não era regra de negócio — era dado
> velho: antes de `temFases` existir, a tela oferecia "1º ano / 2º ano" para
> **toda** categoria, Adultos incluído. Duas turmas de Adultos com fase gravada
> diferente (uma com 1, outra com 2) passaram a parecer **percursos diferentes**
> para a regra de transferência, que comparava a fase sem checar se a categoria
> tinha fase. `RegrasDeMovimentacao.mesmoPercurso` agora só compara fase onde ela
> existe; `sql/movimentacao/LIMPA_FASE_SEM_CATEGORIA.sql` limpa o que já está
> gravado errado. `docs/regressao-turmas.py` reproduz o caso com fase espúria
> divergente nas duas turmas de Adultos.

### 4.1 A regra de fundo

> **Transferência é mudança de lugar, não de percurso.**

Trocar de comunidade sem trocar de fase é mudança de endereço. Trocar de fase é
**progressão**, e progressão acontece no encerramento do ano, com a coordenação
revisando — não no meio do ano, um a um.

1. **Mesmo percurso, mesma fase, outra comunidade.** É o caso normal.
2. **Mesma fase e mesma comunidade é recusado**: não é transferência, é troca de turma.
3. **Fase diferente é recusado** — Eucaristia 1 não vai para Eucaristia 2 por
   transferência.
4. **Categoria diferente é recusada**, com duas exceções (4.2 e 4.3).
5. **Turma sem categoria não recebe ninguém**: sem ela a regra não tem como saber se o
   percurso bate.

### 4.2 Os dois percursos que preparam para outro

Pré-catequese e perseverança não têm etapas próprias — preparam para o passo seguinte.
Por isso a saída delas **é** mudança de categoria:

- **Pré-catequese → Eucaristia, 1ª fase.** Só isso.
- **Perseverança → Crisma, 1ª fase.** Só isso.

### 4.3 O catecúmeno

Catecúmeno é quem não tem batismo. Ele **não vai para a catequese de adultos** enquanto
não cumprir os quatro ritos: pré-catecumenato, catecumenato, purificação e iluminação, e
mistagogia. A tela diz **quais faltam**.

Cumpridos os quatro, o caminho abre. *(Esta linha existe porque sem ela o caso caía na
regra geral e era barrado por "categoria diferente" — quem completou o itinerário ficava
preso nele.)*

Catecumenato segue a regra de idade de adultos: é percurso de adulto.

### 4.4 Idade mínima

| Percurso | Idade | Tolerância |
|---|---|---|
| Eucaristia, 1ª fase | 9 anos | 3 meses |
| Crisma, 1ª fase | 13 anos | 3 meses |
| Adultos e Catecumenato | 18 anos | 6 meses |

**A tolerância conta a partir da data da inscrição** (decisão do Gabriel): quem completa
a idade dentro dos meses seguintes entra. Segurar uma criança um ano inteiro por causa
de algumas semanas seria desproporcional. Adulto tem seis meses porque o percurso é de
dois anos e a maioridade chega antes do fim do primeiro.

**Só a primeira fase tem porta de idade.** Quem está na segunda entrou pela primeira, e
cobrar de novo barraria quem tem aniversário tardio no meio do próprio percurso.

**Sem data de nascimento a regra não barra.** Cadastro antigo costuma vir sem ela, e
recusar quem tem a idade certa por causa de um campo em branco seria pior do que
aceitar. A migração traz a consulta que lista esses casos.

### 4.5 Saída para outra paróquia

Destino alternativo, **exclusivo** da transferência interna: a tela pede o nome da
paróquia, a inscrição atual é encerrada como `TRANSFERIDO` e **não se cria inscrição
nova** — a dela passa a ser de outro lugar.

O nome vai para `tb_matricula.paroquia_destino`. Sem ele, "transferido" não responde a
pergunta que sempre vem depois — *para onde?* — e a secretaria acaba anotando em papel.

### 4.6 A virada do ano

**A fase muda sozinha, mas passando pela prévia do "Encerrar ano"** — decisão do
Gabriel. A prévia agora diz, por pessoa, para qual fase ela vai (Eucaristia 1 → 2,
Crisma 1 → 2) ou que o percurso termina ali. Ninguém sobe de fase sem o coordenador ver,
e quem não fechou a frequência aparece com a proposta de `NAO_CONCLUIDO` ao lado.

A progressão **não atravessa categoria**: ir da pré-catequese para a Eucaristia é uma
decisão com porta de idade, feita nesta tela, e não algo que o sistema faz na virada.

## 5. Dados

| Mudança | Onde |
|---|---|
| `tb_matricula.paroquia_destino VARCHAR(255) NULL` | `sql/movimentacao/MIGRACAO_MOVIMENTACAO.sql` |

**Só isso.** As regras de idade, fase e catecumenato leem o que já existe:
`tb_catequisando.data_nascimento`, `tb_turma.categoria`, `tb_turma.etapa` e
`tb_etapa_catecumeno.data_fim`.

`POST /api/admin/matriculas/{id}/transferir` passa a aceitar `paroquiaDestino` como
alternativa a `idTurmaDestino` — os dois juntos são recusados.

## 6. Onde a regra é aplicada

**No serviço, sempre** (invariante 1). A tela **poda** a lista de destinos pelo mesmo
critério, mas isso é conforto, não segurança: oferecer tudo e deixar o servidor recusar
seria tecnicamente correto e ruim de usar — a pessoa escolhe, confirma, e só então
descobre que aquele destino nunca foi possível.

Quando o servidor recusa, a tela **repassa a frase dele**, que nomeia o motivo (fase
diferente, idade, catecumenato incompleto). Inventar outra mensagem aqui criaria duas
explicações para o mesmo "não".

## 7. O que fica de fora

- **Onde mora "Corrigir chamada".** Saiu do cartão a seu pedido e ficou sem entrada.
  Preciso da sua decisão.
- **Criar a inscrição do ano seguinte automaticamente.** A prévia já diz para qual fase
  cada um vai, mas criar a inscrição exige que a turma do ano que vem exista. Vale
  decidir se o encerramento passa a criá-las, ou se continua sendo trabalho da
  inscrição.
- **O rename no código e no banco** — ver a nota da §1.
- **Idade máxima.** Nada impede um adulto numa turma infantil; a regra pedida era só de
  mínimo.
- **Uma tela própria para mudar a comunidade dona da turma.** Foi tirada da edição
  (§3.2) por ser uma decisão maior do que classificar categoria/fase, e sem tela dela
  hoje isso só se faz direto no banco. Turma sem comunidade continua existindo — o
  aviso "Sem comunidade definida" nos Indicadores é para isso.

## 8. Como verificar

Automatizado em `docs/regressao-turmas.py` (Playwright, sem servidor):

- [x] Nada aparece antes do "Consultar"; mudar o filtro sozinho não consulta
- [x] O filtro de comunidade encolhe a lista de turmas, e limpa a turma escolhida
- [x] O ano filtra a **lista**: turma de 2025 não aparece na consulta de 2026
- [x] Turma sem ano aparece em qualquer ano, e o aviso diz por quê
- [x] Trocar o ano limpa a lista e espera o "Consultar"
- [x] A listagem não tem `<select>`, `<button>` nem `<input>` nenhum
- [x] As colunas são **Turma, Fase, Comunidade, Inscritos**; sem fase, travessão
- [x] A contagem de inscritos sai à direita
- [x] O botão da barra de filtro divide a base com os campos (e não quebra sozinho)
- [x] Clicar na linha (mouse ou teclado) abre a edição daquela turma
- [x] Eucaristia e Crisma mostram o campo de fase; as outras quatro, não
- [x] Trocar para uma categoria sem fase grava `etapa` **nula**
- [x] Não há "Inscrever" nem campo de escolher catequisando
- [x] Cada inscrição tem só "Salvar"; "Transferir" só existe na outra aba
- [x] Quem já foi transferido não aparece na aba de transferências, e diz para onde foi
- [x] Voltar para a listagem mostra a classificação recém-alterada
- [x] Em 400px nada estoura o pai e a página não rola na horizontal
- [x] O select de classificação da categoria se chama "Categoria", não "Turma"
- [x] O select de navegação abre já mostrando a turma e a comunidade atuais
- [x] Mudar o filtro de navegação **não** chama o PUT de classificação
- [x] Mudar o filtro de navegação encolhe o select de turma para pular
- [x] Escolher uma turma no select de navegação abre ela, e reseta o filtro para a
      comunidade dela
- [x] A comunidade da turma aparece como texto, sem `<select>`
- [x] Trocar só a categoria/fase preserva a comunidade da turma no `PUT`
      (sem isso, o campo vazio apagaria a comunidade a cada troca)

Do servidor (o Gradle não roda no sandbox):

- [ ] Mudar categoria/fase/comunidade grava sozinho e confirma na tela
- [ ] Eucaristia 1 → Eucaristia 2 é **recusado**, com o motivo da fase
- [ ] Eucaristia 1 → Crisma 1 é **recusado**, com o motivo do percurso
- [ ] Eucaristia 1 (comunidade A) → Eucaristia 1 (comunidade B) é **aceito**
- [ ] Mesma fase e mesma comunidade é **recusado**
- [ ] Pré-catequese só oferece Eucaristia 1; com menos de 9 anos (fora dos 3 meses) é
      recusado com a data em que a pessoa completa a idade
- [ ] Perseverança só oferece Crisma 1, com a porta dos 13
- [ ] Catecúmeno com etapa em aberto é recusado, e a mensagem **diz quais faltam**
- [ ] Catecúmeno com os quatro ritos concluídos é aceito em Adultos
- [ ] "Outra paróquia" pede o nome, encerra a inscrição e **não cria** outra
- [ ] Turma sem categoria não aparece como destino
- [ ] A prévia do encerramento mostra a fase seguinte de quem continua
- [ ] Classificar uma turma como Adultos apaga a fase que ela tinha
- [x] Duas turmas de Adultos com fase espúria DIFERENTE ainda se enxergam como
      mesmo percurso, e a transferência entre elas aparece (regressao-turmas.py)

**Não verificado daqui:** tudo em §4 é regra de servidor, e o Gradle não roda no
sandbox. `RegrasDeMovimentacao` é objeto puro — é o melhor candidato a teste de unidade
do projeto, e a lista acima é a bateria pronta.
