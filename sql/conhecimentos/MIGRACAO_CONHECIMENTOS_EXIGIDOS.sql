-- =====================================================
-- MIGRACAO_CONHECIMENTOS_EXIGIDOS.sql
--
-- Catálogo de conhecimentos que a paróquia exige do catequista (Kerigma,
-- Artigos do Credo, Pai Nosso...) e a marca de quem já tem cada um --
-- tela-catequistas.md, aba "Conhecimentos".
--
-- Duas tabelas novas, sem relação com `tb_conhecimento_catequista` (que já
-- existia, é usada por nenhuma tela, e guarda área/nível/descrição em texto
-- livre POR catequista, sem catálogo comum) -- ver a KDoc de
-- RequisitoConhecimento.kt para o porquê do nome diferente.
--
-- SEGURO EM PRODUCAO: só cria estrutura nova e insere os 7 itens de exemplo
-- pedidos pelo Gabriel (só se ainda não existirem, pelo nome). Não mexe em
-- nenhuma tabela existente.
--
-- Escrito para MariaDB. Rode o arquivo inteiro de uma vez.
-- =====================================================

-- -----------------------------------------------------
-- 1. tb_requisito_conhecimento: o catálogo
-- -----------------------------------------------------
-- `ativo` em vez de apagar a linha: "tirar" um conhecimento da exigência
-- (o coordenador quer pedir menos) não pode apagar as marcações já feitas
-- de quem tinha esse conhecimento -- regra do projeto, nada é apagado de
-- verdade.

CREATE TABLE IF NOT EXISTS tb_requisito_conhecimento (
  id_requisito   BIGINT       NOT NULL AUTO_INCREMENT,
  nome           VARCHAR(150) NOT NULL,
  ativo          BOOLEAN      NOT NULL DEFAULT TRUE,
  criado_por     VARCHAR(100) NULL,
  criado_em      DATETIME     NULL,
  atualizado_por VARCHAR(100) NULL,
  atualizado_em  DATETIME     NULL,
  PRIMARY KEY (id_requisito)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------
-- 2. tb_requisito_conhecimento_marcado: quem tem o quê
-- -----------------------------------------------------
-- Uma linha por par catequista+requisito -- marcar de novo ATUALIZA a linha
-- (a UNIQUE impede duplicar), não insere outra. Sem FOREIGN KEY para
-- catequista/requisito, mesmo padrão já usado em tb_formacao_inscrito e
-- tb_presenca_formacao nesta base.

CREATE TABLE IF NOT EXISTS tb_requisito_conhecimento_marcado (
  id_requisito_marcado BIGINT       NOT NULL AUTO_INCREMENT,
  id_requisito         BIGINT       NOT NULL,
  id_catequista        BIGINT       NOT NULL,
  possui               BOOLEAN      NOT NULL DEFAULT FALSE,
  marcado_por          VARCHAR(100) NULL,
  marcado_em           DATETIME     NULL,
  PRIMARY KEY (id_requisito_marcado),
  UNIQUE KEY uk_requisito_catequista (id_requisito, id_catequista),
  INDEX idx_reqmarcado_catequista (id_catequista)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------
-- 3. Os 7 conhecimentos de exemplo pedidos pelo Gabriel
-- -----------------------------------------------------
-- São só uma SUGESTÃO inicial para ver a aba funcionando -- o coordenador
-- paroquial cadastra, renomeia e inativa o que quiser depois, em
-- Configurações → "Conhecimentos exigidos do catequista".
--
-- INSERT ... SELECT ... WHERE NOT EXISTS em vez de INSERT IGNORE: assim o
-- script pode ser rodado de novo com segurança mesmo depois de o coordenador
-- já ter renomeado ou inativado algum destes -- não recria o que já existe
-- (mesmo nome, ativo ou não).

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT * FROM (SELECT 'Kerigma' AS nome, TRUE AS ativo, 'script-migracao' AS criado_por, NOW() AS criado_em) AS novo
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = novo.nome);

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT * FROM (SELECT 'Artigos do Credo', TRUE, 'script-migracao', NOW()) AS novo
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = novo.nome);

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT * FROM (SELECT 'Pai Nosso', TRUE, 'script-migracao', NOW()) AS novo
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = novo.nome);

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT * FROM (SELECT '10 Mandamentos', TRUE, 'script-migracao', NOW()) AS novo
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = novo.nome);

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT * FROM (SELECT 'Cristologia', TRUE, 'script-migracao', NOW()) AS novo
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = novo.nome);

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT * FROM (SELECT 'Mariologia', TRUE, 'script-migracao', NOW()) AS novo
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = novo.nome);

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT * FROM (SELECT 'Mandamentos da Igreja', TRUE, 'script-migracao', NOW()) AS novo
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = novo.nome);

SELECT id_requisito, nome, ativo FROM tb_requisito_conhecimento ORDER BY id_requisito;


-- =====================================================
-- COMO DESFAZER (só as tabelas -- cuidado, apaga toda marcação já feita)
-- =====================================================
--   DROP TABLE IF EXISTS tb_requisito_conhecimento_marcado;
--   DROP TABLE IF EXISTS tb_requisito_conhecimento;
-- =====================================================
