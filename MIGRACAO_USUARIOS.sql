-- =====================================================
-- MIGRACAO_USUARIOS.sql
-- Etapa 1 do controle de acesso: tabela de usuarios do sistema.
--
-- Cria SOMENTE uma tabela nova. Nao altera nenhuma tabela existente.
-- tb_login e tb_permissoes ficam intactas (nunca foram usadas por codigo algum).
--
-- OBRIGATORIO rodar ANTES de subir o app com esta versao:
-- o projeto usa spring.jpa.hibernate.ddl-auto=validate, entao a aplicacao
-- NAO INICIA se a tabela nao existir.
--
-- Uso: mysql -u <user> -p -h <host> -P <porta> catequese < MIGRACAO_USUARIOS.sql
-- =====================================================

USE catequese;

CREATE TABLE IF NOT EXISTS tb_usuario (
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    -- Hash BCrypt gerado pela aplicacao (nunca a senha em texto puro).
    password_hash VARCHAR(255) NOT NULL,
    -- CATEQUISTA | COORDENADOR | COORDENADOR_PAROQUIAL
    tipo VARCHAR(40) NOT NULL,
    -- Vinculos opcionais, apenas para exibicao/relatorio.
    id_catequista BIGINT NULL,
    id_coordenador BIGINT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao DATETIME NULL,
    INDEX idx_usuario_username (username),
    INDEX idx_usuario_tipo (tipo),
    CONSTRAINT fk_usuario_catequista
        FOREIGN KEY (id_catequista) REFERENCES tb_catequista(id_catequista)
        ON DELETE SET NULL,
    CONSTRAINT fk_usuario_coordenador
        FOREIGN KEY (id_coordenador) REFERENCES tb_coordenador(id_coordenador)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- O primeiro administrador NAO e criado por SQL, porque a senha precisa ser
-- gravada como hash BCrypt. Quem cria e a propria aplicacao, na primeira subida,
-- se a tabela estiver vazia e estas variaveis estiverem no ambiente:
--
--   export ADMIN_INICIAL_USERNAME=coordenador
--   export ADMIN_INICIAL_PASSWORD='sua senha forte'
--   export ADMIN_INICIAL_NOME='Nome do Coordenador Paroquial'
--
-- Suba o app uma vez, confirme no log a mensagem de usuario criado e depois
-- remova essas variaveis do ambiente.
-- =====================================================

-- Conferencia
SELECT id_usuario, nome, username, tipo, ativo, data_criacao FROM tb_usuario;
