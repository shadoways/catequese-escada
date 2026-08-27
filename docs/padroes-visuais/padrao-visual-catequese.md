# Padrão visual — Catequese Escada

Guia de referência para manter as telas consistentes: mesma paleta, mesmo espaçamento, mesma tipografia. A tela de **Turmas** (admin) é o padrão-ouro — foi a referência usada para alinhar as demais.

Todo o app usa um único `style.css` com variáveis CSS (`:root`) como tokens de design. Qualquer tela nova deve reaproveitar essas variáveis em vez de escrever cor, raio ou sombra "no olho".

## 1. Paleta de cores

```css
:root {
  --ink: #1b1a16;        /* texto principal */
  --muted: #5b5a52;      /* texto secundário, legendas */
  --accent: #c05f3c;     /* terracota — cor de ação/marca, botões primários */
  --accent-2: #2f6f7e;   /* teal — links, ações secundárias, destaque de dado */
  --paper: #f5efe7;      /* fundo dos painéis/cards */
  --paper-2: #f9f4ee;    /* fundo do header e áreas mais claras */
  --highlight: #f1c97a;  /* realce amarelado */
  --stroke: #d8cdbc;     /* bordas sutis */
  --shadow: 0 18px 55px rgba(33, 28, 20, 0.18);
  --radius: 18px;

  /* Texto sobre fundo tintado (.status warning/error e afins) */
  --warning-text: #8b6914;
  --warning-border: #d8ab52;
  --danger-text: #9f2c0b;
}
```

Regra prática: se uma cor de aviso, erro ou destaque textual vai aparecer em mais de um lugar, ela vira uma variável em `:root` — nunca um hex repetido tela a tela. Foi assim que três tons (`#8b6914`, `#9f2c0b`, `#d8ab52`) espalhados pelo CSS acabavam divergindo sutilmente entre telas; agora têm nome único.

**Não usar cor de alerta "crua".** Antes existia um vermelho puro (`#e03`) usado só no destaque de campo inválido, sem nenhuma relação com o `--danger-text` usado em outros lugares — por isso o app tinha dois vermelhos de erro diferentes. Qualquer estado de erro/perigo novo deve derivar de `--danger-text`, nunca de um hex novo.

### Badges de status (`.status`)

```css
.status.ok      { background: rgba(47, 111, 126, 0.14); color: var(--accent-2); }
.status.warning { background: rgba(241, 201, 122, 0.3);  color: var(--warning-text); }
.status.error   { background: rgba(192, 95, 60, 0.2);    color: var(--danger-text); }
.status.neutro  { background: rgba(91, 90, 82, 0.10);    color: var(--muted); }
```

Sempre em par: fundo tintado em baixa opacidade + texto na cor "escurecida" correspondente. Nunca fundo sólido saturado com texto branco — é isso que faz um badge parecer mais grave do que é (foi o caso do botão de nome do catequisando, ver seção 4).

## 2. Tipografia

- Fonte única: `Arial, sans-serif` em todo o app — sem mistura de família.
- `h1`: `clamp(2rem, 3vw, 2.8rem)`, `font-weight: 600`, usado só no cabeçalho da página.
- `h2`: `1.5rem`, `margin: 0` — título de painel/seção.
- Texto secundário/legendas: classe `.muted`, `color: var(--muted)`, `font-size: 0.9rem`.
- Labels de formulário: `font-size: 0.95rem`, `color: var(--muted)`.

Não criar um novo tamanho de fonte "no olho" para um título — reusar `h2` (seção) ou `.muted` (legenda). Se nenhum dos padrões existentes servir, é sinal de que o layout deveria ser repensado, não que precisa de mais uma variação.

## 3. Espaçamento, raio e sombra

- **Painel/card principal** (`.panel`): `padding: 22px 24px`, `gap: 16px` (grid), `border-radius: var(--radius)`, `box-shadow: var(--shadow)`. Esse é o container-base de qualquer tela — a tela de Turmas segue exatamente isso.
- **Espaçamento entre painéis**: `gap: 24px` no `.shell`/`.layout`.
- **Escala de raio usada hoje** (documentando para não crescer mais): `8px` e `10px` em inputs pequenos/badges, `12px`–`14px` em cards internos e inputs, `18px` (`--radius`) em painéis, `999px` em pílulas (botões, chips). Ao criar um componente novo, escolher o raio da família mais próxima em vez de inventar um valor — por exemplo, um card dentro de um painel deve usar `12px`, não `13px` ou `15px`.
- **Sombra**: `var(--shadow)` para painéis; elementos internos (botão, card pequeno) não devem ter sombra própria a menos que sejam clicáveis e precisem indicar elevação — nesse caso, uma sombra mais leve e na cor do próprio elemento (ver `button` base: `0 10px 22px rgba(192, 95, 60, 0.25)`), nunca a sombra genérica de painel.


## 3b. Campos que não podem estourar a coluna

Esta é a armadilha que mais estragou tela neste projeto, e ela não é óbvia olhando o CSS.

Todo item de grid ou flex nasce com `min-width: auto`, ou seja: **ele se recusa a encolher abaixo do próprio conteúdo**. Num `<select>`, o "conteúdo" é a **maior opção da lista** — então basta alguém cadastrar uma formação de nome comprido para o campo esticar para fora da célula e passar por cima do campo vizinho. `input[type="date"]` tem o mesmo problema, por causa da largura intrínseca do seletor de calendário.

A correção precisa estar nos **dois** lados — no campo e na célula que o contém:

```css
input, textarea, select {
  min-width: 0;      /* deixa encolher */
  width: 100%;       /* ocupa a coluna em vez de se medir pelo conteúdo */
  max-width: 100%;   /* cinto de segurança contra largura inline */
}

select { text-overflow: ellipsis; }   /* opção longa some com reticências */

.grid > * { min-width: 0; }
label { min-width: 0; }
```

Caixas de seleção ficam de fora: elas têm tamanho próprio, e `width: 100%` as esticaria pela linha inteira.

```css
input[type="checkbox"], input[type="radio"] { width: auto; }
```

**Como verificar:** abra a tela com o texto mais longo que o banco pode conter (não com "Teste 1") e confira se a página rola na horizontal. Se rolar, algum campo está estourando. Um jeito rápido de achar o culpado no console:

```js
document.querySelectorAll('*').forEach(el => {
  const r = el.getBoundingClientRect(), p = el.parentElement?.getBoundingClientRect();
  if (p && r.width && r.right > p.right + 1.5) console.log(el);
});
```

## 3c. Todo container que empilha precisa de gap próprio

`.tab-content` era um `<div>` sem `display` nem `gap`. Como quase toda aba tem mais de um `.panel` dentro (Cadastro tem 4, Usuários 3), os painéis empilhavam **encostados**. As abas de Chamada e Frequência escapavam por acidente: elas carregam `.chamada-layout` junto, que já define o próprio `gap`.

A regra: **se um elemento pode conter mais de um filho empilhado, ele declara `display: grid` e `gap`.** Não conte com margem dos filhos — margem colapsa, `gap` não.

O espaçamento entre painéis é **24px em toda a aplicação**, esteja o painel dentro de uma aba ou solto no `.shell`. O valor mora num token só:

```css
:root { --gap-paineis: 24px; }
```

Usado por `.shell`, `.layout` e `.chamada-layout` como `gap`, e por `.tab-content` como `margin-top` do painel (ver abaixo por que a diferença importa). Se um dia ficar apertado ou folgado demais, muda num lugar e vale para tudo.

### NUNCA mexa no `display` de um container que é escondido por `[hidden]`

Esta regra nasceu de um bug que eu mesmo causei e que derrubou a navegação inteira.

Tentando garantir o respiro em qualquer container futuro, escrevi:

```css
/* NÃO FAÇA ISSO */
:is(div, section, main, form, aside):has(> .panel + .panel) {
  display: grid;
  gap: var(--gap-paineis);
}
```

O que aconteceu: quem esconde a aba inativa é `.tab-content[hidden] { display: none }`, com especificidade (0,1,1). A regra acima também dá (0,1,1) — `:has()` herda a especificidade do seu argumento, `.panel + .panel`. **Empate.** E como ela vinha depois no arquivo, ganhou: `display: grid` sobrepôs `display: none` e **todas as abas com dois ou mais painéis apareceram ao mesmo tempo**, empilhadas numa página só. As abas de um painel só continuaram escondidas — o que tornou o sintoma ainda mais confuso.

A regra que fica:

- Para dar respiro entre irmãos dentro de um container que pode ser escondido, use **`margin` no filho**, não `display` + `gap` no pai. Margem não tem como reativar um container oculto.
- Se precisar mesmo mexer em `display`, escreva a regra **antes** de `[hidden]` no arquivo e confira a especificidade dos dois.

A solução em uso hoje:

```css
.tab-content:not(.chamada-layout) > .panel + .panel {
  margin-top: var(--gap-paineis);
}
```

O `:not(.chamada-layout)` existe porque Chamada, Frequência e Admin carregam aquela classe junto, e ela já define `gap: 24px` — sem a exceção ficariam com 24 de gap + 24 de margem.

### O teste que faltava

O bug passou porque minha bateria media *estouro* e *espaçamento*, mas nunca verificou o invariante mais básico da tela: **só uma aba visível por vez**. Verificar a propriedade em que se está pensando não basta; é preciso verificar também a que se pode quebrar sem perceber.

```js
// Para cada aba clicada, isto tem que ser verdade:
[...document.querySelectorAll('.tab-content')]
  .filter(t => getComputedStyle(t).display !== 'none')
  .length === 1
```

## 3d. Piso de espaçamento no celular

A cascata de media queries vinha encolhendo os espaços a cada quebra: 14px → 12px → 8px → 6px. No fim, dois botões de largura cheia ficavam a 6px um do outro — e um deles era "Excluir".

**Espaço vertical não é escasso numa tela pequena; o que falta é largura.** Espremer o vertical não ganha nada e amontoa os alvos de toque, que é justamente onde o dedo erra. O piso adotado:

| | desktop | ≤768px | ≤600px | ≤480px |
|---|---|---|---|---|
| `.panel` padding | 22/24 | 18/16 | 16/14 | 14/12 |
| `.panel` gap | 16 | 14 | 12 | 12 |
| `.grid` gap | 14/16 | 12 | 12 | 12 |
| `.row` gap | 12 | 12 | 10 | 10 |
| `label` gap | 6 | 6 | 6 | 5 |

**Nada abaixo de 10px entre dois controles clicáveis.** Gaps menores (2px na lista da barra lateral, 4px nas células do calendário, 3–5px na tipografia interna de um cartão) são deliberados — são elementos de uma mesma unidade visual, não controles independentes.

## 4. Padrão de botões — o cuidado principal

A base global de `button` no CSS é "botão-pílula" cheio:

```css
button {
  border: none;
  border-radius: 999px;
  padding: 10px 18px;
  background: var(--accent);
  color: #fff;
  box-shadow: 0 10px 22px rgba(192, 95, 60, 0.25);
}
```

Isso é ótimo para uma ação real ("Salvar", "Enviar chamada"), mas **qualquer `<button>` usado como link, card clicável ou item de lista precisa resetar essas propriedades explicitamente.** Foi a causa raiz dos três bugs corrigidos nesta rodada:

- `.nome-link` num `<button>` (nome do catequisando em Frequência) herdava fundo terracota sólido + texto branco → parecia um botão de erro/alerta chamando atenção, quando é só um link para abrir a ficha.
- `.turma-chamada-card` (card de turma em "Minhas turmas") herdava `color: #fff` do botão-base → título da turma ficava ilegível sobre o fundo claro do card.
- `.adm-encontro` (linha de encontro em "Corrigir chamada") tinha o mesmo problema com a data.

**Checklist ao estilizar um `<button>` que não é uma ação primária:**

```css
.meu-componente {
  display: inline;      /* ou block/flex, conforme o layout */
  background: none;
  border: none;
  box-shadow: none;
  padding: 0;            /* ou o padding próprio do componente, nunca o herdado */
  border-radius: 0;      /* ou o raio próprio do componente */
  font: inherit;
  width: auto;
  color: var(--ink);     /* ou --accent-2 se for um link */
  text-decoration: none;
  cursor: pointer;
}
```

Regra de bolso: **todo `<button>` estilizado como card ou como link precisa declarar `color` e `background` explicitamente.** Não confiar na herdada. Um jeito rápido de auditar isso no futuro: procurar por `<button class="...">` no JS e conferir se a classe correspondente no CSS define `color` — se não define, provavelmente está herdando branco do botão-base.

### Variantes de botão que já existem — reusar em vez de criar novo

- **Primário** (`button` puro ou `.primary` se existir): pílula sólida terracota, texto branco. Ação principal da tela.
- **Secundário** (`button.secondary`): fundo branco, borda e texto `var(--accent)`. Ação alternativa, menos prioridade visual.
- **Link inline** (`.nome-link`): sem fundo, sem borda, cor `var(--accent-2)`, sublinhado só no hover. Para navegação disfarçada de texto.
- **Card clicável** (ex.: `.turma-chamada-card`, `.adm-encontro`): fundo `var(--paper)` ou branco, sem pílula, `color: var(--ink)` explícito, conteúdo interno (título, badge, legenda) é quem carrega a cor/ênfase — o botão em si é só um contêiner.

## 5. Cards de listagem

Um card de item de lista (turma, encontro, catequisando) segue este esqueleto:

```
<card>
  <título forte>            → color: var(--ink), font-weight herdado de <strong>
  <legenda .muted>          → color: var(--muted), font-size 0.9rem
  <badge .status.*>         → cor tintada conforme seção 1
  <ação/CTA>                → texto var(--accent-2), nunca outro tom de link
</card>
```

Evitar: título sem cor própria dentro de um elemento que pode herdar branco (ver seção 4), badge com fundo sólido saturado para informação neutra, mistura de `--accent` e `--accent-2` como se fossem intercambiáveis (— `--accent` é ação/marca; `--accent-2` é link/dado secundário).

## 6. Checklist rápido para telas novas

Antes de considerar uma tela pronta, conferir:

1. Todo texto sobre fundo colorido tem contraste suficiente (nada de branco sobre `--paper`, nada de `--ink` sobre `--accent` sólido).
2. Nenhum hex novo foi escrito direto no CSS — reusar ou, se for um tom genuinamente novo e recorrente, promover a variável em `:root`.
3. Todo `<button>` que não é uma ação primária reseta `background`, `color`, `border-radius`, `box-shadow` e `padding` herdados.
4. Painel usa `.panel` (ou o mesmo padding/radius/shadow) em vez de valores soltos.
5. Badge de status usa uma das quatro variantes (`ok`/`warning`/`error`/`neutro`) — não um estilo ad-hoc.
6. Título de seção é `h2` (1.5rem); legenda é `.muted` (0.9rem) — nenhum tamanho de fonte novo inventado.
7. Espaçamento entre painéis é `24px`; dentro do painel, `16px` de gap e `22px 24px` de padding.
8. Nenhum campo estoura a coluna: `min-width: 0` no campo **e** na célula do grid (seção 3b).
9. Todo container que empilha filhos declara `display: grid` e `gap` — não confie em margem (seção 3c).
10. Nada abaixo de 10px entre dois controles clicáveis, inclusive no celular (seção 3d).
11. Testado com o texto mais longo que o banco aceita, não com "Teste 1" — e conferido se a página rola na horizontal.
