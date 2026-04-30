-- ================================================================
-- CREATE_AUTH_MISSING.sql
-- ================================================================
-- Objetivo: criar somente o que faltar para o login funcionar.
-- Seguro para reexecucao (usa IF NOT EXISTS + inserts idempotentes).
-- ================================================================

-- Tabela de usuarios
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

-- Tabela de roles
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

-- Tabela de reset de senha
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

-- Seed admin (idempotente)
INSERT INTO tb_usuario (nome, email, password_hash, ativo)
SELECT
    'Administrador',
    'admin@catequese.com',
    '$2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq',
    TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM tb_usuario WHERE email = 'admin@catequese.com'
);

INSERT INTO tb_usuario_role (id_usuario, role)
SELECT u.id_usuario, 'COORDENADOR_PAROQUIAL'
FROM tb_usuario u
WHERE u.email = 'admin@catequese.com'
  AND NOT EXISTS (
      SELECT 1
      FROM tb_usuario_role ur
      WHERE ur.id_usuario = u.id_usuario
        AND ur.role = 'COORDENADOR_PAROQUIAL'
  );

-- Verificacao rapida
SELECT 'OK - Estrutura minima de login criada/verificada' AS status;

