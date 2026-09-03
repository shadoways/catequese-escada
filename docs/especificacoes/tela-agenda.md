# Tela: Agenda

**Situação:** implementada
**Aba:** `data-tab="agenda"` · **Arquivo:** `agenda.js`

Escrita depois da implementação, como exemplo preenchido do modelo. Daqui em diante a
ordem é a inversa: especificação primeiro.

## 1. Para que serve

Mostrar e marcar tudo que a catequese tem no calendário: formações, sacramentos, ritos
do RICA e encontros avulsos.

Quem abre a Agenda vem responder uma de duas perguntas: **"o que tem marcado?"** e
**"onde encaixo mais uma coisa?"**. Por isso são duas visões — mês para marcar, lista
para ler.

## 2. Quem usa

| Papel | O que pode fazer aqui |
|---|---|
| Catequista | Vê a agenda inteira. Cria e edita eventos **das turmas em que atua**. |
| Coordenador | Vê tudo. Cria e edita na **sua comunidade** e nas turmas dela. |
| Coordenador paroquial | Tudo, em qualquer nível. |

**Todo mundo vê a agenda inteira**, inclusive o que não pode alterar — saber que outra
comunidade marcou batismo no mesmo sábado é exatamente o conflito que uma agenda existe
para mostrar. O que não é seu aparece **sem os botões**, em vez de sumir.

Coordenador **sem comunidade definida** não cria nada, e a tela diz isso com todas as
letras, inclusive a quem pedir.

## 3. Regras

1. **Nível decide permissão; tipo decide aparência.** São eixos independentes: uma
   formação pode ser diocesana ou paroquial, um rito do RICA pode ser paroquial ou de
   comunidade. Misturar os dois num campo só foi o que deixou todo evento sem dono.
2. **Nível `COMUNIDADE` exige comunidade; `TURMA` exige turma.** Ao trocar o nível, o
   vínculo que não pertence mais é zerado — senão a permissão passaria a olhar uma
   turma sem relação com o evento.
3. **Permissão conferida antes e depois de editar**, para ninguém "promover" o próprio
   evento de turma a paroquial.
4. **Conflito é público sobreposto**, não "um evento por dia". Uma paróquia com quatro
   comunidades tem várias coisas no mesmo domingo, e travar tudo deixaria o sistema
   inútil na primeira semana.
5. **Conflito avisa, não bloqueia de vez.** O primeiro Salvar é sempre barrado e mostra
   o que bate; o botão vira "Marcar assim mesmo". Existe caso legítimo de dois eventos
   no mesmo dia para o mesmo público — a missa de manhã e o retiro à tarde. Travar sem
   saída levaria alguém a cadastrar com data errada só para conseguir salvar, o que é
   pior que o conflito.
6. **Evento cancelado continua na agenda**, riscado, mas não ocupa ninguém: quem olha o
   mês precisa entender que aquilo foi desmarcado.
7. **Datas do RICA são manuais.** Decisão do Gabriel. As datas são móveis e derivam da
   Páscoa; a tabela de referência está no checkpoint.
8. **Encontro de formação não aparece na chamada de catequisando** — ali quem tem
   presença é o catequista, e a chamada é outra.

## 4. Dados

**Entidades:** `Evento` (reformada), `Formacao`, `FormacaoInscrito`, `PresencaFormacao`.
`Turma` ganhou `id_comunidade`.

| Método e rota | Para quê | Quem pode |
|---|---|---|
| `GET /api/agenda?ano=` | Eventos do ano + resumo | qualquer logado |
| `GET /api/agenda/opcoes` | Níveis/tipos/listas do formulário, já filtrados | qualquer logado |
| `GET /api/agenda/conflitos` | Aviso prévio, enquanto preenche | qualquer logado |
| `POST /api/agenda/eventos` | Criar | conforme nível |
| `PUT /api/agenda/eventos/{id}` | Alterar | conforme nível |
| `DELETE /api/agenda/eventos/{id}` | Excluir | conforme nível |

Migração: `sql/agenda/MIGRACAO_AGENDA.sql`. Dados de teste:
`sql/agenda/DADOS_TESTE_FORMACAO.sql`.

> `podeEditar` vem **resolvido do servidor** em cada evento. A tela não recalcula
> permissão — regra duplicada é regra que vai divergir.

## 5. Estados da tela

| Estado | O que aparece |
|---|---|
| Carregando | "Carregando a agenda…" |
| Vazio | "Nenhum evento neste ano" (ou "com esse filtro") |
| Erro de conexão | Mensagem com a razão vinda do servidor |
| Sem permissão para criar | O motivo, vindo do servidor, **e a quem pedir**. O calendário continua clicável para consultar. |
| `/opcoes` falhou | "A agenda está em modo leitura", com o código do erro |
| Preenchido | Calendário do mês + faixa de resumo |

## 6. Componentes

`.panel`, `.grid`, `.row`, `.status` (`ok`/`warning`/`error`/`neutro`), `.muted`.

Específicos, no bloco "Agenda" do `style.css`: `.agenda-calendario`, `.agenda-cal-dia`,
`.agenda-ev`, `.agenda-chip`, `.agenda-conflitos`, `.agenda-card`.

Cinco tons de nível (`--nivel-diocesano` … `--nivel-turma`), dessaturados para conviver
no fundo bege sem competir com `--accent`, que continua sendo a cor de ação.

**Interação do calendário:**

- **dia vazio** → formulário já naquela data
- **dia com evento** → lista daquele dia, com Editar e Excluir
- **botão `+`** → mais um evento naquele dia; sempre visível onde já há evento, porque
  ali virou a única forma de cadastrar
- **trocar de dia** limpa a lista **e** o formulário do dia anterior — cada dia tem a
  sua lista e o seu cadastro

## 7. O que fica de fora

- **Tela de Formação.** O backend está inteiro (`/api/formacoes`), mas não há UI para
  criar trilha, inscrever catequista e fazer a chamada. Sem ela não dá para cadastrar
  evento do tipo `FORMACAO`.
- **Geração automática do itinerário do RICA.** Decisão do Gabriel: manual.
- **Inscrição de batismo** com vagas e documentos de pais e padrinhos — é um módulo do
  tamanho da agenda inteira.
- **Visão de semana.** Mês e lista dão conta do volume atual.

## 8. Decisões em aberto

- Trocar de dia descarta um formulário preenchido **sem avisar**. Se aparecer perda de
  trabalho na prática, pedir confirmação só quando o título estiver preenchido.
- O conflito pode virar bloqueio absoluto removendo o `confirmarConflito` de
  `AgendaService.exigirAgendaLivre`.

## 9. Como verificar

- [x] Dia com evento abre a lista, **não** o formulário
- [x] O `+` preenche a data e mantém a lista aberta
- [x] Dia vazio vai direto ao formulário
- [x] Evento sem permissão aparece sem Editar/Excluir
- [x] Excluir atualiza a lista; excluir o último fecha a lista
- [x] Trocar de dia fecha o formulário do dia anterior
- [x] Reclicar o **mesmo** dia não descarta o rascunho
- [x] Trocar de mês fecha a lista
- [x] Primeiro Salvar com conflito é barrado; o segundo passa
- [x] Coordenador sem comunidade vê a explicação, não uma tela muda

Scripts: `docs/regressao-agenda-dia.py`, `docs/regressao-agenda-transicoes.py`.
