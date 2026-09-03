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
- **`flex-basis: 100%` em container `flex-direction: column`** não é largura, é
  ALTURA. Em `≤600px` o `.row` vira coluna, e um filtro com `flex: 1 1 100%` estourava
  a altura da linha e quebrava para uma SEGUNDA COLUNA, saindo pela direita da tela.
  Ali largura cheia já vem do `align-items: stretch`.
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
python3 docs/kt_comentario_check.py           # KDoc que não fecha (o Gradle não roda aqui)
python3 docs/regressao.py                     # só uma aba visível por vez
python3 docs/regressao-agenda-dia.py          # lista do dia: abrir, editar, excluir
python3 docs/regressao-agenda-transicoes.py   # trocar de dia limpa o estado anterior
python3 docs/regressao-indicadores.py         # comparação, filtro e o caso sem base
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
Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Xdehjdk8wJPA5tXT72jASk
```

Pode commitar e dar `push` na branch de trabalho sem perguntar a cada vez. O que exige
minha palavra é mudar de branch, mexer em `main`/`develop` ou reescrever histórico.
