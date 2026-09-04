-- =====================================================================
-- MOVIMENTACAO DO CATEQUISANDO
-- =====================================================================
--
-- Uma coluna so. As regras novas (idade minima, fase, catecumenato) sao de
-- codigo e nao precisam de estrutura: elas leem o que ja existe --
-- tb_catequisando.data_nascimento, tb_turma.categoria, tb_turma.etapa e
-- tb_etapa_catecumeno.data_fim.
--
-- Seguro para producao: so acrescenta coluna, nasce NULL, nao mexe em dado.
--
-- Rode no DBeaver como SCRIPT (Alt+X).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 1. tb_matricula.paroquia_destino
-- ---------------------------------------------------------------------
-- Para onde a pessoa foi quando saiu desta paroquia.
--
-- Sem esta coluna, "TRANSFERIDO" nao responde a pergunta que sempre vem
-- depois -- "transferido para onde?" -- e a secretaria acaba anotando em
-- papel. Fica NULL na transferencia interna (ali o destino e a turma nova) e
-- preenchida so quando a pessoa sai da paroquia.
--
-- VARCHAR e nao FK de proposito: a outra paroquia nao esta neste banco, e
-- criar um cadastro de paroquias para guardar um nome seria estrutura demais
-- para o problema.

ALTER TABLE tb_matricula
  ADD COLUMN IF NOT EXISTS paroquia_destino VARCHAR(255) NULL;


-- =====================================================================
-- CONFERENCIA
-- =====================================================================
-- Rode depois e confira:
--
--   SHOW COLUMNS FROM tb_matricula LIKE 'paroquia_destino';
--
--   -- quem ja saiu, e para onde (deve vir vazio numa base sem transferencia
--   -- externa registrada):
--   SELECT m.id_matricula, c.nome, m.ano, m.paroquia_destino
--     FROM tb_matricula m
--     JOIN tb_catequisando c ON c.id_catequisando = m.id_catequisando
--    WHERE m.situacao = 'TRANSFERIDO'
--    ORDER BY m.ano DESC;
--
-- O QUE AS REGRAS NOVAS EXIGEM DO DADO (nao e migracao, e conferencia):
--
--   -- 1. Turma sem categoria nao aceita ninguem vindo de outra turma: a regra
--   --    nao tem como saber se o percurso bate. Classifique estas:
--   SELECT id_turma, nome, ano FROM tb_turma WHERE categoria IS NULL;
--
--   -- 2. Turma sem etapa (fase) nas categorias de dois anos: a transferencia
--   --    exige mesma fase, e nulo nao compara com nulo de forma util.
--   SELECT id_turma, nome, categoria FROM tb_turma
--    WHERE categoria IN ('EUCARISTIA','CRISMA') AND etapa IS NULL;
--
--   -- 3. Catequisando sem data de nascimento: a porta de idade NAO barra
--   --    (seria pior recusar quem tem a idade certa por causa de campo em
--   --    branco), mas a tela avisa. Estes sao os casos:
--   SELECT c.id_catequisando, c.nome FROM tb_catequisando c
--     JOIN tb_matricula m ON m.id_catequisando = c.id_catequisando
--    WHERE c.data_nascimento IS NULL GROUP BY c.id_catequisando, c.nome;
-- =====================================================================


-- =====================================================================
-- COMO DESFAZER
-- =====================================================================
--   ALTER TABLE tb_matricula DROP COLUMN paroquia_destino;
--
-- (Perde o registro de para onde cada um foi. So faz sentido se a coluna
-- ainda estiver vazia.)
-- =====================================================================
