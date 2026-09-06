-- =====================================================
-- DADOS_TESTE_CONHECIMENTOS.sql
--
-- Marca alguns catequistas reais como "possuindo" ou "nao possuindo" os
-- conhecimentos do catalogo, para dar para ver a aba "Conhecimentos" de
-- tela-catequistas.md com uma massa minima de teste, em vez de todo mundo
-- aparecendo com a lista inteira desmarcada.
--
-- SO PARA DESENVOLVIMENTO. Nao rode em producao: marca "possui"/"nao possui"
-- de forma ficticia em catequistas de verdade. Tem um bloco "COMO DESFAZER"
-- no fim que apaga so o que este script criou.
--
-- Pre-requisito: MIGRACAO_CONHECIMENTOS_EXIGIDOS.sql ja rodou (precisa dos 7
-- itens de exemplo em tb_requisito_conhecimento).
--
-- Escrito para MariaDB. Rode o arquivo inteiro de uma vez (no DBeaver, Alt+X).
--
-- RODAR DUAS VEZES nao duplica nada -- a UNIQUE (id_requisito, id_catequista)
-- e o INSERT IGNORE cobrem isso, mesmo padrao de DADOS_TESTE_FORMACAO.sql.
-- =====================================================


-- -----------------------------------------------------
-- 1. Confira o terreno antes
-- -----------------------------------------------------
-- Rode estas duas e olhe o resultado. Precisa de pelo menos 3 catequistas
-- ativos e dos 7 itens do catalogo ja inseridos pela migracao.

SELECT COUNT(*) AS catequistas_ativos FROM tb_catequista WHERE ativo = TRUE;
SELECT id_requisito, nome FROM tb_requisito_conhecimento ORDER BY id_requisito;


-- -----------------------------------------------------
-- 2. Escolhe tres catequistas ativos para os tres perfis
-- -----------------------------------------------------
-- Perfis diferentes de proposito, para exercitar cada ramo da tela:
--
--   catA -> possui TODOS os 7 conhecimentos (checklist inteira marcada)
--   catB -> mix: possui alguns, explicitamente NAO possui outros, e fica
--           sem marcacao nenhuma nos restantes (testa os tres estados que a
--           tela deve distinguir: possui / nao possui / ainda nao avaliado)
--   catC -> sem marcacao nenhuma (testa o catequista recem-cadastrado, cuja
--           checklist inteira ainda esta "ainda nao avaliado")

SET @catA = (SELECT id_catequista FROM tb_catequista WHERE ativo = TRUE ORDER BY id_catequista LIMIT 1);
SET @catB = (SELECT id_catequista FROM tb_catequista WHERE ativo = TRUE ORDER BY id_catequista LIMIT 1 OFFSET 1);
SET @catC = (SELECT id_catequista FROM tb_catequista WHERE ativo = TRUE ORDER BY id_catequista LIMIT 1 OFFSET 2);

SELECT @catA AS catA, @catB AS catB, @catC AS catC;


-- -----------------------------------------------------
-- 3. catA: possui todos os 7
-- -----------------------------------------------------

INSERT IGNORE INTO tb_requisito_conhecimento_marcado
  (id_requisito, id_catequista, possui, marcado_por, marcado_em)
SELECT r.id_requisito, @catA, TRUE, 'script-teste', NOW()
  FROM tb_requisito_conhecimento r
 WHERE @catA IS NOT NULL;


-- -----------------------------------------------------
-- 4. catB: mix (possui alguns, nao possui outros, resto sem marcacao)
-- -----------------------------------------------------
-- Possui: Kerigma, Pai Nosso, 10 Mandamentos.
-- Nao possui (marcado explicitamente, para distinguir de "nao avaliado"):
--   Cristologia, Mariologia.
-- Fica sem nenhuma linha (ainda nao avaliado): Artigos do Credo,
-- Mandamentos da Igreja.

INSERT IGNORE INTO tb_requisito_conhecimento_marcado
  (id_requisito, id_catequista, possui, marcado_por, marcado_em)
SELECT r.id_requisito, @catB, TRUE, 'script-teste', NOW()
  FROM tb_requisito_conhecimento r
 WHERE @catB IS NOT NULL
   AND r.nome IN ('Kerigma', 'Pai Nosso', '10 Mandamentos');

INSERT IGNORE INTO tb_requisito_conhecimento_marcado
  (id_requisito, id_catequista, possui, marcado_por, marcado_em)
SELECT r.id_requisito, @catB, FALSE, 'script-teste', NOW()
  FROM tb_requisito_conhecimento r
 WHERE @catB IS NOT NULL
   AND r.nome IN ('Cristologia', 'Mariologia');

-- catC fica sem marcacao nenhuma de proposito: e o caso "checklist toda
-- ainda nao avaliada".


-- -----------------------------------------------------
-- 5. Confira o resultado
-- -----------------------------------------------------

SELECT c.nome AS catequista, r.nome AS conhecimento, m.possui
  FROM tb_requisito_conhecimento_marcado m
  JOIN tb_catequista c ON c.id_catequista = m.id_catequista
  JOIN tb_requisito_conhecimento r ON r.id_requisito = m.id_requisito
 WHERE m.marcado_por = 'script-teste'
 ORDER BY c.nome, r.id_requisito;


-- =====================================================
-- COMO DESFAZER
-- =====================================================
-- Apaga exatamente o que este script criou -- nada mais. A marca e
-- marcado_por = 'script-teste'. Nao apaga as tabelas nem o catalogo, so as
-- marcacoes de teste.
--
--   DELETE FROM tb_requisito_conhecimento_marcado WHERE marcado_por = 'script-teste';
-- =====================================================
