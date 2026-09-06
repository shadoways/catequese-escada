-- =====================================================
-- MIGRACAO_CONHECIMENTOS_EXIGIDOS.sql
--
-- Catalogo de conhecimentos que a paroquia exige do catequista (Kerigma,
-- Artigos do Credo, Pai Nosso...) e a marca de quem ja tem cada um --
-- tela-catequistas.md, aba "Conhecimentos".
--
-- Duas tabelas novas, sem relacao com `tb_conhecimento_catequista` (que ja
-- existia, nao e usada por nenhuma tela, e guarda area/nivel/descricao em
-- texto livre POR catequista, sem catalogo comum) -- ver a KDoc de
-- RequisitoConhecimento.kt para o porque do nome diferente.
--
-- SEGURO EM PRODUCAO: so cria estrutura nova e insere os 7 itens de exemplo
-- pedidos pelo Gabriel (so se ainda nao existirem, pelo nome). Nao mexe em
-- nenhuma tabela existente. Dados de teste (marcacoes ficticias de "possui"
-- em catequistas reais) NAO estao aqui de proposito -- ver
-- DADOS_TESTE_CONHECIMENTOS.sql, que so deve rodar em desenvolvimento.
--
-- Escrito para MariaDB. Rode o arquivo inteiro de uma vez.
-- =====================================================

-- -----------------------------------------------------
-- 1. tb_requisito_conhecimento: o catalogo
-- -----------------------------------------------------
-- `ativo` em vez de apagar a linha: "tirar" um conhecimento da exigencia
-- (o coordenador quer pedir menos) nao pode apagar as marcacoes ja feitas
-- de quem tinha esse conhecimento -- regra do projeto, nada e apagado de
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
-- 2. tb_requisito_conhecimento_marcado: quem tem o que
-- -----------------------------------------------------
-- Uma linha por par catequista+requisito -- marcar de novo ATUALIZA a linha
-- (a UNIQUE impede duplicar), nao insere outra. Sem FOREIGN KEY para
-- catequista/requisito, mesmo padrao ja usado em tb_formacao_inscrito e
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
-- Sao so uma SUGESTAO inicial para ver a aba funcionando -- o coordenador
-- paroquial cadastra, renomeia e inativa o que quiser depois, em
-- Configuracoes -> "Conhecimentos exigidos do catequista".
--
-- INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS em vez de INSERT IGNORE:
-- assim o script pode ser rodado de novo com seguranca mesmo depois de o
-- coordenador ja ter renomeado ou inativado algum destes -- nao recria o que
-- ja existe (mesmo nome, ativo ou nao).
--
-- FROM DUAL (nao uma subconsulta derivada com alias) de proposito: o MariaDB
-- deste servidor rejeita com "Unknown column 'novo.nome'" um
-- "SELECT * FROM (SELECT 'x' AS nome) AS novo WHERE NOT EXISTS (... = novo.nome)"
-- -- o otimizador funde a subconsulta de uma linha so antes de resolver o
-- nome, e o alias some. Comparar a string literal direto no WHERE evita a
-- subconsulta derivada e o problema.

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT 'Kerigma', TRUE, 'script-migracao', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = 'Kerigma');

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT 'Artigos do Credo', TRUE, 'script-migracao', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = 'Artigos do Credo');

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT 'Pai Nosso', TRUE, 'script-migracao', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = 'Pai Nosso');

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT '10 Mandamentos', TRUE, 'script-migracao', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = '10 Mandamentos');

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT 'Cristologia', TRUE, 'script-migracao', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = 'Cristologia');

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT 'Mariologia', TRUE, 'script-migracao', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = 'Mariologia');

INSERT INTO tb_requisito_conhecimento (nome, ativo, criado_por, criado_em)
SELECT 'Mandamentos da Igreja', TRUE, 'script-migracao', NOW() FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM tb_requisito_conhecimento WHERE nome = 'Mandamentos da Igreja');


-- =====================================================
-- CONFERENCIA
-- =====================================================
-- Confirma que as duas tabelas existem com as colunas certas e que os 7
-- itens de exemplo estao no catalogo.

SHOW COLUMNS FROM tb_requisito_conhecimento;
SHOW COLUMNS FROM tb_requisito_conhecimento_marcado;

SELECT id_requisito, nome, ativo FROM tb_requisito_conhecimento ORDER BY id_requisito;


-- =====================================================
-- COMO DESFAZER (so as tabelas -- cuidado, apaga toda marcacao ja feita)
-- =====================================================
--   DROP TABLE IF EXISTS tb_requisito_conhecimento_marcado;
--   DROP TABLE IF EXISTS tb_requisito_conhecimento;
-- =====================================================
