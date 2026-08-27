-- =====================================================
-- DADOS_TESTE_FORMACAO.sql
--
-- Cria uma formação de teste com encontros, inscritos e presenças, para dar
-- para ver a agenda funcionando enquanto a tela de Formação não existe.
--
-- SÓ PARA DESENVOLVIMENTO. Não rode em produção: insere dados fictícios.
-- Tem um bloco "COMO DESFAZER" no fim que apaga tudo o que este script criou.
--
-- Pré-requisito: MIGRACAO_AGENDA.sql já rodou (precisa de tb_formacao,
-- tb_formacao_inscrito, tb_presenca_formacao e das colunas novas de tb_evento).
--
-- Escrito para MariaDB. Rode o arquivo inteiro de uma vez (no DBeaver, Alt+X).
--
-- RODAR DUAS VEZES cria uma SEGUNDA formação de teste, com outros encontros.
-- Não quebra nada, mas polui a agenda -- se quiser recomeçar do zero, use o
-- bloco "COMO DESFAZER" do fim antes de rodar de novo.
-- =====================================================


-- -----------------------------------------------------
-- 0. AJUSTE AQUI ANTES DE RODAR
-- -----------------------------------------------------
-- Coloque o SEU login. É o que faz a coluna "Você: 67%" e o cartão "Atenção na
-- frequência" aparecerem para você -- a agenda calcula a frequência do
-- catequista ligado ao usuário logado, e não de um catequista qualquer.

-- 'admin' é o padrão criado pelo AdminBootstrap na primeira subida do app.
-- Se você entra com outro login, troque aqui.
SET @meu_login = 'admin';   -- <<< SEU LOGIN


-- Descobre o catequista ligado a esse usuário. Fica NULL se o usuário não tem
-- catequista vinculado -- o que é comum no admin, e está tratado mais abaixo.
SET @meu_catequista = (
  SELECT id_catequista FROM tb_usuario WHERE username = @meu_login LIMIT 1
);


-- -----------------------------------------------------
-- 1. Confira o terreno antes
-- -----------------------------------------------------
-- Rode estas três e olhe o resultado. Se `meu_catequista` vier NULL, veja a
-- observação no fim do arquivo antes de continuar.

SELECT @meu_login AS login_usado, @meu_catequista AS meu_catequista;
SELECT COUNT(*) AS catequistas_ativos FROM tb_catequista WHERE ativo = TRUE;
SELECT id_catequista, nome FROM tb_catequista WHERE ativo = TRUE ORDER BY id_catequista LIMIT 5;


-- -----------------------------------------------------
-- 2. A formação
-- -----------------------------------------------------

INSERT INTO tb_formacao
  (nome, nivel, ano, descricao, percentual_minimo, situacao, criado_por, criado_em)
VALUES
  ('Escola Diocesana de Catequese 2026', 'DIOCESANO', 2026,
   'Formação de teste criada por DADOS_TESTE_FORMACAO.sql.',
   80, 'ABERTA', 'script-teste', NOW());

SET @formacao = LAST_INSERT_ID();
SELECT @formacao AS id_formacao_criada;


-- -----------------------------------------------------
-- 3. Os encontros
-- -----------------------------------------------------
-- Quatro REALIZADOS e dois PREVISTOS de propósito: só encontro realizado entra
-- na conta dos 80%. Se todos fossem previstos, todo mundo apareceria com
-- "sem apuração" e não daria para testar a regra.
--
-- As datas são sábados espalhados por 2026, com um em agosto para o calendário
-- já abrir mostrando alguma coisa no mês corrente.

INSERT INTO tb_evento
  (titulo, tipo, nivel, id_formacao, descricao, data_inicio, hora_inicio, `local`,
   situacao, criado_por, criado_em)
VALUES
  ('Escola Diocesana — módulo 1', 'FORMACAO', 'DIOCESANO', @formacao,
   'Iniciação à vida cristã: fundamentos.', '2026-03-14', '8h às 12h', 'Cúria diocesana',
   'REALIZADO', 'script-teste', NOW()),

  ('Escola Diocesana — módulo 2', 'FORMACAO', 'DIOCESANO', @formacao,
   'Bíblia na catequese.', '2026-04-11', '8h às 12h', 'Cúria diocesana',
   'REALIZADO', 'script-teste', NOW()),

  ('Escola Diocesana — módulo 3', 'FORMACAO', 'DIOCESANO', @formacao,
   'Liturgia e ano litúrgico.', '2026-05-09', '8h às 12h', 'Cúria diocesana',
   'REALIZADO', 'script-teste', NOW()),

  ('Escola Diocesana — módulo 4', 'FORMACAO', 'DIOCESANO', @formacao,
   'Pedagogia da catequese.', '2026-06-13', '8h às 12h', 'Cúria diocesana',
   'REALIZADO', 'script-teste', NOW()),

  ('Escola Diocesana — módulo 5', 'FORMACAO', 'DIOCESANO', @formacao,
   'RICA e catecumenato.', '2026-09-12', '8h às 12h', 'Cúria diocesana',
   'PREVISTO', 'script-teste', NOW()),

  ('Escola Diocesana — módulo 6', 'FORMACAO', 'DIOCESANO', @formacao,
   'Envio e avaliação do ano.', '2026-10-10', '8h às 12h', 'Cúria diocesana',
   'PREVISTO', 'script-teste', NOW());


-- Guarda os ids dos realizados, que são os que recebem presença.
SET @enc1 = (SELECT id_evento FROM tb_evento WHERE id_formacao = @formacao AND data_inicio = '2026-03-14');
SET @enc2 = (SELECT id_evento FROM tb_evento WHERE id_formacao = @formacao AND data_inicio = '2026-04-11');
SET @enc3 = (SELECT id_evento FROM tb_evento WHERE id_formacao = @formacao AND data_inicio = '2026-05-09');
SET @enc4 = (SELECT id_evento FROM tb_evento WHERE id_formacao = @formacao AND data_inicio = '2026-06-13');


-- -----------------------------------------------------
-- 4. Um evento de cada tipo, só para ver a agenda cheia
-- -----------------------------------------------------
-- Assim dá para conferir as cinco cores de nível e os quatro ícones de tipo
-- na mesma tela, e testar o conflito (dois eventos no mesmo 15/08).

INSERT INTO tb_evento
  (titulo, tipo, nivel, descricao, data_inicio, hora_inicio, `local`, situacao,
   motivo_cancelamento, criado_por, criado_em)
VALUES
  ('Assembleia diocesana de catequistas', 'ENCONTRO', 'DIOCESANO',
   NULL, '2026-08-15', '9h', 'Catedral', 'PREVISTO', NULL, 'script-teste', NOW()),

  ('Encontro regional de coordenadores', 'ENCONTRO', 'REGIONAL',
   NULL, '2026-08-22', '8h', 'Curitiba', 'PREVISTO', NULL, 'script-teste', NOW()),

  ('Batismo de crianças', 'SACRAMENTO', 'PAROQUIAL',
   'Preparação de pais e padrinhos no sábado anterior.', '2026-09-05', '10h',
   'Matriz', 'PREVISTO', NULL, 'script-teste', NOW()),

  -- Mesmo dia da assembleia diocesana, de proposito: os dois sao de nivel
  -- paroquial-ou-acima, entao atingem todo mundo e o sistema deve acusar
  -- conflito quando voce tentar marcar um terceiro evento em 15/08.
  ('Missa de envio dos catequistas', 'ENCONTRO', 'PAROQUIAL',
   NULL, '2026-08-15', '19h', 'Matriz', 'PREVISTO', NULL, 'script-teste', NOW()),

  ('Passeio cancelado pela chuva', 'ENCONTRO', 'PAROQUIAL',
   NULL, '2026-07-18', NULL, 'Sítio', 'CANCELADO',
   'Chuva forte na estrada.', 'script-teste', NOW());


-- -----------------------------------------------------
-- 5. Os inscritos
-- -----------------------------------------------------
-- Inscrição é nominal: sem ela o percentual não significa nada, porque quem
-- entrou em setembro apareceria com 30% numa formação que correu o ano todo.
--
-- Inscreve até 5 catequistas ativos. O `IGNORE` cobre o caso de o script ser
-- rodado duas vezes: a UNIQUE (id_formacao, id_catequista) barra a repetição
-- em vez de dobrar o peso da pessoa na contagem.

INSERT IGNORE INTO tb_formacao_inscrito (id_formacao, id_catequista, inscrito_em)
SELECT @formacao, id_catequista, NOW()
  FROM (SELECT id_catequista FROM tb_catequista WHERE ativo = TRUE
         ORDER BY id_catequista LIMIT 5) AS escolhidos;

-- Garante que VOCÊ está inscrito, mesmo que seu catequista não esteja entre os
-- 5 primeiros -- senão a formação não apareceria com percentual na sua tela.
-- FROM DUAL e obrigatorio: no MariaDB um SELECT com WHERE precisa de FROM,
-- e "SELECT valor WHERE condicao" nao compila.
INSERT IGNORE INTO tb_formacao_inscrito (id_formacao, id_catequista, inscrito_em)
SELECT @formacao, @meu_catequista, NOW()
  FROM DUAL
 WHERE @meu_catequista IS NOT NULL;


-- Pega três inscritos para variar as presenças.
SET @cat1 = (SELECT id_catequista FROM tb_formacao_inscrito
              WHERE id_formacao = @formacao ORDER BY id_catequista LIMIT 1);
SET @cat2 = (SELECT id_catequista FROM tb_formacao_inscrito
              WHERE id_formacao = @formacao ORDER BY id_catequista LIMIT 1 OFFSET 1);
SET @cat3 = (SELECT id_catequista FROM tb_formacao_inscrito
              WHERE id_formacao = @formacao ORDER BY id_catequista LIMIT 1 OFFSET 2);


-- -----------------------------------------------------
-- 6. As presenças
-- -----------------------------------------------------
-- Perfis diferentes de propósito, para exercitar a regra:
--
--   VOCÊ  -> 3 presenças + 1 falta   = 75%  ABAIXO do mínimo (dispara o aviso)
--   cat1  -> 4 presenças             = 100% em dia
--   cat2  -> 2 presenças + 1 falta
--             + 1 justificada        = 67%  abaixo (justificada sai da conta)
--   cat3  -> sem marcação nenhuma    = 0%   (encontro realizado sem marcação
--                                     conta como falta -- senão bastaria não
--                                     fazer a chamada para todos ficarem 100%)

-- VOCÊ: 3 presenças e 1 falta -> 75%, abaixo dos 80%.
INSERT IGNORE INTO tb_presenca_formacao
  (id_evento, id_catequista, situacao, marcado_por, marcado_em)
SELECT e.id_evento, @meu_catequista, e.sit, 'script-teste', NOW()
  FROM (SELECT @enc1 AS id_evento, 'PRESENTE' AS sit
        UNION ALL SELECT @enc2, 'PRESENTE'
        UNION ALL SELECT @enc3, 'PRESENTE'
        UNION ALL SELECT @enc4, 'FALTA') AS e
 WHERE @meu_catequista IS NOT NULL;

-- cat1: presença em tudo -> 100%.
INSERT IGNORE INTO tb_presenca_formacao
  (id_evento, id_catequista, situacao, marcado_por, marcado_em)
SELECT e.id_evento, @cat1, 'PRESENTE', 'script-teste', NOW()
  FROM (SELECT @enc1 AS id_evento UNION ALL SELECT @enc2
        UNION ALL SELECT @enc3 UNION ALL SELECT @enc4) AS e
 WHERE @cat1 IS NOT NULL AND @cat1 <> IFNULL(@meu_catequista, -1);

-- cat2: 2 presenças, 1 falta, 1 justificada -> 2/(2+1) = 67%.
INSERT IGNORE INTO tb_presenca_formacao
  (id_evento, id_catequista, situacao, justificativa, marcado_por, marcado_em)
SELECT e.id_evento, @cat2, e.sit, e.motivo, 'script-teste', NOW()
  FROM (SELECT @enc1 AS id_evento, 'PRESENTE' AS sit, CAST(NULL AS CHAR(255)) AS motivo
        UNION ALL SELECT @enc2, 'PRESENTE', NULL
        UNION ALL SELECT @enc3, 'JUSTIFICADA', 'Internado no hospital.'
        UNION ALL SELECT @enc4, 'FALTA', NULL) AS e
 WHERE @cat2 IS NOT NULL AND @cat2 <> IFNULL(@meu_catequista, -1);

-- cat3 fica sem marcação de propósito: é o caso "realizado sem chamada".


-- -----------------------------------------------------
-- 7. Confira o resultado
-- -----------------------------------------------------

SELECT f.nome, f.nivel, f.ano, f.percentual_minimo, f.situacao,
       (SELECT COUNT(*) FROM tb_evento e WHERE e.id_formacao = f.id_formacao) AS encontros,
       (SELECT COUNT(*) FROM tb_evento e WHERE e.id_formacao = f.id_formacao
          AND e.situacao = 'REALIZADO') AS realizados,
       (SELECT COUNT(*) FROM tb_formacao_inscrito i WHERE i.id_formacao = f.id_formacao) AS inscritos
  FROM tb_formacao f
 WHERE f.id_formacao = @formacao;

-- O percentual de cada inscrito, pela mesma regra do FrequenciaFormacaoService:
-- justificada sai da conta, e realizado sem marcação conta como falta.
SELECT c.nome,
       SUM(CASE WHEN p.situacao = 'PRESENTE'    THEN 1 ELSE 0 END) AS presencas,
       SUM(CASE WHEN p.situacao = 'JUSTIFICADA' THEN 1 ELSE 0 END) AS justificadas,
       4 - SUM(CASE WHEN p.situacao IN ('PRESENTE','JUSTIFICADA') THEN 1 ELSE 0 END) AS faltas,
       ROUND(
         100 * SUM(CASE WHEN p.situacao = 'PRESENTE' THEN 1 ELSE 0 END)
         / NULLIF(4 - SUM(CASE WHEN p.situacao = 'JUSTIFICADA' THEN 1 ELSE 0 END), 0)
       ) AS percentual
  FROM tb_formacao_inscrito i
  JOIN tb_catequista c ON c.id_catequista = i.id_catequista
  LEFT JOIN tb_presenca_formacao p
         ON p.id_catequista = i.id_catequista
        AND p.id_evento IN (@enc1, @enc2, @enc3, @enc4)
 WHERE i.id_formacao = @formacao
 GROUP BY c.id_catequista, c.nome
 ORDER BY percentual;


-- =====================================================
-- SE `meu_catequista` VEIO NULL
-- =====================================================
-- Quer dizer que o seu usuário não está ligado a nenhum catequista, e a agenda
-- não vai mostrar percentual nenhum para você ("Você: 67%" some, e o cartão
-- "Atenção na frequência" não aparece). O resto continua funcionando.
--
-- Para ligar, escolha um catequista da lista do passo 1 e rode:
--
--   UPDATE tb_usuario SET id_catequista = <ID_DO_CATEQUISTA>
--    WHERE username = 'seu_login';
--
-- Depois SAIA E ENTRE DE NOVO no sistema (o vínculo entra no token) e rode
-- este script outra vez, ou só o passo 6.
--
-- Se não houver nenhum catequista cadastrado, crie um:
--
--   INSERT INTO tb_catequista (nome, ativo) VALUES ('Catequista de teste', TRUE);


-- =====================================================
-- COMO DESFAZER
-- =====================================================
-- Apaga exatamente o que este script criou -- nada mais. A marca é
-- criado_por = 'script-teste'.
--
--   DELETE FROM tb_presenca_formacao WHERE marcado_por = 'script-teste';
--   DELETE FROM tb_formacao_inscrito
--    WHERE id_formacao IN (SELECT id_formacao FROM tb_formacao WHERE criado_por = 'script-teste');
--   DELETE FROM tb_evento WHERE criado_por = 'script-teste';
--   DELETE FROM tb_formacao WHERE criado_por = 'script-teste';
--
-- Nesta ordem: presenças e inscritos apontam para eventos e formação, então
-- apagar a formação primeiro deixaria registros órfãos.
-- =====================================================
