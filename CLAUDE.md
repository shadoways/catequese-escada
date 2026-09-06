# Catequese Escada — instruções para o Claude

Sistema de gestão da catequese de uma paróquia: cadastro de catequisandos, turmas,
chamada, frequência, agenda e permissões por papel.

**Kotlin + Spring Boot** (API REST) e **frontend em JavaScript puro**, servido como
recurso estático pelo próprio Spring (`src/main/resources/static/`). Não há
framework de frontend, não há build de JS, e **todo o CSS mora num único
`style.css`**. Banco MariaDB.

---

## Antes de escrever código, leia

| Arquivo | Quando |
|---|---|
| `docs/especificacoes/ESPECIFICACAO-GLOBAL.md` | Sempre. Domínio, papéis, invariantes e convenções. |
| `docs/padroes-visuais/padrao-visual-catequese.md` | Ao mexer em qualquer tela ou CSS. |
| `docs/especificacoes/tela-<nome>.md` | Ao mexer numa tela que já tem especificação. |
| `docs/especificacoes/_MODELO-TELA.md` | Ao criar uma tela nova — preencha antes de codar. |

As skills de `.claude/skills/` entram sozinhas quando o assunto aparece (padrão visual,
tela nova, migração SQL). `.claude/README.md` explica a estrutura.

**Tela nova exige especificação antes do código.** Preencha o modelo, confirme comigo,
e só então implemente. Foi assim com a Agenda, e evitou refazer modelo de dados depois.

---

## Regras que não se quebram

1. **Permissão é de dados, não de tela.** Esconder botão no frontend não impede
   ninguém de chamar a API direto. Toda regra de acesso é verificada no serviço,
   inclusive nos caminhos que a tela já esconderia.
2. **Enum, não texto livre**, em qualquer campo que decida comportamento. Foi texto
   livre em `tb_evento.nivel` que deixou todo evento sem dono.
3. **Nada é apagado de verdade** no domínio: matrícula vira `DESISTENTE`, encontro
   vira `CANCELADO`, usuário vira inativo. O histórico da catequese é o produto.
4. **Toda marcação guarda quem e quando** (`criado_por`, `marcado_em`…). Foi pedido
   explícito da paróquia.
5. **Comentário explica o PORQUÊ**, não o quê. Se a linha só descreve o que o código
   já diz, apague. Comente a decisão, a armadilha e o caso que motivou a regra.
6. **Português** em código, comentário, commit e tela. Sem acento em nome de
   identificador; com acento no texto que o usuário lê.

---

## Armadilhas deste código (todas já morderam)

- **`display` vence o atributo `hidden`.** Toda vez que declarar `display` numa classe,
  escreva o par `[hidden] { display: none }` junto. Já aconteceu em `.grid`, `.tabs`,
  `.panel`, `label` e `.agenda-lista`.
- **Nunca mexa no `display` de um container escondido por `[hidden]`.** Uma regra com
  especificidade igual e posterior faz todas as abas aparecerem de uma vez. Para dar
  respiro entre filhos ali, use `margin` no filho.
- **`<button>` estilizado como link ou card** precisa anular `background`, `color`,
  `border-radius`, `box-shadow` e `padding` da regra base — senão herda a pílula
  terracota com texto branco.
- **Campo em grid não encolhe sozinho** (`min-width: auto`). Um `<select>` se dimensiona
  pela MAIOR opção e estoura a coluna. Precisa de `min-width: 0` no campo **e** na célula.
- **Coordenador sem comunidade**: `EscopoAcessoService` trata nulo como "vê todas" —
  regra de leitura. Em escrita isso é o oposto do desejado; ali a regra fecha.
- **Nome de tipo colide dentro do pacote.** `FormacaoDetalheDTO` já existia como
  "detalhe de uma formação" quando criei outro com o mesmo nome. O compilador escolhe
  um dos dois, e o erro seguinte fala de **parâmetro inexistente** num arquivo que está
  correto. DTO de tela leva o prefixo da tela (`IndicadoresFormacaoDTO`).
- **Botão ao lado de campo se alinha por BAIXO, não pelo centro.** Um `<label>` é rótulo
  + campo empilhados, então é mais alto que um `<button>` sozinho: centralizado, o botão
  flutua na altura do rótulo, alguns pixels acima da caixa que ele aciona. `.row:has(>
  label)` já resolve — o cuidado é não criar barra de filtro fora de `.row`.
  `docs/regressao-alinhamento.py` mede isso em todas as abas.
- **Campo que só existe para ALGUMAS categorias pode ter dado gravado nas outras.**
  Antes de `RegrasDeMovimentacao.temFases` existir, a tela oferecia "1º ano / 2º ano"
  para toda turma, Adultos incluído. Regra que compara esse campo cru (`origem.etapa ==
  destino.etapa`) sem checar se a categoria TEM o campo lê dado velho como se fosse
  percurso diferente — foi o que escondeu a transferência entre duas turmas de Adultos.
  Regra nova sobre campo condicional: escreva a condição (`temFases`) uma vez e reuse-a
  em toda comparação; não repita `== null` cru em cada lugar.
- **Um `<select>` na posição de um filtro É lido como filtro**, mesmo que na verdade
  grave dado. A tela de edição de turma tinha "Turma" e "Comunidade" no topo, no
  mesmo lugar e com a mesma cara da barra de filtro da listagem logo acima — mas
  escolher outra coisa ali gravava uma classificação nova em vez de navegar. Relato:
  *"se eu alterar a seleção ele faz alteração no cadastro... essa barra deveria ser
  uma forma de navegar."* Regra: campo que MUDA dado e campo que NAVEGA não dividem a
  mesma barra visual — nem os mesmos rótulos ("Turma", "Categoria" e o nome de uma
  turma específica são três coisas diferentes; usar a mesma palavra para duas delas
  na mesma tela é o que confunde).
- **`flex-basis: 100%` em container `flex-direction: column`** não é largura, é
  ALTURA. Em `≤600px` o `.row` vira coluna, e um filtro com `flex: 1 1 100%` estourava
  a altura da linha e quebrava para uma SEGUNDA COLUNA, saindo pela direita da tela.
  Ali largura cheia já vem do `align-items: stretch`.
- **Comunidade de uma turma é da TURMA, não de quem estuda nela.** A chamada
  filtrava as turmas do coordenador olhando a comunidade dos matriculados
  (`catequisando.comunidade`), não `turma.idComunidade` — outra pergunta
  inteira ("quem estuda aqui mora onde?" em vez de "esta turma é de qual
  comunidade?"). Escondia uma turma nova, ainda sem ninguém matriculado, da
  comunidade dela; e podia mostrar uma turma de outra comunidade só porque
  alguém de fora foi matriculado nela. Mesma comunidade que decide
  transferência (`RegrasDeMovimentacao`) e edição (Turmas e Inscrições)
  decide quem vê a chamada — regra de recorte por comunidade sempre lê o
  campo da entidade que tem dono, nunca deriva de uma relação à parte.
- **A largura do container de uma aba depende do CONTEÚDO da aba, não só do
  viewport.** O container das abas (`.shell`) encolhe para caber no conteúdo
  da aba ativa. Remover a seção de Eventos da Chamada deixou aquela aba mais
  estreita — e isso, por si só, quebrou o alinhamento do filtro de
  comunidade/turma em ~760px, mesmo sem nenhuma mudança na barra de filtro em
  si. Regra: ao tirar conteúdo (não só ao adicionar), rode
  `docs/regressao-alinhamento.py` de novo — encolher a aba pode apertar uma
  barra que já estava no limite.
- **Comentário de bloco em Kotlin ANINHA** (diferente de Java e C). Escrever uma rota
  com curinga dentro de um KDoc — `/api/indicadores/` seguido de dois asteriscos — abre
  um comentário novo, e o arquivo inteiro vira comentário. O compilador só reclama na
  ÚLTIMA linha (`Unclosed comment`) e num import que está correto, então nenhum dos
  dois erros aponta o problema. Não escreva o curinga em KDoc; use `//` ou cite a rota
  sem ele.

---

## Como verificar

O Gradle **não roda neste sandbox** (o proxy bloqueia `services.gradle.org`). Compile
localmente com `./gradlew compileKotlin` antes de subir.

O frontend tem verificação automatizada em `docs/`:

```bash
# O Gradle não roda aqui; estes três são a conferência de Kotlin possível deste lado:
python3 docs/kt_comentario_check.py           # KDoc que não fecha
python3 docs/kt_nomes_check.py                # tipo declarado duas vezes no mesmo pacote
python3 docs/kt_argumentos_check.py           # argumento nomeado que não existe, obrigatório faltando
python3 docs/regressao.py                     # só uma aba visível por vez
python3 docs/regressao-agenda-dia.py          # lista do dia: abrir, editar, excluir
python3 docs/regressao-agenda-transicoes.py   # trocar de dia limpa o estado anterior
python3 docs/regressao-indicadores.py         # comparação, filtro e o caso sem base
python3 docs/regressao-turmas.py              # listagem só de leitura, fase condicional, abas
python3 docs/regressao-chamada.py             # filtro de comunidade/turma, e eventos recortados junto
python3 docs/regressao-alinhamento.py         # botão ao lado de campo divide a base com ele
```

Os scripts de tela leem de `/tmp/audit/`; copie os estáticos antes:
`mkdir -p /tmp/audit && cp src/main/resources/static/* /tmp/audit/`

Ao mexer em CSS ou layout, verifique também em **1280 / 760 / 400px**: nenhum elemento
pode estourar o pai, e a página não pode rolar na horizontal.

> Verifique o invariante que você pode quebrar sem perceber, não só a propriedade em
> que está pensando. Uma regra de espaçamento derrubou a navegação inteira porque os
> testes mediam espaçamento e ninguém checava "só uma aba visível".

---

## Commits

Mensagem em português, explicando **o problema e a decisão**, não a lista de arquivos.
Se houve armadilha, registre — é o que impede a repetição.

Termine com:

```
Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Xdehjdk8wJPA5tXT72jASk
```

Pode commitar e dar `push` na branch de trabalho sem perguntar a cada vez. O que exige
minha palavra é mudar de branch, mexer em `main`/`develop` ou reescrever histórico.
