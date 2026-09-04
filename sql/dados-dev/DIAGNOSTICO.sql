-- =====================================================================
-- DIAGNOSTICO ANTES DA MASSA
-- =====================================================================
--
-- Rode ESTE arquivo primeiro, no DBeaver, como script (Alt+X), e me mande o
-- resultado das cinco consultas. Ele nao escreve nada -- so olha.
--
-- Existe para responder tres perguntas que decidem se MASSA_DEV.sql roda:
--
--   1. As tabelas que a massa preenche existem todas? Se a migracao da agenda
--      (sql/agenda/MIGRACAO_AGENDA.sql) ainda nao rodou, tb_formacao,
--      tb_formacao_inscrito e tb_presenca_formacao nao existem -- e o script
--      morre em "Table doesn't exist".
--   2. As COLUNAS novas existem? tb_evento.tipo, tb_evento.id_formacao e
--      tb_turma.id_comunidade tambem vem daquela migracao. Sem elas o erro e
--      "Unknown column ... in 'field list'", que derruba o INSERT inteiro.
--   3. Quais comunidades ja estao cadastradas? tb_comunidade.nome e UNIQUE,
--      e a massa agora reaproveita a que ja existe em vez de tentar criar
--      outra igual.
-- =====================================================================


-- 1. Tabelas que a massa preenche. Toda linha tem de aparecer.
SELECT table_name AS tabela, table_rows AS linhas_aprox
  FROM information_schema.tables
 WHERE table_schema = DATABASE()
   AND table_name IN ('tb_comunidade','tb_catequista','tb_turma','tb_turma_catequista',
                      'tb_catequisando','tb_matricula','tb_encontro','tb_presenca',
                      'tb_etapa_catecumeno','tb_formacao','tb_formacao_inscrito',
                      'tb_evento','tb_presenca_formacao')
 ORDER BY table_name;


-- 2. As que FALTAM (esta consulta tem de vir vazia).
SELECT esperada AS tabela_faltando FROM (
    SELECT 'tb_comunidade' AS esperada UNION ALL SELECT 'tb_catequista'
    UNION ALL SELECT 'tb_turma'          UNION ALL SELECT 'tb_turma_catequista'
    UNION ALL SELECT 'tb_catequisando'   UNION ALL SELECT 'tb_matricula'
    UNION ALL SELECT 'tb_encontro'       UNION ALL SELECT 'tb_presenca'
    UNION ALL SELECT 'tb_etapa_catecumeno' UNION ALL SELECT 'tb_formacao'
    UNION ALL SELECT 'tb_formacao_inscrito' UNION ALL SELECT 'tb_evento'
    UNION ALL SELECT 'tb_presenca_formacao'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name = t.esperada
);


-- 3. Colunas que a massa escreve e que vieram de migracoes posteriores.
--    Tem de aparecer TODAS as 12 linhas.
SELECT table_name AS tabela, column_name AS coluna
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND (
        (table_name = 'tb_evento' AND column_name IN
            ('tipo','id_comunidade','id_turma','id_formacao','hora_inicio',
             'situacao','motivo_cancelamento','criado_por','criado_em'))
     OR (table_name = 'tb_turma' AND column_name IN ('categoria','etapa','id_comunidade'))
     OR (table_name = 'tb_presenca' AND column_name IN ('id_encontro','situacao','justificativa'))
   )
 ORDER BY table_name, column_name;


-- 4. Comunidades ja cadastradas. A massa reaproveita as que baterem pelo nome.
SELECT id_comunidade, nome, ativo FROM tb_comunidade ORDER BY nome;


-- 5. O que ja existe nas tabelas principais, e se ha algo na faixa da massa
--    (id >= 900000). Se houver, foi de uma execucao anterior.
SELECT 'tb_catequista' AS tabela, COUNT(*) AS total,
       SUM(id_catequista >= 900000) AS na_faixa_da_massa FROM tb_catequista
UNION ALL SELECT 'tb_turma',        COUNT(*), SUM(id_turma        >= 900000) FROM tb_turma
UNION ALL SELECT 'tb_catequisando', COUNT(*), SUM(id_catequisando >= 900000) FROM tb_catequisando
UNION ALL SELECT 'tb_matricula',    COUNT(*), SUM(id_matricula    >= 900000) FROM tb_matricula
UNION ALL SELECT 'tb_encontro',     COUNT(*), SUM(id_encontro     >= 900000) FROM tb_encontro
UNION ALL SELECT 'tb_presenca',     COUNT(*), SUM(id_presenca     >= 900000) FROM tb_presenca
UNION ALL SELECT 'tb_evento',       COUNT(*), SUM(id_evento       >= 900000) FROM tb_evento;


-- =====================================================================
-- E O ERRO EM SI
-- =====================================================================
-- No DBeaver, o erro completo fica em Window > Show View > Error Log, ou no
-- balao vermelho da aba de resultados. Me mande a PRIMEIRA mensagem inteira --
-- ela nomeia a tabela, a coluna e a restricao, e e o que fecha o diagnostico.
--
-- Vale conferir tambem, na conexao do DBeaver:
--   Preferences > Editors > SQL Editor > SQL Processing
--   "Error handling" = Stop at error  (o padrao) faz o script parar na
--   primeira falha; "Ignore" faz ele seguir. Saber qual estava ligado explica
--   se as 37 linhas foram o comeco do script ou sobras espalhadas.
-- =====================================================================
