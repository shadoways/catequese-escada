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

## 3. O que mudou na tela

**Filtro de verdade no topo: comunidade e turma.** Não havia nenhum — e os três selects
*dentro* de cada cartão (categoria, fase, comunidade) eram lidos como filtro. Com o
filtro explícito acima, o que está no cartão deixa de ser ambíguo.

**O botão "Salvar" do cartão saiu — mas a função ficou.** Ele não salvava filtro
nenhum: gravava a **classificação da turma**, que é justamente o que destrava a
comunidade nos Indicadores (o balde "Sem comunidade definida" vem daí). Apagar sem mais
nada tiraria a única forma de classificar turma. A classificação passou a gravar **na
mudança do select**, com uma confirmação discreta no próprio cartão — o botão some e a
capacidade fica.

**"Corrigir chamada" saiu do cartão**, como pedido. O painel e o código continuam
inteiros, mas **hoje não há como chegar até ele** — falta decidir onde ele mora. É o
primeiro item da §7.

**"Matrículas" virou "Inscrições"**, aqui e na aba de Indicadores.

## 4. Regras de movimentação

Vivem em `RegrasDeMovimentacao`, um objeto **puro** — sem Spring, sem repositório. Regra
de percurso muda depois de reunião de coordenação; escondida dentro de um serviço com
dez dependências, ninguém consegue conferir nem testar.

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

## 8. Como verificar

- [ ] O filtro de comunidade encolhe a lista de turmas, e limpa a turma escolhida
- [ ] Mudar categoria/fase/comunidade grava sozinho e confirma no cartão
- [ ] Não há mais botão "Salvar" nem "Corrigir chamada" no cartão de turma
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

**Não verificado daqui:** tudo em §4 é regra de servidor, e o Gradle não roda no
sandbox. `RegrasDeMovimentacao` é objeto puro — é o melhor candidato a teste de unidade
do projeto, e a lista acima é a bateria pronta.
