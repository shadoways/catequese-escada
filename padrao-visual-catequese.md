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
