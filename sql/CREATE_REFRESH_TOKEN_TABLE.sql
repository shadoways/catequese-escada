-- ================================================================
-- TABELA DE REFRESH TOKEN (OPCAO A)
-- ================================================================
-- Armazena refresh tokens (hash) para rotação e revogação de sessão
-- ================================================================

CREATE TABLE IF NOT EXISTS tb_refresh_token (
    id_refresh_token BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    data_expiracao DATETIME NOT NULL,
    revogado BOOLEAN NOT NULL DEFAULT FALSE,
    data_criacao DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_revogacao DATETIME NULL,

    CONSTRAINT fk_refresh_token_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES tb_usuario(id_usuario)
        ON DELETE CASCADE,

    UNIQUE KEY uk_refresh_token_hash (token_hash),
    INDEX idx_refresh_token_usuario (id_usuario),
    INDEX idx_refresh_token_expiracao (data_expiracao),
    INDEX idx_refresh_token_revogado (revogado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

