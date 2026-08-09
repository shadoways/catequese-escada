-- =====================================================
-- MIGRACAO_USUARIOS.sql
-- Controle de acesso: tabela de usuarios do sistema.
--
-- Cria SOMENTE tabelas novas. Nao altera nenhuma tabela existente.
-- tb_login e tb_permissoes ficam intactas (nunca foram usadas por codigo algum).
--
-- OBRIGATORIO rodar ANTES de subir o app com esta versao:
-- o projeto usa spring.jpa.hibernate.ddl-auto=validate, entao a aplicacao
-- NAO INICIA se a tabela nao existir ou se faltar alguma coluna.
--
-- Uso: mysql -u <user> -p -h <host> -P <porta> catequese < MIGRACAO_USUARIOS.sql
-- =====================================================

USE catequese;

-- =====================================================
-- CASO A) Banco ainda NAO tem tb_usuario -> este bloco resolve tudo.
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_usuario (
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    -- Hash BCrypt gerado pela aplicacao (nunca a senha em texto puro).
    password_hash VARCHAR(255) NOT NULL,
    -- CATEQUISTA | COORDENADOR | COORDENADOR_PAROQUIAL
    tipo VARCHAR(40) NOT NULL,
    -- Usado na recuperacao de senha por e-mail.
    email VARCHAR(255) NULL,
    -- Reservado para um futuro envio por SMS.
    telefone VARCHAR(40) NULL,
    -- Enquanto TRUE o usuario so consegue trocar a propria senha.
    senha_provisoria BOOLEAN NOT NULL DEFAULT FALSE,
    -- Vai dentro do JWT: token emitido antes da troca deixa de valer.
    data_troca_senha DATETIME NULL,
    ultimo_login DATETIME NULL,
    -- Bloqueio anti forca-bruta.
    tentativas_falhas INT NOT NULL DEFAULT 0,
    bloqueado_ate DATETIME NULL,
    -- Vinculos opcionais, apenas para exibicao/relatorio.
    id_catequista BIGINT NULL,
    id_coordenador BIGINT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao DATETIME NULL,
    INDEX idx_usuario_username (username),
    INDEX idx_usuario_tipo (tipo),
    INDEX idx_usuario_email (email),
    CONSTRAINT fk_usuario_catequista
        FOREIGN KEY (id_catequista) REFERENCES tb_catequista(id_catequista)
        ON DELETE SET NULL,
    CONSTRAINT fk_usuario_coordenador
        FOREIGN KEY (id_coordenador) REFERENCES tb_coordenador(id_coordenador)
        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- CASO B) Voce JA rodou a versao anterior deste arquivo (tabela sem as colunas
-- novas). Rode SOMENTE os ALTERs abaixo, um a um.
-- O MySQL nao aceita "ADD COLUMN IF NOT EXISTS", entao se a coluna ja existir
-- ele acusa erro -- e so ignorar esse erro especifico e seguir.
-- Confira antes com:  DESC tb_usuario;
-- =====================================================

-- ALTER TABLE tb_usuario ADD COLUMN email VARCHAR(255) NULL;
-- ALTER TABLE tb_usuario ADD COLUMN telefone VARCHAR(40) NULL;
-- ALTER TABLE tb_usuario ADD COLUMN senha_provisoria BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE tb_usuario ADD COLUMN data_troca_senha DATETIME NULL;
-- ALTER TABLE tb_usuario ADD COLUMN ultimo_login DATETIME NULL;
-- ALTER TABLE tb_usuario ADD COLUMN tentativas_falhas INT NOT NULL DEFAULT 0;
-- ALTER TABLE tb_usuario ADD COLUMN bloqueado_ate DATETIME NULL;
-- ALTER TABLE tb_usuario ADD INDEX idx_usuario_email (email);

-- =====================================================
-- O primeiro administrador NAO e criado por SQL, porque a senha precisa ser
-- gravada como hash BCrypt. Quem cria e a propria aplicacao, na primeira subida,
-- se tb_usuario estiver vazia.
--
-- Sem configurar nada, ele cria o usuario "admin" com uma senha aleatoria e
-- imprime essa senha UMA UNICA VEZ no log da subida. A troca e exigida no
-- primeiro login.
--
-- Para escolher os dados do admin, defina antes de subir:
--   export ADMIN_INICIAL_USERNAME=coordenador
--   export ADMIN_INICIAL_NOME='Nome do Coordenador Paroquial'
--   export ADMIN_INICIAL_EMAIL=coordenador@paroquia.org
--   export ADMIN_INICIAL_PASSWORD='sua senha'    # opcional; sem isso o sistema gera
-- =====================================================

-- Conferencia
DESC tb_usuario;
SELECT id_usuario, nome, username, tipo, email, senha_provisoria, ativo FROM tb_usuario;
