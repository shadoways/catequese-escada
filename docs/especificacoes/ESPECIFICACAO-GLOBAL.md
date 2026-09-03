# Especificação global — Catequese Escada

Contexto que vale para o sistema inteiro: quem usa, o vocabulário do domínio, as
regras que nenhuma tela pode violar e as convenções de código.

Este arquivo responde **"o que é verdade em todo lugar"**. O que é específico de uma
tela vive em `tela-<nome>.md`. A identidade visual vive em
`../padroes-visuais/padrao-visual-catequese.md`.

---

## 1. Para que serve

Uma paróquia com várias comunidades precisa saber quem está na catequese, se está
frequentando e o que está marcado. Antes disso era caderno e planilha, e a informação
morria com o catequista que saía.

O sistema atende **três públicos com necessidades bem diferentes**, e essa diferença
é a coisa mais importante do produto:

- **O catequista** abre o sistema para fazer uma coisa só, com a turma na frente dele:
  a chamada de hoje. Tela dele precisa ser rápida e não pedir decisão.
- **O coordenador de comunidade** acompanha as turmas da comunidade dele e corrige o
  que os catequistas não podem corrigir.
- **O coordenador paroquial** administra tudo: usuários, turmas, matrículas,
  configurações e a agenda da paróquia.

## 2. Vocabulário

Usar exatamente estes termos, em código e em tela. Sinônimo inventado é o começo da
divergência.

| Termo | O que é |
|---|---|
| **Catequisando** | Quem recebe a catequese. |
| **Catequista** | Quem ensina. Pertence a uma ou mais turmas. |
| **Coordenador** | Responsável por uma comunidade. |
| **Coordenador paroquial** | Administrador do sistema. Responde pela paróquia inteira. |
| **Comunidade** | Uma capela/comunidade da paróquia. |
| **Turma** | Grupo de catequisandos com um percurso e uma categoria. |
| **Categoria da turma** | Decide a regra de frequência (ver §4). |
| **Matrícula** | Vínculo catequisando ↔ turma num ano. |
| **Encontro** | A "aula". É onde a chamada acontece. |
| **Chamada** | O ato de marcar presença num encontro. |
| **Frequência** | O percentual apurado a partir das chamadas. |
| **Evento** | Item da agenda: formação, sacramento, rito do RICA ou encontro avulso. |
| **Formação** | Trilha de formação **de catequista**, com vários encontros. |
| **RICA** | Ritual de Iniciação Cristã de Adultos. Ver §7. |

## 3. Papéis e alcance

Três tipos, em ordem crescente de acesso (`TipoUsuario`):

| | `CATEQUISTA` | `COORDENADOR` | `COORDENADOR_PAROQUIAL` |
|---|---|---|---|
| Consultar fichas e turmas | sim | sim | sim |
| Chamada | só das turmas dele | sim | sim |
| Alterar cadastro | não | sim | sim |
| Alcance dos dados | turmas em que atua | a comunidade dele | tudo |
| Gestão de usuários e configurações | não | não | sim |

**O escopo é de dados.** `EscopoAcessoService` responde "o que este usuário enxerga",
e os serviços filtram por ali. A tela apenas reflete — nunca decide.

> **Pendência conhecida:** `usuario.id_comunidade` só passou a ser preenchível por tela
> recentemente. Coordenador sem comunidade definida não consegue cadastrar evento de
> comunidade — é proposital, mas parece defeito para quem não sabe. Ver §6.

## 4. Regras de frequência

O mínimo padrão é **80%** (`CalculoFrequencia.MINIMO_PADRAO`), mas quem decide *como*
apurar é a **categoria da turma**:

| Categoria | Janela de apuração | Anos previstos |
|---|---|---|
| `PRE_CATEQUESE` | não apura | — |
| `EUCARISTIA` | ano civil | 2 |
| `CRISMA` | ano civil | 2 |
| `ADULTOS` | semestre | 2 |
| `CATECUMENATO` | por etapa | variável |
| `PERSEVERANCA` | não apura | — |

Categoria é **campo próprio**, nunca deduzida do nome da turma: renomear "Crisma I"
para "Crisma 1" faria a regra errar em silêncio, que é a pior falha possível num
controle de frequência.

As cinco regras de contagem, que valem também para a formação de catequista:

1. **Só encontro fechado conta.** Encontro previsto do resto do ano não pode virar
   falta, senão todo mundo começa o ano reprovado.
2. **Encontro cancelado não entra em conta nenhuma.** Feriado, chuva ou catequista
   doente não viram falta de ninguém — por isso cancelar exige motivo registrado.
3. **Falta justificada sai da conta**, em vez de contar contra.
4. **Encontro realizado sem marcação conta como falta.** Se contasse como "não
   apurado", bastaria não fazer a chamada para todos ficarem com 100%.
5. **Sem encontro apurado, o percentual é nulo — não zero.** São coisas diferentes, e
   0% numa turma que não começou assusta sem motivo.

**Presença em evento (retiro, missa) não entra nos 80% da turma.** Faltar num retiro
não reprova quem cumpriu os encontros.

## 5. Agenda: dois eixos

Todo evento responde a duas perguntas **independentes**:

- **`nivel`** — *de quem ele é*. Decide quem pode alterar.
  `DIOCESANO`, `REGIONAL`, `PAROQUIAL`, `COMUNIDADE`, `TURMA`.
- **`tipo`** — *o que ele é*. Decide como aparece e se tem chamada.
  `FORMACAO`, `SACRAMENTO`, `RITO_RICA`, `ENCONTRO`.

Na tela, a **cor** é o nível e o **ícone** é o tipo. Codificar os dois na mesma cor foi
o que deixava a leitura confusa.

**Conflito de agenda** não é "um evento por dia" — é **público sobreposto**. Evento de
nível paroquial ou acima bate com qualquer outro; comunidade bate com a mesma
comunidade e com as turmas dela; turma bate só com ela mesma. Evento cancelado nunca
ocupa ninguém.

## 6. Invariantes do sistema

Regras que nenhuma tela nova pode violar. Se uma delas atrapalhar, o certo é discutir
a regra — não contornar.

1. **A permissão é verificada no servidor**, sempre, inclusive no caminho que a tela já
   esconderia.
2. **Ao editar, a permissão é checada antes e depois** de aplicar o formulário. Sem o
   segundo teste, quem pode editar um evento da própria turma poderia "promovê-lo" a
   paroquial e passar a mandar num evento que nunca foi dele.
3. **Campo que decide comportamento é enum**, nunca texto livre.
4. **Nada é apagado**: matrícula vira `DESISTENTE`, encontro vira `CANCELADO`, usuário
   vira inativo.
5. **Toda marcação é auditada** — quem e quando.
6. **Vínculo que não pertence ao estado atual é zerado.** Mudar um evento de `TURMA`
   para `PAROQUIAL` limpa o `id_turma`; senão a permissão passaria a olhar uma turma
   sem relação nenhuma com o evento.
7. **Percentual mínimo é configurável**, nunca constante no código: entre as escolas
   diocesanas reais o mínimo varia (75%, 80%).
8. **Falhar fechado.** Na dúvida sobre permissão de escrita, negue — e explique o
   porquê na tela, porque "não acontece nada" é o pior retorno possível.

## 7. Notas litúrgicas

Levantadas em fonte antes de virar código, e fáceis de errar:

- No RICA existem **duas entregas oficiais**: o Símbolo (Credo) e a Oração do Senhor
  (Pai-Nosso). A entrega da Bíblia é gesto opcional dentro do rito de entrada, não rito
  próprio. As entregas da catequese infantil brasileira (Mandamentos, Bem-Aventuranças,
  terço) são adaptação pastoral, não norma.
- **Três escrutínios**, no 3º, 4º e 5º Domingos da Quaresma. Eleição no 1º. Sacramentos
  na Vigília Pascal.
- As entregas acontecem **na semana seguinte** a cada escrutínio, não no domingo
  seguinte — o domingo seguinte ao 1º escrutínio já é o 2º.
- O rótulo do primeiro rito varia por diocese ("Entrada no catecumenato", "Acolhida",
  "Admissão"), por isso é texto livre e não enum.
- **Batismo não tem fluxo único**: criança até 7 anos segue o Ritual do Batismo de
  Crianças; adulto no catecumenato recebe os três sacramentos juntos; quem já foi
  batizado validamente em outra igreja não se rebatiza.

## 8. Arquitetura e convenções

**Backend.** Kotlin + Spring Boot. Camadas: `controller` (rota e DTO) → `service`
(regra) → `repository` (dados). Regra de negócio mora no serviço, nunca no controller,
para a mesma regra valer em qualquer rota que a chame.

**Frontend.** JavaScript puro, sem framework e sem build. Cada tela é um arquivo `.js`
com IIFE, expondo uma única função `window.carregarXxx()` que `script.js` chama ao
trocar de aba. As telas são abas dentro de `index.html` (`.tab-content`), não páginas
separadas.

**CSS.** Um `style.css` só, com tokens em `:root`. Nunca escrever cor, raio ou sombra
"no olho" — sempre a variável. Detalhes no guia de padrão visual.

**Nomes.** Português sem acento em identificador (`idComunidade`, `naoPodeCriar`); com
acento no texto que o usuário lê. Tabelas com prefixo `tb_`.

**Comentários.** Explicam o **porquê**: a decisão, a armadilha, o caso que motivou. Se
o comentário só repete o código, ele é dívida.

**Migração SQL.** Um arquivo por assunto em `sql/<assunto>/`, escrito para MariaDB,
seguro para rodar em produção (só cria estrutura, não apaga dado) e com um bloco de
conferência no fim.

## 9. Definição de pronto

Uma tela só está pronta quando:

- [ ] A especificação da tela está preenchida e bate com o que foi feito.
- [ ] A permissão é verificada **no serviço**, e há caso de teste para cada papel.
- [ ] Os estados vazios, de erro e de "sem permissão" **explicam o que houve** e o que
      fazer — nunca ficam mudos.
- [ ] Kotlin compila (`./gradlew compileKotlin`).
- [ ] Sem erro de JavaScript no console.
- [ ] Layout conferido em 1280 / 760 / 400px: nada estoura o pai, nada rola na
      horizontal.
- [ ] Testada com **o texto mais longo que o banco aceita**, não com "Teste 1".
- [ ] Os testes de regressão em `docs/` continuam passando.
- [ ] A migração SQL, se houver, foi escrita e conferida.
