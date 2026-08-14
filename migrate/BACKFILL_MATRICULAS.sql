-- =====================================================
-- BACKFILL_MATRICULAS.sql
-- Cria as matriculas dos catequisandos que ja existem no banco.
--
-- POR QUE FICA SEPARADO DO MIGRACAO_USUARIOS.sql:
-- aquele arquivo so cria estrutura e nao toca em dado nenhum, o que permite
-- roda-lo em producao sem medo. Este aqui INSERE registros. Sao naturezas
-- diferentes e merecem decisoes diferentes.
--
-- SEGURANCA:
--   * Nao apaga nem altera nada: so INSERT.
--   * Pode rodar mais de uma vez -- o NOT EXISTS impede duplicar matricula.
--
-- QUANDO RODAR: uma vez, depois do MIGRACAO_USUARIOS.sql, para que os
-- catequisandos atuais passem a ter historico e frequencia.
--
-- CONFIRA ANTES: rode a consulta do PASSO 1 e veja se os numeros fazem sentido.
-- =====================================================

USE catequese;

-- =====================================================
-- PASSO 1: previa. Nao grava nada, so mostra o que seria criado.
-- =====================================================

SELECT
    c.id_catequisando,
    c.nome,
    t.nome AS turma,
    COALESCE(YEAR(f.data_inscricao), YEAR(CURDATE())) AS ano,
    f.data_inscricao AS data_matricula
FROM tb_catequisando c
JOIN tb_turma t ON t.id_turma = c.id_turma
LEFT JOIN (
    SELECT id_catequisando, MIN(data_inscricao) AS data_inscricao
      FROM tb_ficha_inscricao
     GROUP BY id_catequisando
) f ON f.id_catequisando = c.id_catequisando
WHERE c.id_turma IS NOT NULL
ORDER BY t.nome, c.nome;

-- Quantos ficariam sem data de matricula (sem ficha de inscricao)?
-- Para esses, a contagem de frequencia comeca no inicio do periodo.
SELECT COUNT(*) AS sem_data_de_matricula
  FROM tb_catequisando c
  LEFT JOIN tb_ficha_inscricao f ON f.id_catequisando = c.id_catequisando
 WHERE c.id_turma IS NOT NULL
   AND f.id_ficha IS NULL;

-- =====================================================
-- PASSO 2: a criacao em si.
-- O ano vem do ano da ficha de inscricao; sem ficha, usa o ano corrente.
-- =====================================================

INSERT INTO tb_matricula
    (id_catequisando, id_turma, ano, data_matricula, situacao, criado_em)
SELECT
    c.id_catequisando,
    c.id_turma,
    COALESCE(YEAR(f.data_inscricao), YEAR(CURDATE())),
    f.data_inscricao,
    'CURSANDO',
    NOW()
FROM tb_catequisando c
LEFT JOIN (
    SELECT id_catequisando, MIN(data_inscricao) AS data_inscricao
      FROM tb_ficha_inscricao
     GROUP BY id_catequisando
) f ON f.id_catequisando = c.id_catequisando
WHERE c.id_turma IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
        FROM tb_matricula m
       WHERE m.id_catequisando = c.id_catequisando
         AND m.id_turma = c.id_turma
         AND m.ano = COALESCE(YEAR(f.data_inscricao), YEAR(CURDATE()))
  );

-- =====================================================
-- PASSO 3: conferencia
-- =====================================================

SELECT COUNT(*) AS matriculas_existentes FROM tb_matricula;

SELECT t.nome AS turma, m.ano, COUNT(*) AS quantidade
  FROM tb_matricula m
  JOIN tb_turma t ON t.id_turma = m.id_turma
 GROUP BY t.nome, m.ano
 ORDER BY m.ano DESC, t.nome;

-- =====================================================
-- DEPOIS DISTO, no sistema:
-- classifique cada turma (categoria e etapa) pela tela de administracao.
-- Enquanto a categoria estiver vazia, a turma nao entra no controle de
-- frequencia -- de proposito, para nada ser avaliado com a regra errada.
-- =====================================================
