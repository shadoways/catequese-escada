-- =====================================================================
-- LIMPA FASE EM TURMA QUE NAO TEM FASE
-- =====================================================================
--
-- Nao acrescenta coluna nenhuma -- e conserto de dado, nao de estrutura.
--
-- Motivo: antes de `RegrasDeMovimentacao.temFases` existir, a tela de
-- classificacao oferecia "1o ano / 2o ano" para TODA turma, Adultos e
-- Catecumenato inclusive -- categorias que nunca tiveram fase. Turma
-- classificada naquela epoca pode ter guardado um `etapa` que hoje nao
-- significa nada.
--
-- Isso nao ficou so bonito de corrigir: quebrava a transferencia entre
-- comunidades. A regra "mesmo percurso, outra comunidade" comparava a fase
-- crua, e duas turmas de Adultos com `etapa` diferente (uma com 1, a outra
-- com 2 ou nula) pareciam PERCURSOS DIFERENTES -- a transferencia entre elas,
-- que deveria ser o caso normal, sumia da lista sem nenhum aviso. O codigo ja
-- foi corrigido para ignorar `etapa` fora de Eucaristia e Crisma
-- (RegrasDeMovimentacao.mesmoPercurso), mas o dado que ja esta gravado
-- continua lá ate alguem limpar -- e e mais simples limpar do que carregar
-- pra sempre um campo que a tela nem mostra mais.
--
-- Seguro para producao: so zera um campo que a tela de edicao ja nao exibe
-- para estas categorias (ver admDesenharFase em admin-catequese.js), e a
-- classificacao mais recente ja impede o campo de ser gravado de novo.
--
-- Rode no DBeaver como SCRIPT (Alt+X).
-- =====================================================================


-- ---------------------------------------------------------------------
-- 0. Conferencia ANTES: quais turmas tem fase gravada fora de lugar
-- ---------------------------------------------------------------------
SELECT id_turma, nome, categoria, etapa
  FROM tb_turma
 WHERE categoria IS NOT NULL
   AND categoria NOT IN ('EUCARISTIA', 'CRISMA')
   AND etapa IS NOT NULL;


-- ---------------------------------------------------------------------
-- 1. A limpeza
-- ---------------------------------------------------------------------
UPDATE tb_turma
   SET etapa = NULL
 WHERE categoria IS NOT NULL
   AND categoria NOT IN ('EUCARISTIA', 'CRISMA')
   AND etapa IS NOT NULL;

-- Turma SEM categoria fica de fora de proposito: `etapa` sem categoria ja nao
-- significa nada em nenhuma regra, e mexer nela aqui seria arrumar uma coisa
-- que ninguem perguntou. Quem classifica a turma zera o campo sozinho, pela
-- mesma regra que este script aplica.


-- =====================================================================
-- CONFERENCIA DEPOIS
-- =====================================================================
--   SELECT id_turma, nome, categoria, etapa
--     FROM tb_turma
--    WHERE categoria NOT IN ('EUCARISTIA', 'CRISMA') AND etapa IS NOT NULL;
--
--   -- deve vir vazio. Se nao vier, alguma categoria nova ganhou fase e este
--   -- script (ou RegrasDeMovimentacao.temFases) ficou desatualizado.
-- =====================================================================


-- =====================================================================
-- COMO DESFAZER
-- =====================================================================
-- Nao tem volta: o valor apagado nao significava nada em nenhuma regra do
-- sistema (nenhuma tela mostra "fase" para quem nao e Eucaristia ou Crisma),
-- entao nao ha um valor "certo" para restaurar.
-- =====================================================================
