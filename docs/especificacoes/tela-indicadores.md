# Tela: Indicadores

**Situação:** implementada
**Aba:** `data-tab="indicadores"` · **Arquivo:** `indicadores.js`

> **Reescrita.** A primeira versão era um contador com filtros — "quantos, quebrados
> por dimensão". Isso ainda é ferramenta de gestão, que é o que o sistema já faz. O que
> esta tela é: **relatório**. A diferença está na §1 e governa todo o resto.

## 1. Para que serve

Responder **"como está indo a catequese, comparada com o ano passado?"**

O sistema já tem telas de gestão, e elas são de operação: o catequista acompanha a
frequência da turma dele, o coordenador vê se os catequistas estão indo às formações, a
aba Dashboard de hoje lista pendência de documento. São ferramentas de quem **faz**.

Esta tela é de quem **presta contas**. Não se opera nada aqui — se lê. Ela existe para
a reunião de coordenação e para a conversa com a diocese, e é impressa.

**A consequência prática, que vale como regra de projeto:**

> **Número sem comparação não é indicador.** "312 catequisandos" é um dado. "312, contra
> 287 no ano passado — +25, +8,7%" é um indicador. Nenhum número aparece sozinho nesta
> tela.

A aba `dashboard` de hoje continua exatamente como está.

## 2. Quem usa

| Papel | O que pode fazer aqui |
|---|---|
| Catequista | **Não vê a aba.** |
| Coordenador | **Não vê a aba.** |
| Coordenador paroquial | Tudo. Único papel com acesso. |

Permissão é de dados, não de tela (invariante 1): esconder o botão não basta, a rota
também responde 403. O padrão do repositório é uma linha em `SecurityConfig`, **antes**
da regra genérica de `GET /api/**` (hoje na linha 167):

```kotlin
.requestMatchers("/api/indicadores/**").hasRole("COORDENADOR_PAROQUIAL")
```

Não há method security no projeto (nenhum `@PreAuthorize`); seguir o que já existe para
`/api/admin/**` e `/api/usuarios/**`.

## 3. Regras

### 3.1 Comparação

1. **A base de comparação padrão é o ano anterior**, e ela aparece escrita por extenso
   ao lado de cada número — nunca só uma seta verde. Seta sem o número de referência
   obriga a pessoa a confiar sem conferir.
2. **Ano corrente compara até a mesma data.** Em setembro, comparar o ano em curso com
   o ano anterior **inteiro** mostra uma queda que não existe. A comparação é sempre
   *"até 3 de setembro"* dos dois anos, e a tela diz isso no cabeçalho. Esta é a
   armadilha número um de tela de relatório.
3. **Base pequena não vira percentual.** Com menos de 10 na base, `1 → 2` viraria
   "+100%". Abaixo desse piso mostra-se só a diferença absoluta ("+1"). Percentual sobre
   base minúscula é ruído com cara de tendência.
4. **Base zero não tem variação**: mostra-se **"novo"**, não "+∞" nem "+100%".
5. **Sem ano anterior no sistema, não há comparação** — o cartão diz "primeiro ano
   apurado", e não uma variação de 0%. É o estado em que a paróquia vai abrir a tela na
   primeira vez.
6. **Aumento nem sempre é bom.** Verde e vermelho só onde a direção é inequívoca
   (catequisandos, participação em formação, retenção). Em desistência a escala é
   invertida; onde não há "bom" definido (número de eventos), a variação é neutra, em
   cinza.

### 3.2 Quem entrou e quem saiu

7. **Entrada** = catequisando com matrícula neste ano e **sem** matrícula no ano
   anterior. **Saída** = tinha no ano anterior e não tem neste. **Permaneceu** = tem nos
   dois. É a única leitura que responde "quantos entraram e quantos saíram" —
   comparar dois totais não separa quem chegou de quem ficou.
8. **Saída por conclusão não é perda.** Quem terminou a Crisma e saiu cumpriu o
   percurso. Duas linhas separadas:
   - **concluíram** (`CONCLUIDO`, ou percurso encerrado) — resultado bom;
   - **abandonaram** (`DESISTENTE`, `NAO_CONCLUIDO`, ou simplesmente não voltou) —
     é este o número que preocupa.
   Somar os dois numa "taxa de saída" esconderia justamente a diferença que interessa.
9. **Retenção** = permaneceram ÷ matriculados no ano anterior, **descontando quem
   concluiu** do denominador. Sem esse desconto, uma paróquia que forma muita gente
   apareceria com retenção ruim por estar indo bem.
10. **Transferido não é saída da catequese** — é saída daquela turma. Sai da contagem
    dos dois lados.

### 3.3 O que se conta

11. **A unidade é a matrícula, não a pessoa.** `tb_catequisando` é cadastro histórico:
    só cresce e nunca bate com um ano. Quem tem ano, turma e situação é a matrícula.
    Pessoas distintas aparecem ao lado **só quando os dois números diferirem**.
12. **A comunidade vem da turma da matrícula**, não de `catequisando.id_comunidade`. O
    do catequisando é do cadastro (onde a pessoa pertence) e não tem ano; o da turma é
    onde ela está sendo catequizada **naquele ano**.
13. **`Turma.id_comunidade` é nulo em turma antiga** → balde explícito **"Sem
    comunidade definida"**, com aviso e link para classificar. Escondê-lo faria o
    gráfico não fechar com o total, que é pior do que mostrar o problema.
14. **Catequista do ano é quem atuou numa turma daquele ano** (`tb_turma_catequista`
    mais o responsável principal da turma), e a comunidade dele é a da turma.
    *(Mudou durante a implementação.)* A ideia original era usar
    `Usuario.id_comunidade`, mas esse campo diz onde a pessoa está **hoje** — não tem
    ano. Com ele, os dois lados da comparação dariam sempre o mesmo número e o
    indicador viraria enfeite. O vínculo com a turma é o único que tem ano.
    Consequência aceita: quem atua em duas comunidades conta nas duas, e a soma das
    comunidades pode passar do total da paróquia, que é distinto. A tela diz isso.

### 3.4 Frequência e formação

15. **Reusar `FrequenciaService`, nunca recalcular.** As cinco regras de contagem
    (especificação global §4) vivem lá: só encontro `FECHADO` conta, `CANCELADO` não
    entra, justificada sai da conta, realizado sem marcação é falta, e sem encontro
    apurado o percentual é **nulo, não zero**.
16. **`PRE_CATEQUESE` e `PERSEVERANCA` aparecem como "não se aplica"**, jamais 0%.
17. **A pergunta da formação é participação, não quantidade.** Por nível
    (`DIOCESANO`, `REGIONAL`, `PAROQUIAL`), três números que contam a história inteira:
    **inscritos → participaram → atingiram o mínimo**. Um nível com 40 inscritos e 6
    participantes é exatamente o que precisa saltar aos olhos.
18. **O mínimo não é 80 fixo** — vem de `formacao.percentual_minimo`, e a tela diz qual
    usou.
19. **`COMUNIDADE` e `TURMA` não entram no bloco de formação**: formação de catequista
    hoje existe nos três níveis de cima.

### 3.5 Eventos

20. **Contados por `dataInicio`**, quebrados por tipo e por situação. **Cancelado
    aparece, separado** — sumir com ele faria o total do ano encolher sem explicação
    entre uma consulta e outra.

### 3.6 Filtro

21. **Dois controles, só: ano e comunidade.** Tela de relatório não é painel de
    controle. A barra da versão anterior tinha seis campos, e cada campo é uma chance
    de a pessoa ler um número achando que é outro.
22. **O que está filtrado aparece escrito**, em ficha removível, com "Ver a paróquia
    inteira" ao lado. Filtro esquecido é o erro mais caro que uma tela dessas comete.
23. **Clicar na barra de uma comunidade filtra por ela.** Não é descobrível sozinho:
    cursor de mão, realce ao passar o mouse e a legenda fixa *"Clique numa comunidade
    para ver só ela."*
24. **Nenhum bloco desaparece por causa do filtro** — sem dado, continua no lugar
    dizendo "nenhum registro". Bloco que some faz a tela mudar de forma a cada clique.

## 4. Dados

**Só leitura.** Entidades: `Matricula`, `Catequisando`, `Turma`, `Comunidade`,
`Catequista`, `Usuario`, `Encontro`, `Presenca`, `Evento`, `Formacao`,
`FormacaoInscrito`, `PresencaFormacao`.

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `GET /api/indicadores/opcoes` | Anos disponíveis e comunidades | coordenador paroquial |
| `GET /api/indicadores?ano=&idComunidade=` | O relatório inteiro, já com a comparação | coordenador paroquial |

**Um endpoint só.** Oito requisições fariam os números aparecerem em ordem aleatória,
cada um com seu "carregando". Um payload só também garante que todos os blocos falam do
**mesmo instante** do banco — num relatório, números de instantes diferentes é defeito.

**O servidor devolve o par, não o número solto.** Cada indicador vem como
`{ valor, base, variacao, variacaoPercentual, direcao, comparavel }`. A tela **não
calcula variação** — regra de comparação duplicada em JS é regra que vai divergir do
que for impresso.

**Serviço novo:** `IndicadoresService`, compondo `FrequenciaService`,
`FrequenciaFormacaoService` e `EscopoAcessoService` em vez de reimplementar regra.

> **Desempenho.** `CatequisandoRepository` e `TurmaRepository` são `JpaRepository`
> vazios e não há nenhum `GROUP BY` no projeto — hoje tudo é filtrado em memória. Como
> a tela apura **dois anos** por requisição, o custo dobra. Para a paróquia atual é
> aceitável; se passar de ~1s, o caminho é `@Query` com `GROUP BY` no repositório, não
> cache no navegador — relatório com número velho é pior que relatório lento.

**Sem migração SQL.** Nada de campo novo. O que falta é classificação de dado que já
existe (turma sem comunidade, usuário sem comunidade), que é trabalho humano nas telas
de Turmas e Usuários.

## 5. Estados da tela

| Estado | O que aparece |
|---|---|
| Carregando | Esqueleto dos blocos com "Apurando 2026 e 2025…" — os blocos já no lugar, para a tela não pular quando o dado chegar |
| Primeiro ano apurado | Os números do ano, e no lugar da variação: "primeiro ano apurado — sem base de comparação" |
| Vazio | "Nenhuma matrícula em 2026", com **Ver a paróquia inteira** ao lado se houver filtro |
| Erro de conexão | A razão vinda do servidor, e **Tentar de novo** |
| Sem permissão | Não acontece pela tela (a aba não existe para os outros papéis); pela API, 403 explicando que é exclusiva do coordenador paroquial |
| Dado por classificar | Faixa de aviso: "3 turmas sem comunidade definida — 47 matrículas estão em *Sem comunidade*", com link para Turmas |
| Preenchido | Cabeçalho do período + os blocos da §6 |

## 6. Componentes

### Layout, de cima para baixo

**Cabeçalho do relatório** — "Catequese em 2026 · comparado com 2025 · até 3 de
setembro · paróquia inteira". Uma linha, em texto. É ela que vai no papel.

**1. Os cinco números do ano** — fila de cartões, cada um com valor, comparação escrita
e uma minilinha dos últimos anos:

| Cartão | O que compara |
|---|---|
| Catequisandos | matrículas ativas, contra o ano anterior |
| Catequistas | ativos, contra o ano anterior |
| Entraram | novos no ano |
| Saíram | separando concluíram de abandonaram |
| Retenção | % que permaneceu, descontando quem concluiu |

**2. Evolução** — colunas dos últimos 5 anos, catequisandos e catequistas. Duas séries,
duas escalas muito diferentes → **dois gráficos**, nunca dois eixos y no mesmo. Eixo
duplo é a forma mais fácil de fazer duas curvas "se cruzarem" sem que isso signifique
nada.

Coluna e não linha porque os pontos são discretos: não existe meio-termo entre 31/12 e
1º/01. E **cada gráfico tem moldura própria com o topo da escala escrito** — lado a
lado sem separação, os dois liam-se como um gráfico só de oito colunas, e a coluna de
34 catequistas aparecia da mesma altura que a de 312 catequisandos. Era o erro do eixo
duplo por outro caminho.

**3. Movimento do ano** — entraram acima da linha, saíram abaixo, saldo ao lado. Barra
divergente, porque a pergunta é de polaridade: ganhou ou perdeu.

**4. Por comunidade** — barras horizontais (nome de comunidade é longo; barra
horizontal deixa ler sem virar a cabeça). Catequisandos e catequistas, cada um com a
variação contra o ano anterior ao lado. **Sequencial de um tom só** — a identidade está
no rótulo, não na cor.

**5. Formações** — por nível, o funil `inscritos → participaram → atingiram o mínimo`,
com a taxa de participação como medidor. Três linhas nomeadas, uma tabela ao lado.

**6. Frequência** — média da paróquia contra o ano anterior, e quantas turmas estão
abaixo do mínimo. `PRE_CATEQUESE` e `PERSEVERANCA` como "não se aplica".

**7. Eventos e sacramentos** — total no ano contra o anterior, por tipo (`FORMACAO`,
`SACRAMENTO`, `RITO_RICA`, `ENCONTRO`) e por situação, com cancelados à parte.

**8. Fundos da catequese** — cartão reservado, ver §7.

### Gráficos

**HTML e CSS, sem biblioteca e sem SVG.** Não há build de JS no projeto, e depender de
CDN quebraria a tela numa paróquia com internet ruim — que é exatamente onde ela roda.

*(Mudou durante a implementação: era SVG.)* O SVG com `viewBox` escalado trouxe dois
defeitos reais, que a regressão pegou: o rótulo no fim da barra passava da largura do
pai — 76 elementos estourando a 400px — e o texto crescia junto com a largura da tela,
porque `font-size` em unidade de usuário escala com o `viewBox`. Barra em HTML não tem
nenhum dos dois, e imprime melhor.

Regras de desenho:

- **Todo gráfico vem com a tabela de números.** Quem não lê gráfico lê a tabela, e é
  ela que a impressão leva.
- **Nunca dois eixos y.** Duas medidas de escala diferente → dois gráficos.
- **Cor pela entidade, nunca pela posição.** Filtrar não pode repintar quem sobrou.
- **Sequencial de um tom** onde o assunto é magnitude (comunidades, turmas); a cor
  categórica fica só onde as séries **são** o assunto (tipo de evento).
- **Rótulo direto** nas séries; nunca um número em cima de cada ponto.
- Marcas finas, grade discreta, ponta arredondada de 4px, 2px de respiro entre
  preenchimentos vizinhos.
- Texto sempre em `--ink` / `--muted`, nunca na cor da série.

**Paleta categórica — validada, não escolhida no olho.** Os tons `--nivel-*` que a
Agenda usa **não servem para gráfico**: diocesano (`#4a5f8a`) e regional (`#2f6f7e`)
ficam a ΔE 6,2 um do outro em visão normal — indistinguíveis lado a lado numa barra.
Eles funcionam como chip porque ali há um rótulo colado. A paleta abaixo passou em
todos os testes (banda de luminosidade, croma, separação para daltonismo protan/deutan/
tritan, e contraste) **tanto no bege do painel quanto no branco do papel**:

```css
:root {
  --graf-1: #c05f3c;  /* terracota — a cor de marca, primeira série */
  --graf-2: #0f7ba8;  /* azul */
  --graf-3: #8f7c00;  /* ocre */
  --graf-4: #6d54b5;  /* violeta */
}
```

Ordem fixa: a primeira série é sempre `--graf-1`. Nunca gerar um quinto tom — acima de
quatro categorias, agrupar o resto em "Outros" ou virar tabela.

**Classes novas** (bloco "Indicadores" do `style.css`): `.ind-cabecalho`,
`.ind-cartao`, `.ind-variacao` (`.subiu` / `.caiu` / `.neutra`), `.ind-grafico`,
`.ind-barra`, `.ind-tabela`, `.ind-ficha`. Reusar `.panel`, `.grid`, `.row`, `.status`,
`.muted` e os tokens de `:root` — nenhum hex fora deles.

### Impressão

`@media print` no mesmo `style.css`. **Armadilha encontrada:** a regra de impressão da
ficha esconde `.tab-content` inteiro com `!important` — sem tratar isso, imprimir esta
tela sairia em branco. A Frequência resolveu pondo o relatório fora da aba; aqui a tela
inteira é o relatório, então a aba volta com
`#tab-indicadores:not([hidden]) { display: block !important }`. O `:not([hidden])` não é
enfeite: é ele que impede a regra de ressuscitar uma aba fechada.

Fora isso: esconde menu, filtros e botões; **imprime o cabeçalho do período como
subtítulo** (senão o papel vira número sem contexto); força
as tabelas a aparecerem onde a tela mostra só o gráfico; evita quebra de página no meio
de um bloco. "Salvar como PDF" é a impressão do próprio navegador — sem biblioteca.

## 7. O que fica de fora

- **Fundos da catequese — só o desenho, nada de código.** Decisão do Gabriel. O bloco 8
  entra como cartão desabilitado ("Disponível quando existir a tela de lançamentos"),
  visível de propósito para o lugar já estar reservado no layout. O desenho, para quando
  for a hora:

  | Campo | Tipo | Observação |
  |---|---|---|
  | `id_lancamento` | BIGINT | |
  | `tipo` | ENUM | `ENTRADA` / `SAIDA` — enum, nunca texto (invariante 3) |
  | `categoria` | ENUM | `DIZIMO`, `DOACAO`, `EVENTO`, `MATERIAL`, `FORMACAO`, `OUTRO` |
  | `descricao` | VARCHAR | |
  | `valor_centavos` | BIGINT | **inteiro em centavos**, nunca `DOUBLE` — dinheiro em ponto flutuante não fecha o caixa |
  | `data_lancamento` | DATE | |
  | `id_comunidade` | BIGINT NULL | nulo = paróquia |
  | `id_evento` | BIGINT NULL | amarra a despesa ao evento que a gerou |
  | `situacao` | ENUM | `PREVISTO` / `REALIZADO` / `CANCELADO` — cancelado nunca é apagado (invariante 4) |
  | `criado_por`, `criado_em` | | auditoria (invariante 5) |

  Indicadores previstos, no mesmo espírito de comparação: entradas, saídas e saldo do
  ano **contra o ano anterior**; saldo por comunidade; gasto por categoria; previsto ×
  realizado.

- **Recorte por sexo.** Não existe campo de sexo em nenhuma tabela do projeto. Seria
  campo novo em `tb_catequisando` mais migração — e, principalmente, alguém teria de
  preencher o que já está cadastrado.
- **Faixa etária.** Cabe num relatório, mas responde "quem são", não "como está indo".
  Fica para uma segunda rodada, se você sentir falta.
- **Comparar duas comunidades lado a lado.**
- **Metas** ("queremos 300 catequisandos") — exige tela de cadastro de meta, e sem meta
  não há como dizer se 312 é bom.
- **Exportar CSV.** Gabriel escolheu impressão. Acrescentar depois não gera retrabalho:
  os números já estarão montados no serviço.
- **Pendência de documentos.** Já existe na aba Dashboard; duplicar criaria dois números
  que vão divergir. Entra como link.

## 8. Decisões em aberto

1. **Um resumo em palavras no topo?** Uma ou duas frases geradas dos próprios números
   ("A catequese cresceu 8,7% em 2026. A retenção caiu de 84% para 79%, puxada por São
   José."). Ajuda muito quem não lê gráfico — e é a parte mais fácil de soar errada se
   os números tiverem qualquer ressalva. **Não foi feito**, à espera da sua palavra.

Fechadas na implementação: aba chamada **Indicadores** (para não confundir com o
Dashboard que já existe) e posta no grupo **Administração** do menu — não depois de
Agenda como eu havia proposto, porque o grupo Administração é exatamente o conjunto do
que só o coordenador paroquial enxerga, e um item exclusivo dele fora do grupo ficaria
incoerente. Evolução mostra até 5 anos.

## 9. Como verificar

Além da definição de pronto global (`ESPECIFICACAO-GLOBAL.md` §9):

- [x] Catequista e coordenador **não veem a aba**, e a rota devolve 403 para os dois
- [x] **Nenhum número aparece sem comparação** ao lado
- [x] O ano corrente é comparado **até a mesma data**, e a tela diz isso
- [x] Paróquia sem ano anterior mostra "primeiro ano apurado", não 0%
- [x] Base zero mostra "novo"; base menor que 10 mostra a diferença, não o percentual
- [x] Quem concluiu aparece separado de quem abandonou
- [ ] Transferido não conta como saída
- [x] A soma das barras de comunidade bate com o total, com o balde "Sem comunidade"
      preenchido
- [ ] `PRE_CATEQUESE` aparece como "não se aplica", nunca 0%
- [x] Clicar numa comunidade filtra; remover a ficha volta ao estado anterior
- [x] A impressão sai sem menu, com o período no cabeçalho e com as tabelas visíveis
- [x] Nenhum gráfico tem dois eixos y
- [x] A paleta passa no validador contra o bege **e** contra o branco do papel
- [x] Layout em 1280 / 760 / 400px, com o nome de comunidade mais longo que o banco aceita
- [x] `python3 docs/regressao.py` continua passando (só uma aba visível por vez)
- [x] Script novo `docs/regressao-indicadores.py`: filtro, drill-down e limpar

**Não verificado daqui:** "transferido não conta como saída" e "`PRE_CATEQUESE` aparece
como não se aplica" estão implementados no `IndicadoresService`, mas são regras de
backend e o Gradle não roda no sandbox (o proxy bloqueia `services.gradle.org`).
Precisam de `./gradlew compileKotlin` e de uma conferência com dado real. São os dois
primeiros candidatos a teste de unidade — `IndicadoresService.fluxoDe` é função pura
sobre uma lista de matrículas.
