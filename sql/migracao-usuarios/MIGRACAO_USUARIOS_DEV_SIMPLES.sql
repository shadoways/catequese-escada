-- =====================================================
-- MIGRACAO_USUARIOS_DEV_SIMPLES.sql
--
-- Versao simplificada, so para BANCO DE DESENVOLVIMENTO LOCAL. Nao usa
-- procedure nem DELIMITER -- cada linha e uma instrucao independente, entao
-- nao tem como o DBeaver (ou qualquer cliente) juntar instrucoes erradas.
--
-- Por que existe: os scripts MIGRACAO_USUARIOS.sql / MIGRACAO_USUARIOS_DBEAVER.sql
-- usam procedures para funcionar tanto em MySQL quanto em MariaDB (o MySQL
-- nao aceita "ADD COLUMN IF NOT EXISTS"). Mas a mensagem de erro que voce
-- recebeu no DBeaver confirma que este banco E MariaDB -- e o MariaDB aceita
-- "IF NOT EXISTS" direto em ADD COLUMN e ADD INDEX. Entao, so para o seu
-- ambiente de dev, da para pular a complexidade toda das procedures.
--
-- NAO use este arquivo em producao -- ele nao faz a checagem de "coluna
-- unica tem duplicado?" antes de criar indice unico, que o script original
-- faz por seguranca. Para producao, use MIGRACAO_USUARIOS.sql.
--
-- SEGURO PARA RODAR VARIAS VEZES (idempotente):
--   * So usa CREATE TABLE IF NOT EXISTS e ADD COLUMN/INDEX IF NOT EXISTS.
--   * NUNCA faz DROP, DELETE ou UPDATE de dado.
--
-- COMO USAR NO DBEAVER: selecione tudo (Ctrl+A) e rode com
-- "Execute SQL Script" (Alt+X). Como nao ha bloco BEGIN...END nem
-- DELIMITER, funciona igual mesmo se vier a rodar so um pedaco selecionado.
--
-- Ao final, a ultima consulta mostra o que a aplicacao ainda espera e nao
-- existe. Vazio = pronto para subir.
-- =====================================================

USE catequese;

-- =====================================================
-- TABELAS (so cria se nao existirem)
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_usuario (
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    email VARCHAR(255) NULL,
    telefone VARCHAR(40) NULL,
    senha_provisoria BOOLEAN NOT NULL DEFAULT FALSE,
    data_troca_senha DATETIME NULL,
    ultimo_login DATETIME NULL,
    tentativas_falhas INT NOT NULL DEFAULT 0,
    bloqueado_ate DATETIME NULL,
    id_catequista BIGINT NULL,
    id_coordenador BIGINT NULL,
    id_comunidade BIGINT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_token_recuperacao (
    id_token BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expira_em DATETIME NOT NULL,
    usado_em DATETIME NULL,
    criado_em DATETIME NOT NULL,
    ip_solicitante VARCHAR(45) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_chave_inscricao (
    id_chave BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(40) NOT NULL,
    descricao VARCHAR(255) NULL,
    expira_em DATETIME NOT NULL,
    limite_usos INT NULL,
    usos INT NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por VARCHAR(255) NULL,
    criado_em DATETIME NULL,
    revogada_em DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_configuracao (
    chave VARCHAR(80) PRIMARY KEY,
    valor VARCHAR(255) NOT NULL,
    descricao VARCHAR(255) NULL,
    atualizado_em DATETIME NULL,
    atualizado_por VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_matricula (
    id_matricula BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_catequisando BIGINT NOT NULL,
    id_turma BIGINT NOT NULL,
    ano INT NOT NULL,
    data_matricula DATE NULL,
    situacao VARCHAR(30) NOT NULL DEFAULT 'CURSANDO',
    observacao VARCHAR(500) NULL,
    criado_em DATETIME NULL,
    atualizado_em DATETIME NULL,
    atualizado_por VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_encontro (
    id_encontro BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_turma BIGINT NOT NULL,
    data DATE NOT NULL,
    tema VARCHAR(255) NULL,
    situacao VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    motivo_cancelamento VARCHAR(500) NULL,
    id_evento BIGINT NULL,
    aberto_por VARCHAR(255) NULL,
    aberto_em DATETIME NULL,
    fechado_por VARCHAR(255) NULL,
    fechado_em DATETIME NULL,
    fechamento_automatico BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_etapa_catecumeno (
    id_etapa BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_catequisando BIGINT NOT NULL,
    etapa VARCHAR(40) NOT NULL,
    data_inicio DATE NULL,
    data_fim DATE NULL,
    observacao VARCHAR(500) NULL,
    registrado_por VARCHAR(255) NULL,
    registrado_em DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_turma_catequista (
    id_turma_catequista BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_turma BIGINT NOT NULL,
    id_catequista BIGINT NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- COLUNAS (cobre tabela recem-criada e tabela antiga/incompleta)
-- =====================================================

ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS nome VARCHAR(255) NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS username VARCHAR(255) NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS tipo VARCHAR(40) NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS email VARCHAR(255) NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS telefone VARCHAR(40) NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS senha_provisoria BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS data_troca_senha DATETIME NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS ultimo_login DATETIME NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS tentativas_falhas INT NOT NULL DEFAULT 0;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS bloqueado_ate DATETIME NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS id_catequista BIGINT NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS id_coordenador BIGINT NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS id_comunidade BIGINT NULL;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS data_criacao DATETIME NULL;

ALTER TABLE tb_token_recuperacao ADD COLUMN IF NOT EXISTS id_usuario BIGINT NULL;
ALTER TABLE tb_token_recuperacao ADD COLUMN IF NOT EXISTS token_hash VARCHAR(64) NULL;
ALTER TABLE tb_token_recuperacao ADD COLUMN IF NOT EXISTS expira_em DATETIME NULL;
ALTER TABLE tb_token_recuperacao ADD COLUMN IF NOT EXISTS usado_em DATETIME NULL;
ALTER TABLE tb_token_recuperacao ADD COLUMN IF NOT EXISTS criado_em DATETIME NULL;
ALTER TABLE tb_token_recuperacao ADD COLUMN IF NOT EXISTS ip_solicitante VARCHAR(45) NULL;

-- Categoria e etapa da turma decidem a regra de frequencia.
-- PRE_CATEQUESE | EUCARISTIA | CRISMA | ADULTOS | CATECUMENATO | PERSEVERANCA
ALTER TABLE tb_turma ADD COLUMN IF NOT EXISTS categoria VARCHAR(40) NULL;
-- 1 = primeiro ano (Crisma I), 2 = segundo ano (Crisma II).
ALTER TABLE tb_turma ADD COLUMN IF NOT EXISTS etapa INT NULL;

-- Presenca: repetido aqui por seguranca -- ADD COLUMN IF NOT EXISTS nao
-- reclama se a coluna ja existir, entao roda de novo sem problema.
ALTER TABLE tb_presenca ADD COLUMN IF NOT EXISTS id_encontro BIGINT NULL;
ALTER TABLE tb_presenca ADD COLUMN IF NOT EXISTS situacao VARCHAR(20) NULL;
ALTER TABLE tb_presenca ADD COLUMN IF NOT EXISTS justificativa VARCHAR(500) NULL;
ALTER TABLE tb_presenca ADD COLUMN IF NOT EXISTS marcado_por VARCHAR(255) NULL;
ALTER TABLE tb_presenca ADD COLUMN IF NOT EXISTS marcado_em DATETIME NULL;

ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS codigo VARCHAR(40) NULL;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS descricao VARCHAR(255) NULL;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS expira_em DATETIME NULL;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS limite_usos INT NULL;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS usos INT NOT NULL DEFAULT 0;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS criado_por VARCHAR(255) NULL;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS criado_em DATETIME NULL;
ALTER TABLE tb_chave_inscricao ADD COLUMN IF NOT EXISTS revogada_em DATETIME NULL;

ALTER TABLE tb_configuracao ADD COLUMN IF NOT EXISTS valor VARCHAR(255) NULL;
ALTER TABLE tb_configuracao ADD COLUMN IF NOT EXISTS descricao VARCHAR(255) NULL;
ALTER TABLE tb_configuracao ADD COLUMN IF NOT EXISTS atualizado_em DATETIME NULL;
ALTER TABLE tb_configuracao ADD COLUMN IF NOT EXISTS atualizado_por VARCHAR(255) NULL;

-- =====================================================
-- INDICES
-- Sem a checagem de duplicados que o script de producao faz -- ok para dev,
-- onde a tabela normalmente esta vazia ou com poucos dados de teste.
-- =====================================================

ALTER TABLE tb_usuario ADD UNIQUE INDEX IF NOT EXISTS uk_usuario_username (username);
ALTER TABLE tb_usuario ADD INDEX IF NOT EXISTS idx_usuario_tipo (tipo);
ALTER TABLE tb_usuario ADD INDEX IF NOT EXISTS idx_usuario_email (email);

ALTER TABLE tb_chave_inscricao ADD UNIQUE INDEX IF NOT EXISTS uk_chave_codigo (codigo);

ALTER TABLE tb_matricula ADD INDEX IF NOT EXISTS idx_matricula_catequisando (id_catequisando);
ALTER TABLE tb_encontro ADD INDEX IF NOT EXISTS idx_encontro_turma_data (id_turma, data);
ALTER TABLE tb_presenca ADD INDEX IF NOT EXISTS idx_presenca_encontro (id_encontro);

ALTER TABLE tb_token_recuperacao ADD INDEX IF NOT EXISTS idx_token_hash (token_hash);
ALTER TABLE tb_token_recuperacao ADD INDEX IF NOT EXISTS idx_token_usuario (id_usuario);

-- =====================================================
-- RELATORIO FINAL
-- Lista tudo o que a aplicacao espera e ainda nao existe. Vazio = pronto.
-- =====================================================

SELECT esperada.tabela, esperada.coluna AS coluna_faltando
  FROM (
        SELECT 'tb_usuario' AS tabela, 'id_usuario' AS coluna
  UNION ALL SELECT 'tb_usuario', 'nome'
  UNION ALL SELECT 'tb_usuario', 'username'
  UNION ALL SELECT 'tb_usuario', 'password_hash'
  UNION ALL SELECT 'tb_usuario', 'tipo'
  UNION ALL SELECT 'tb_usuario', 'email'
  UNION ALL SELECT 'tb_usuario', 'telefone'
  UNION ALL SELECT 'tb_usuario', 'senha_provisoria'
  UNION ALL SELECT 'tb_usuario', 'data_troca_senha'
  UNION ALL SELECT 'tb_usuario', 'ultimo_login'
  UNION ALL SELECT 'tb_usuario', 'tentativas_falhas'
  UNION ALL SELECT 'tb_usuario', 'bloqueado_ate'
  UNION ALL SELECT 'tb_usuario', 'id_catequista'
  UNION ALL SELECT 'tb_usuario', 'id_coordenador'
  UNION ALL SELECT 'tb_usuario', 'id_comunidade'
  UNION ALL SELECT 'tb_usuario', 'ativo'
  UNION ALL SELECT 'tb_usuario', 'data_criacao'
  UNION ALL SELECT 'tb_token_recuperacao', 'id_token'
  UNION ALL SELECT 'tb_token_recuperacao', 'id_usuario'
  UNION ALL SELECT 'tb_token_recuperacao', 'token_hash'
  UNION ALL SELECT 'tb_token_recuperacao', 'expira_em'
  UNION ALL SELECT 'tb_token_recuperacao', 'usado_em'
  UNION ALL SELECT 'tb_token_recuperacao', 'criado_em'
  UNION ALL SELECT 'tb_token_recuperacao', 'ip_solicitante'
  UNION ALL SELECT 'tb_turma', 'categoria'
  UNION ALL SELECT 'tb_turma', 'etapa'
  UNION ALL SELECT 'tb_presenca', 'id_encontro'
  UNION ALL SELECT 'tb_presenca', 'situacao'
  UNION ALL SELECT 'tb_presenca', 'justificativa'
  UNION ALL SELECT 'tb_presenca', 'marcado_por'
  UNION ALL SELECT 'tb_presenca', 'marcado_em'
  UNION ALL SELECT 'tb_matricula', 'id_matricula'
  UNION ALL SELECT 'tb_matricula', 'id_catequisando'
  UNION ALL SELECT 'tb_matricula', 'id_turma'
  UNION ALL SELECT 'tb_matricula', 'ano'
  UNION ALL SELECT 'tb_matricula', 'data_matricula'
  UNION ALL SELECT 'tb_matricula', 'situacao'
  UNION ALL SELECT 'tb_matricula', 'observacao'
  UNION ALL SELECT 'tb_matricula', 'criado_em'
  UNION ALL SELECT 'tb_matricula', 'atualizado_em'
  UNION ALL SELECT 'tb_matricula', 'atualizado_por'
  UNION ALL SELECT 'tb_encontro', 'id_encontro'
  UNION ALL SELECT 'tb_encontro', 'id_turma'
  UNION ALL SELECT 'tb_encontro', 'data'
  UNION ALL SELECT 'tb_encontro', 'tema'
  UNION ALL SELECT 'tb_encontro', 'situacao'
  UNION ALL SELECT 'tb_encontro', 'motivo_cancelamento'
  UNION ALL SELECT 'tb_encontro', 'id_evento'
  UNION ALL SELECT 'tb_encontro', 'aberto_por'
  UNION ALL SELECT 'tb_encontro', 'aberto_em'
  UNION ALL SELECT 'tb_encontro', 'fechado_por'
  UNION ALL SELECT 'tb_encontro', 'fechado_em'
  UNION ALL SELECT 'tb_encontro', 'fechamento_automatico'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'id_etapa'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'id_catequisando'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'etapa'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'data_inicio'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'data_fim'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'observacao'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'registrado_por'
  UNION ALL SELECT 'tb_etapa_catecumeno', 'registrado_em'
  UNION ALL SELECT 'tb_turma_catequista', 'id_turma_catequista'
  UNION ALL SELECT 'tb_turma_catequista', 'id_turma'
  UNION ALL SELECT 'tb_turma_catequista', 'id_catequista'
  UNION ALL SELECT 'tb_turma_catequista', 'principal'
  UNION ALL SELECT 'tb_turma_catequista', 'criado_em'
  UNION ALL SELECT 'tb_chave_inscricao', 'id_chave'
  UNION ALL SELECT 'tb_chave_inscricao', 'codigo'
  UNION ALL SELECT 'tb_chave_inscricao', 'descricao'
  UNION ALL SELECT 'tb_chave_inscricao', 'expira_em'
  UNION ALL SELECT 'tb_chave_inscricao', 'limite_usos'
  UNION ALL SELECT 'tb_chave_inscricao', 'usos'
  UNION ALL SELECT 'tb_chave_inscricao', 'ativo'
  UNION ALL SELECT 'tb_chave_inscricao', 'criado_por'
  UNION ALL SELECT 'tb_chave_inscricao', 'criado_em'
  UNION ALL SELECT 'tb_chave_inscricao', 'revogada_em'
  UNION ALL SELECT 'tb_configuracao', 'chave'
  UNION ALL SELECT 'tb_configuracao', 'valor'
  UNION ALL SELECT 'tb_configuracao', 'descricao'
  UNION ALL SELECT 'tb_configuracao', 'atualizado_em'
  UNION ALL SELECT 'tb_configuracao', 'atualizado_por'
       ) esperada
  LEFT JOIN information_schema.COLUMNS c
         ON c.TABLE_SCHEMA = DATABASE()
        AND c.TABLE_NAME   = esperada.tabela
        AND c.COLUMN_NAME  = esperada.coluna
 WHERE c.COLUMN_NAME IS NULL;
