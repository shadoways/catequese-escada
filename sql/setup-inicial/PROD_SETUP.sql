-- =====================================================
-- PROD_SETUP.sql
-- Script para criar todas as tabelas e inserir dados iniciais
-- Gerado a partir dos mapeamentos JPA do projeto
-- Uso: execute no banco (ex.: mysql -u user -p database < PROD_SETUP.sql)
-- OBS: revise as credenciais/ambiente antes de rodar em produção
-- =====================================================

USE catequese;

-- Opcional: desabilitar checagens de FK durante criação (garante ordem independente)
SET FOREIGN_KEY_CHECKS=0;

-- =====================================================
-- 1. TABELAS INDEPENDENTES (sem foreign keys)
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_catequista (
    id_catequista BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(255),
    email VARCHAR(255),
    endereco VARCHAR(500),
    data_nascimento DATE,
    data_inicio DATE,
    ativo BOOLEAN DEFAULT TRUE,
    INDEX idx_nome (nome),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_comunidade (
    id_comunidade BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL UNIQUE,
    descricao TEXT,
    ativo BOOLEAN DEFAULT TRUE,
    INDEX idx_nome (nome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_coordenador (
    id_coordenador BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(255),
    email VARCHAR(255),
    nivel_organizacional VARCHAR(255),
    data_nascimento DATE,
    data_inicio DATE,
    ativo BOOLEAN DEFAULT TRUE,
    INDEX idx_nome (nome),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_evento (
    id_evento BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    nivel VARCHAR(255),
    publico_alvo VARCHAR(255),
    descricao TEXT,
    data_inicio DATE,
    data_fim DATE,
    local VARCHAR(500),
    INDEX idx_titulo (titulo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 2. TABELAS COM DEPENDÊNCIA SIMPLES
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_turma (
    id_turma BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    descricao TEXT,
    ano INT,
    nivel VARCHAR(255),
    id_catequista BIGINT,
    FOREIGN KEY (id_catequista) REFERENCES tb_catequista(id_catequista) ON DELETE SET NULL,
    INDEX idx_nome (nome),
    INDEX idx_id_catequista (id_catequista)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_login (
    id_login BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(500) NOT NULL,
    id_catequista BIGINT,
    FOREIGN KEY (id_catequista) REFERENCES tb_catequista(id_catequista) ON DELETE CASCADE,
    INDEX idx_username (username),
    INDEX idx_id_catequista (id_catequista)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_conhecimento_catequista (
    id_conhecimento BIGINT AUTO_INCREMENT PRIMARY KEY,
    area_conhecimento VARCHAR(255),
    nivel VARCHAR(255),
    descricao TEXT,
    id_catequista BIGINT,
    FOREIGN KEY (id_catequista) REFERENCES tb_catequista(id_catequista) ON DELETE CASCADE,
    INDEX idx_id_catequista (id_catequista)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 3. TABELA PRINCIPAL: tb_catequisando
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_catequisando (
    id_catequisando BIGINT AUTO_INCREMENT PRIMARY KEY,

    nome VARCHAR(255) NOT NULL,
    telefone VARCHAR(255),
    email VARCHAR(255),
    data_nascimento DATE,

    nome_responsavel VARCHAR(255),
    telefone_responsavel VARCHAR(255),

    endereco VARCHAR(500),
    numero_documento VARCHAR(50),
    tipo_documento VARCHAR(50),

    intolerante_gluten BOOLEAN DEFAULT FALSE,
    foi_batizado BOOLEAN DEFAULT FALSE,
    fez_primeira_eucaristia BOOLEAN DEFAULT FALSE,

    estado_conjugal VARCHAR(100),

    ativo BOOLEAN DEFAULT TRUE,

    id_turma BIGINT,
    id_comunidade BIGINT,

    INDEX idx_nome (nome),
    INDEX idx_email (email),
    INDEX idx_id_turma (id_turma),
    INDEX idx_id_comunidade (id_comunidade),

    FOREIGN KEY (id_turma) REFERENCES tb_turma(id_turma) ON DELETE SET NULL,
    FOREIGN KEY (id_comunidade) REFERENCES tb_comunidade(id_comunidade) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 4. TABELAS QUE DEPENDEM DE tb_catequisando
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_presenca (
    id_presenca BIGINT AUTO_INCREMENT PRIMARY KEY,
    data DATE,
    presente BOOLEAN DEFAULT FALSE,
    id_catequisando BIGINT NOT NULL,
    FOREIGN KEY (id_catequisando) REFERENCES tb_catequisando(id_catequisando) ON DELETE CASCADE,
    INDEX idx_id_catequisando (id_catequisando),
    INDEX idx_data (data)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_ficha_inscricao (
    id_ficha BIGINT AUTO_INCREMENT PRIMARY KEY,
    data_inscricao DATE,
    observacoes TEXT,
    id_catequisando BIGINT NOT NULL,
    FOREIGN KEY (id_catequisando) REFERENCES tb_catequisando(id_catequisando) ON DELETE CASCADE,
    INDEX idx_id_catequisando (id_catequisando),
    INDEX idx_data_inscricao (data_inscricao)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tb_documento (
    id_documento BIGINT AUTO_INCREMENT PRIMARY KEY,
    tipo_documento VARCHAR(255),
    caminho_arquivo VARCHAR(500),
    data_envio DATE,
    id_catequisando BIGINT NOT NULL,
    FOREIGN KEY (id_catequisando) REFERENCES tb_catequisando(id_catequisando) ON DELETE CASCADE,
    INDEX idx_id_catequisando (id_catequisando),
    INDEX idx_data_envio (data_envio)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- 5. TABELAS COM DEPENDÊNCIA DUPLA
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_permissoes (
    id_permissao BIGINT AUTO_INCREMENT PRIMARY KEY,
    permissao VARCHAR(255),
    id_login BIGINT NOT NULL,
    FOREIGN KEY (id_login) REFERENCES tb_login(id_login) ON DELETE CASCADE,
    INDEX idx_id_login (id_login),
    INDEX idx_permissao (permissao)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- DADOS INICIAIS (INSERTS)
-- =====================================================

-- Inserir comunidades padrão
INSERT IGNORE INTO tb_comunidade (nome, descricao, ativo) VALUES
('Matriz', 'Matriz', TRUE),
('Nsa. Dores', 'Comunidade Nsa. das Dores', TRUE),
('Nsa. Esperança', 'Comunidade Nsa. da Esperança', TRUE),
('Nsa. Aparecida', 'Comunidade Nsa. Aparecida', TRUE),
('São José', 'Comunidade São José', TRUE),
('Santo Antônio', 'Comunidade Santo Antônio', TRUE);

-- Inserir turmas padrão
INSERT IGNORE INTO tb_turma (nome, descricao, ano, nivel, id_catequista) VALUES
('Pré Catequese', 'Turma da Pré catequese', NULL, NULL, NULL),
('Primeira Eucaristia I', 'Turma primeira Eucaristia 1', NULL, NULL, NULL),
('Crisma I', 'Turma crisma 1', NULL, NULL, NULL),
('Adultos', 'Catequese de adultos', NULL, NULL, NULL),
('Catecumenato', 'Catecumenato', NULL, NULL, NULL);

-- =====================================================
-- VERIFICAÇÕES FINAIS
-- =====================================================

SET FOREIGN_KEY_CHECKS=1;

-- Mostrar tabelas criadas
SHOW TABLES;

-- Mostrar estrutura da tabela principal
DESC tb_catequisando;

-- Contar comunidades criadas
SELECT COUNT(*) as total_comunidades FROM tb_comunidade;

-- Listar foreign keys configuradas (verificação)
SELECT
    TABLE_NAME,
    COLUMN_NAME,
    CONSTRAINT_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE()
AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME;

-- =====================================================
-- FIM
-- =====================================================

