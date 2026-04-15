-- ================================================================
-- SISTEMA DE AUTENTICAÇÃO E CONTROLE DE ACESSO
-- ================================================================
-- Criação das tabelas para gestão de usuários, roles e reset de senha
-- Data: 2026-03-03
-- ================================================================

-- ================================================================
-- 1. TABELA DE USUÁRIOS
-- ================================================================
CREATE TABLE IF NOT EXISTS tb_usuario (
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    ultimo_login DATETIME NULL,
    id_comunidade BIGINT NULL,
    id_catequista BIGINT NULL,
    data_criacao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_atualizacao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_usuario_comunidade
        FOREIGN KEY (id_comunidade)
        REFERENCES tb_comunidade(id_comunidade)
        ON DELETE SET NULL,

    CONSTRAINT fk_usuario_catequista
        FOREIGN KEY (id_catequista)
        REFERENCES tb_catequista(id_catequista)
        ON DELETE SET NULL,

    INDEX idx_usuario_email (email),
    INDEX idx_usuario_ativo (ativo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================
-- 2. TABELA DE ROLES (PERMISSÕES)
-- ================================================================
CREATE TABLE IF NOT EXISTS tb_usuario_role (
    id_usuario_role BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario BIGINT NOT NULL,
    role VARCHAR(50) NOT NULL,
    data_criacao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_usuario_role_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES tb_usuario(id_usuario)
        ON DELETE CASCADE,

    CONSTRAINT chk_role_type
        CHECK (role IN ('COORDENADOR_PAROQUIAL', 'COORDENADOR_COMUNIDADE', 'CATEQUISTA')),

    INDEX idx_usuario_role_usuario (id_usuario),
    INDEX idx_usuario_role_role (role),
    UNIQUE KEY uk_usuario_role (id_usuario, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================
-- 3. TABELA DE TOKENS DE RESET DE SENHA
-- ================================================================
CREATE TABLE IF NOT EXISTS tb_password_reset_token (
    id_token BIGINT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    id_usuario BIGINT NOT NULL,
    data_expiracao DATETIME NOT NULL,
    usado BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reset_token_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES tb_usuario(id_usuario)
        ON DELETE CASCADE,

    INDEX idx_reset_token (token),
    INDEX idx_reset_token_usuario (id_usuario),
    INDEX idx_reset_token_expiracao (data_expiracao)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ================================================================
-- 4. DADOS INICIAIS
-- ================================================================

-- Inserir usuário administrador padrão
-- Senha: admin123 (deve ser alterada no primeiro acesso!)
INSERT INTO tb_usuario (nome, email, password_hash, ativo)
VALUES (
    'Administrador',
    'admin@catequese.com',
    '$2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq', -- senha: admin123
    TRUE
);

-- Adicionar role de coordenador paroquial ao admin
INSERT INTO tb_usuario_role (id_usuario, role)
VALUES (
    (SELECT id_usuario FROM tb_usuario WHERE email = 'admin@catequese.com'),
    'COORDENADOR_PAROQUIAL'
);

-- ================================================================
-- 5. COMENTÁRIOS DAS TABELAS
-- ================================================================

ALTER TABLE tb_usuario
    COMMENT = 'Tabela de usuários do sistema com credenciais e informações de acesso';

ALTER TABLE tb_usuario_role
    COMMENT = 'Tabela de roles (permissões) dos usuários - relacionamento N:N';

ALTER TABLE tb_password_reset_token
    COMMENT = 'Tabela de tokens para recuperação de senha - válidos por 24h';

-- ================================================================
-- 6. DESCRIÇÃO DAS ROLES
-- ================================================================

-- COORDENADOR_PAROQUIAL:
--   - Acesso total ao sistema
--   - Gerencia todas as comunidades
--   - Gerencia todos os catequistas
--   - Gerencia usuários e permissões

-- COORDENADOR_COMUNIDADE:
--   - Acesso à sua comunidade específica
--   - Visualiza catequistas da comunidade
--   - Gerencia catequisandos da comunidade

-- CATEQUISTA:
--   - Acesso às suas turmas
--   - Gerencia presença dos alunos
--   - Visualiza dados dos catequisandos

-- ================================================================
-- FIM DO SCRIPT
-- ================================================================


