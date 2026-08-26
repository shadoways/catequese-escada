-- =====================================================
-- MIGRACAO_AGENDA.sql
-- Agenda da catequese: eventos com nivel e tipo, trilhas de formacao de
-- catequistas e presenca de catequista.
--
-- Escrito para MariaDB (usa ADD COLUMN IF NOT EXISTS nativo). Para MySQL 8,
-- que nao aceita IF NOT EXISTS em ALTER TABLE, use o mesmo padrao de
-- procedure de MIGRACAO_USUARIOS.sql.
--
-- SEGURO EM PRODUCAO: so cria estrutura e converte o texto livre que ja
-- existia em tb_evento.nivel. Nao apaga nem sobrescreve dado nenhum.
--
-- Rode uma vez, na ordem em que esta.
-- =====================================================

-- -----------------------------------------------------
-- 1. tb_evento: os campos que faltavam
-- -----------------------------------------------------
-- `nivel` ja existia como VARCHAR livre. A coluna e mantida e convertida no
-- passo 2 -- por isso aqui nao se mexe nela.

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'ENCONTRO';

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS id_comunidade BIGINT NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS id_turma BIGINT NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS id_formacao BIGINT NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS hora_inicio VARCHAR(10) NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS situacao VARCHAR(20) NOT NULL DEFAULT 'PREVISTO';

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS motivo_cancelamento VARCHAR(255) NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS criado_por VARCHAR(100) NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS criado_em DATETIME NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS alterado_por VARCHAR(100) NULL;

ALTER TABLE tb_evento
  ADD COLUMN IF NOT EXISTS alterado_em DATETIME NULL;

-- A agenda sempre lista por periodo; sem indice em data_inicio a consulta do
-- ano vira varredura da tabela inteira.
ALTER TABLE tb_evento
  ADD INDEX IF NOT EXISTS idx_evento_data_inicio (data_inicio);

ALTER TABLE tb_evento
  ADD INDEX IF NOT EXISTS idx_evento_formacao (id_formacao);


-- -----------------------------------------------------
-- 2. Converter o `nivel` que era texto livre
-- -----------------------------------------------------
-- O campo aceitava qualquer coisa. Converte-se o que da para reconhecer e o
-- resto fica NULL, que a tela mostra como "sem nivel definido" -- de proposito:
-- chutar PAROQUIAL para um texto desconhecido daria ao registro um dono que
-- ele nunca teve, e dono e justamente o que decide quem pode alterar.
--
-- Confira o que sobrou antes de rodar o UPDATE:
--   SELECT DISTINCT nivel FROM tb_evento;

UPDATE tb_evento
   SET nivel = CASE
     WHEN UPPER(TRIM(nivel)) IN ('DIOCESANO', 'DIOCESANA', 'DIOCESE') THEN 'DIOCESANO'
     WHEN UPPER(TRIM(nivel)) IN ('REGIONAL')                          THEN 'REGIONAL'
     WHEN UPPER(TRIM(nivel)) IN ('PAROQUIAL', 'PAROQUIA', 'PARÓQUIA') THEN 'PAROQUIAL'
     WHEN UPPER(TRIM(nivel)) IN ('COMUNIDADE', 'COMUNITARIO')         THEN 'COMUNIDADE'
     WHEN UPPER(TRIM(nivel)) IN ('TURMA')                             THEN 'TURMA'
     ELSE NULL
   END
 WHERE nivel IS NOT NULL;


-- -----------------------------------------------------
-- 3. tb_formacao: a trilha de formacao de catequistas
-- -----------------------------------------------------
-- percentual_minimo e coluna, e nao constante no codigo: o minimo varia por
-- diocese (Divinopolis exige 80%, Santo Andre 75%).

CREATE TABLE IF NOT EXISTS tb_formacao (
  id_formacao        BIGINT       NOT NULL AUTO_INCREMENT,
  nome               VARCHAR(150) NOT NULL,
  nivel              VARCHAR(20)  NOT NULL DEFAULT 'PAROQUIAL',
  ano                INT          NULL,
  descricao          VARCHAR(500) NULL,
  percentual_minimo  INT          NOT NULL DEFAULT 80,
  situacao           VARCHAR(20)  NOT NULL DEFAULT 'ABERTA',
  criado_por         VARCHAR(100) NULL,
  criado_em          DATETIME     NULL,
  PRIMARY KEY (id_formacao),
  INDEX idx_formacao_ano (ano)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------
-- 4. tb_formacao_inscrito: quem esta matriculado na trilha
-- -----------------------------------------------------
-- A inscricao e nominal para que o percentual signifique alguma coisa. Sem
-- ela, quem comecou a atuar em setembro apareceria com 30% numa formacao que
-- correu o ano todo -- o numero acusaria falta que nunca houve.
--
-- A UNIQUE impede inscricao duplicada, que dobraria o peso da pessoa na
-- contagem de "em dia" e "abaixo do minimo".

CREATE TABLE IF NOT EXISTS tb_formacao_inscrito (
  id_formacao_inscrito BIGINT   NOT NULL AUTO_INCREMENT,
  id_formacao          BIGINT   NOT NULL,
  id_catequista        BIGINT   NOT NULL,
  inscrito_em          DATETIME NULL,
  PRIMARY KEY (id_formacao_inscrito),
  UNIQUE KEY uk_formacao_catequista (id_formacao, id_catequista),
  INDEX idx_finscrito_formacao (id_formacao),
  INDEX idx_finscrito_catequista (id_catequista)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------
-- 5. tb_presenca_formacao: presenca do CATEQUISTA
-- -----------------------------------------------------
-- Tabela propria em vez de reaproveitar tb_presenca: aquela liga catequisando
-- a matricula e nao teria como registrar catequista sem distorcer o modelo.
-- Alem disso as duas contam de formas diferentes: esta apura os 80% da
-- formacao, e a de la apura a frequencia da turma.
--
-- A UNIQUE e a rede de seguranca do refaz-chamada: sem ela, um apaga-e-insere
-- que saisse fora de ordem deixaria a pessoa marcada duas vezes no mesmo
-- encontro e a frequencia contaria em dobro.

CREATE TABLE IF NOT EXISTS tb_presenca_formacao (
  id_presenca_formacao BIGINT       NOT NULL AUTO_INCREMENT,
  id_evento            BIGINT       NOT NULL,
  id_catequista        BIGINT       NOT NULL,
  situacao             VARCHAR(20)  NOT NULL DEFAULT 'PRESENTE',
  justificativa        VARCHAR(255) NULL,
  marcado_por          VARCHAR(100) NULL,
  marcado_em           DATETIME     NULL,
  PRIMARY KEY (id_presenca_formacao),
  UNIQUE KEY uk_presenca_formacao (id_evento, id_catequista),
  INDEX idx_presformacao_evento (id_evento),
  INDEX idx_presformacao_catequista (id_catequista)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- -----------------------------------------------------
-- 6. tb_turma.id_comunidade: o destravamento da permissao
-- -----------------------------------------------------
-- Antes disso a comunidade da turma era deduzida da comunidade dos
-- catequisandos matriculados. Isso fazia turma vazia nao pertencer a lugar
-- nenhum, e turma com gente de duas comunidades aparecer para dois
-- coordenadores. Fica NULL nas turmas existentes: o coordenador paroquial
-- classifica uma vez pela tela, e ate la a turma se comporta como antes.

ALTER TABLE tb_turma
  ADD COLUMN IF NOT EXISTS id_comunidade BIGINT NULL;

ALTER TABLE tb_turma
  ADD INDEX IF NOT EXISTS idx_turma_comunidade (id_comunidade);


-- =====================================================
-- CONFERENCIA
-- =====================================================
-- Rode depois e confira que nao sobrou nada:
--
--   SHOW COLUMNS FROM tb_evento;
--   SHOW COLUMNS FROM tb_turma LIKE 'id_comunidade';
--   SELECT DISTINCT nivel FROM tb_evento;          -- so os 5 enums ou NULL
--   SELECT COUNT(*) FROM tb_formacao;              -- 0 e o esperado
--
-- Depois de subir a aplicacao, o passo pendente e humano, nao de SQL:
--   1. Tela de Usuarios: definir a comunidade de cada coordenador.
--      Coordenador sem comunidade NAO consegue cadastrar evento de comunidade.
--   2. Tela de Turmas: definir a comunidade de cada turma.
-- =====================================================
