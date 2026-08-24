#!/usr/bin/env bash
# Reproduz localmente as mudancas feitas na sessao: reorganizacao de docs/ e sql/,
# mais o commit do style.css/tokens de cor que ja estava pendente.
# Rode dentro da pasta do projeto (~/workspace/catequese-escada), na branch login-e-permissoes.
set -e

git status --short
echo
echo "Conferindo branch..."
git branch --show-current | grep -q "^login-e-permissoes$" || { echo "Voce nao esta na branch login-e-permissoes. Aborte e troque de branch antes de continuar."; exit 1; }

echo "Movendo scripts SQL para pastas por finalidade..."
mkdir -p sql/setup-inicial sql/migracao-usuarios sql/documentos sql/matriculas

git mv CREATE_TABELAS_MAPEADAS.sql sql/setup-inicial/CREATE_TABELAS_MAPEADAS.sql
git mv PROD_SETUP.sql sql/setup-inicial/PROD_SETUP.sql

git mv MIGRACAO_USUARIOS.sql sql/migracao-usuarios/MIGRACAO_USUARIOS.sql
git mv MIGRACAO_USUARIOS_DBEAVER.sql sql/migracao-usuarios/MIGRACAO_USUARIOS_DBEAVER.sql
git mv MIGRACAO_USUARIOS_DEV_SIMPLES.sql sql/migracao-usuarios/MIGRACAO_USUARIOS_DEV_SIMPLES.sql

git mv ALTER_CAMINHO_ARQUIVO.sql sql/documentos/ALTER_CAMINHO_ARQUIVO.sql
git mv MIGRAR_DOCUMENTOS.sql sql/documentos/MIGRAR_DOCUMENTOS.sql
git mv MIGRAR_DOCUMENTOS_FIX.sql sql/documentos/MIGRAR_DOCUMENTOS_FIX.sql

git mv BACKFILL_MATRICULAS.sql sql/matriculas/BACKFILL_MATRICULAS.sql

cat > sql/README.md <<'EOF'
# Scripts SQL

Organizados por finalidade, não por data. Ao criar um script novo, ver se ele se encaixa numa pasta existente antes de criar uma nova.

- **setup-inicial/** — cria a base do zero (tabelas mapeadas do JPA + dados iniciais). Usado para provisionar um banco novo.
- **migracao-usuarios/** — evolução do schema para autenticação/permissões (`tb_usuario` e afins). Três variantes do mesmo script para contextos diferentes: produção (com procedures, compatível MySQL/MariaDB), DBeaver (sem `DELIMITER`) e dev local simples (usa `ADD COLUMN IF NOT EXISTS` nativo do MariaDB).
- **documentos/** — migração e correção do schema de `tb_documento`/`tb_documentos`.
- **matriculas/** — backfill de dados (não é migração de schema — insere registros).
EOF
git add sql/README.md

echo "Movendo documentacao para docs/, com subpastas por tema..."
mkdir -p docs/checkpoints docs/padroes-visuais

# Os checkpoints ja existem em docs/, so faltam entrar na subpasta certa.
[ -f docs/CHECKPOINT-FREQUENCIA.md ] && git mv docs/CHECKPOINT-FREQUENCIA.md docs/checkpoints/CHECKPOINT-FREQUENCIA.md
[ -f docs/CHECKPOINT-LOGIN.md ] && git mv docs/CHECKPOINT-LOGIN.md docs/checkpoints/CHECKPOINT-LOGIN.md
# docs/checkpoints/FRONTEND_CHECKPOINT_ETAPA_02.md ja esta no lugar certo -- so precisa ser adicionado ao git.
[ -f docs/checkpoints/FRONTEND_CHECKPOINT_ETAPA_02.md ] && git add docs/checkpoints/FRONTEND_CHECKPOINT_ETAPA_02.md

# O guia de padrao visual estava solto na raiz do projeto.
[ -f padrao-visual-catequese.md ] && git mv padrao-visual-catequese.md docs/padroes-visuais/padrao-visual-catequese.md

cat > docs/README.md <<'EOF'
# Documentação

- **checkpoints/** — snapshots de progresso de cada etapa (o que foi feito, o que falta, decisões tomadas no momento).
- **padroes-visuais/** — guia de padrão de UI (paleta, tipografia, espaçamento, componentes) para manter as telas consistentes entre si.
EOF
git add docs/README.md

echo "Adicionando o resto das mudancas pendentes (style.css e afins)..."
git add -A

echo
git status --short
echo
read -p "Conferido acima? Enter para commitar e enviar para o origin, Ctrl+C para cancelar."

git commit -m "Ajusta consistencia visual e organiza scripts SQL / docs por finalidade"
git push -u origin login-e-permissoes

echo "Pronto."
