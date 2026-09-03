# Documentação

- **especificacoes/** — o que cada tela deve fazer. `ESPECIFICACAO-GLOBAL.md` vale para
  o sistema inteiro; `_MODELO-TELA.md` é o modelo a copiar para toda tela nova, e deve
  ser preenchido **antes** do código. O ponto de entrada de tudo é o `CLAUDE.md` da raiz.
- **checkpoints/** — snapshots de progresso de cada etapa (o que foi feito, o que falta, decisões tomadas no momento).
- **padroes-visuais/** — guia de padrão de UI (paleta, tipografia, espaçamento, componentes) para manter as telas consistentes entre si.
- **kt_comentario_check.py** — acha KDoc que não fecha. Comentário de bloco em Kotlin
  aninha, e o compilador só acusa na última linha do arquivo. Vale rodar sempre que o
  Gradle não estiver à mão.
- **regressao*.py** — verificações de tela com Playwright. Rodar antes de dar uma
  mudança de frontend por encerrada:

  ```bash
  python3 docs/regressao.py                     # só uma aba visível por vez
  python3 docs/regressao-agenda-dia.py          # lista do dia da agenda
  python3 docs/regressao-agenda-transicoes.py   # trocar de dia limpa o estado
  python3 docs/regressao-indicadores.py         # as 5 telas, os filtros de cada uma, comparação
  ```

  Os scripts leem a tela a partir de `/tmp/audit/`. Antes de rodar, copie os estáticos
  para lá:

  ```bash
  mkdir -p /tmp/audit && cp src/main/resources/static/* /tmp/audit/
  ```
