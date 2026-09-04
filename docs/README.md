# Documentação

- **especificacoes/** — o que cada tela deve fazer. `ESPECIFICACAO-GLOBAL.md` vale para
  o sistema inteiro; `_MODELO-TELA.md` é o modelo a copiar para toda tela nova, e deve
  ser preenchido **antes** do código. O ponto de entrada de tudo é o `CLAUDE.md` da raiz.
- **checkpoints/** — snapshots de progresso de cada etapa (o que foi feito, o que falta, decisões tomadas no momento).
- **padroes-visuais/** — guia de padrão de UI (paleta, tipografia, espaçamento, componentes) para manter as telas consistentes entre si.
- **kt_*_check.py** — a conferência de Kotlin possível sem Gradle. `kt_comentario_check`
  acha KDoc que não fecha (comentário de bloco em Kotlin aninha, e o compilador só acusa
  na última linha do arquivo); `kt_nomes_check` acha tipo declarado duas vezes no mesmo
  pacote; `kt_argumentos_check` acha argumento nomeado inexistente e obrigatório
  faltando. Os três nasceram de erros que custaram uma ida e volta cada.
- **gerar_massa_dev.py** — gera `sql/dados-dev/MASSA_DEV.sql`, a massa do banco de
  desenvolvimento (dois anos de catequese inventada, para os Indicadores terem o que
  comparar). Determinista; confere a integridade referencial antes de emitir e imprime
  a prévia do que a tela deve mostrar.
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
