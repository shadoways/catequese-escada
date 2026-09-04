# Scripts SQL

Organizados por finalidade, não por data. Ao criar um script novo, ver se ele se encaixa numa pasta existente antes de criar uma nova.

- **setup-inicial/** — cria a base do zero (tabelas mapeadas do JPA + dados iniciais). Usado para provisionar um banco novo.
- **migracao-usuarios/** — evolução do schema para autenticação/permissões (`tb_usuario` e afins). Três variantes do mesmo script para contextos diferentes: produção (com procedures, compatível MySQL/MariaDB), DBeaver (sem `DELIMITER`) e dev local simples (usa `ADD COLUMN IF NOT EXISTS` nativo do MariaDB).
- **documentos/** — migração e correção do schema de `tb_documento`/`tb_documentos`.
- **matriculas/** — backfill de dados (não é migração de schema — insere registros).

- **dados-dev/** — massa de teste do banco de desenvolvimento. `MASSA_DEV.sql` e
  **gerado** por `docs/gerar_massa_dev.py` (semente fixa); edite o gerador, nunca o SQL.
  Todo id comeca em 900000, entao o bloco COMO DESFAZER apaga exatamente o que ele criou.
  **Nao rode em producao.**
