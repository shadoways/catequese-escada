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
-- COMO USAR: rode o arquivo INTEIRO, sempre.
-- Ele e idempotente: cria o que falta e ignora o que ja existe, tanto em banco
-- novo quanto em banco que ja recebeu uma versao anterior deste script.
--   mysql -u <user> -p -h <host> -P <porta> catequese < MIGRACAO_USUARIOS.sql
-- =====================================================

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

-- Tokens de "esqueci minha senha".
-- O banco guarda apenas o SHA-256 do token, nunca o valor enviado por e-mail:
-- quem ler esta tabela nao consegue redefinir a senha de ninguem.
CREATE TABLE IF NOT EXISTS tb_token_recuperacao (
    id_token BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario BIGINT NOT NULL,
    -- SHA-256 em hexadecimal = 64 caracteres.
    token_hash VARCHAR(64) NOT NULL,
    expira_em DATETIME NOT NULL,
    -- Preenchido no momento do uso: o token e de uso unico.
    usado_em DATETIME NULL,
    criado_em DATETIME NOT NULL,
    -- Apenas para investigar abuso.
    ip_solicitante VARCHAR(45) NULL,
    INDEX idx_token_hash (token_hash),
    INDEX idx_token_usuario (id_usuario),
    CONSTRAINT fk_token_usuario
        FOREIGN KEY (id_usuario) REFERENCES tb_usuario(id_usuario)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Configuracoes do sistema, no formato chave/valor.
-- Hoje guarda so o interruptor do cadastro publico. Chave/valor evita uma
-- migracao de banco a cada opcao nova que aparecer.
CREATE TABLE IF NOT EXISTS tb_configuracao (
    chave VARCHAR(80) PRIMARY KEY,
    valor VARCHAR(255) NOT NULL,
    descricao VARCHAR(255) NULL,
    atualizado_em DATETIME NULL,
    -- Username de quem mexeu por ultimo, para saber a quem perguntar.
    atualizado_por VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- NAO e preciso inserir nada: sem a linha, o sistema considera o cadastro
-- ABERTO, que e exatamente como ele sempre funcionou. A linha aparece sozinha
-- na primeira vez que o coordenador paroquial usar o interruptor.

-- =====================================================
-- CASO B) Voce JA rodou a versao anterior deste arquivo (tabela sem as colunas
-- novas). O bloco abaixo resolve isso sozinho: ele confere no catalogo do
-- banco e so cria o que estiver faltando.
--
-- Pode rodar quantas vezes quiser, em banco novo ou antigo, sem dar erro.
-- O MySQL nao aceita "ADD COLUMN IF NOT EXISTS" (o MariaDB aceita), por isso a
-- checagem e feita via information_schema, que funciona nos dois.
-- =====================================================

DROP PROCEDURE IF EXISTS cria_coluna_se_faltar;
DROP PROCEDURE IF EXISTS cria_indice_se_faltar;

DELIMITER $$

CREATE PROCEDURE cria_coluna_se_faltar(
    IN p_tabela VARCHAR(64),
    IN p_coluna VARCHAR(64),
    IN p_definicao TEXT
)
BEGIN
    DECLARE v_quantas INT DEFAULT 0;

    SELECT COUNT(*) INTO v_quantas
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabela
       AND COLUMN_NAME = p_coluna;

    IF v_quantas = 0 THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_tabela, ' ADD COLUMN ', p_coluna, ' ', p_definicao);
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

CREATE PROCEDURE cria_indice_se_faltar(
    IN p_tabela VARCHAR(64),
    IN p_indice VARCHAR(64),
    IN p_colunas TEXT
)
BEGIN
    DECLARE v_quantas INT DEFAULT 0;

    SELECT COUNT(*) INTO v_quantas
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabela
       AND INDEX_NAME = p_indice;

    IF v_quantas = 0 THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_tabela, ' ADD INDEX ', p_indice, ' (', p_colunas, ')');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;

-- Colunas que entraram depois da primeira versao da tb_usuario.
CALL cria_coluna_se_faltar('tb_usuario', 'email',             'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'telefone',          'VARCHAR(40) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'senha_provisoria',  'BOOLEAN NOT NULL DEFAULT FALSE');
CALL cria_coluna_se_faltar('tb_usuario', 'data_troca_senha',  'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'ultimo_login',      'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'tentativas_falhas', 'INT NOT NULL DEFAULT 0');
CALL cria_coluna_se_faltar('tb_usuario', 'bloqueado_ate',     'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'id_catequista',     'BIGINT NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'id_coordenador',    'BIGINT NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'data_criacao',      'DATETIME NULL');

CALL cria_indice_se_faltar('tb_usuario', 'idx_usuario_email', 'email');
CALL cria_indice_se_faltar('tb_usuario', 'idx_usuario_tipo',  'tipo');

DROP PROCEDURE IF EXISTS cria_coluna_se_faltar;
DROP PROCEDURE IF EXISTS cria_indice_se_faltar;

-- ---------------------------------------------------------------------------
-- PLANO B, se o bloco acima nao rodar no seu ambiente.
--
-- Dois motivos possiveis: o cliente usado nao entende a instrucao DELIMITER
-- (comum em interfaces graficas), ou o usuario do banco nao tem permissao para
-- criar procedure (pode acontecer em banco gerenciado).
--
-- Nesse caso rode os ALTERs abaixo um a um. Se a coluna ja existir, o banco
-- responde "Duplicate column name" -- e so ignorar ESSE erro e seguir.
--
-- ALTER TABLE tb_usuario ADD COLUMN email VARCHAR(255) NULL;
-- ALTER TABLE tb_usuario ADD COLUMN telefone VARCHAR(40) NULL;
-- ALTER TABLE tb_usuario ADD COLUMN senha_provisoria BOOLEAN NOT NULL DEFAULT FALSE;
-- ALTER TABLE tb_usuario ADD COLUMN data_troca_senha DATETIME NULL;
-- ALTER TABLE tb_usuario ADD COLUMN ultimo_login DATETIME NULL;
-- ALTER TABLE tb_usuario ADD COLUMN tentativas_falhas INT NOT NULL DEFAULT 0;
-- ALTER TABLE tb_usuario ADD COLUMN bloqueado_ate DATETIME NULL;
-- ALTER TABLE tb_usuario ADD COLUMN id_catequista BIGINT NULL;
-- ALTER TABLE tb_usuario ADD COLUMN id_coordenador BIGINT NULL;
-- ALTER TABLE tb_usuario ADD COLUMN data_criacao DATETIME NULL;
-- ---------------------------------------------------------------------------

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

-- =====================================================
-- Configuracao do envio de e-mail (recuperacao de senha).
-- Nao e obrigatorio: sem SMTP o app sobe igual e o administrador ainda
-- consegue gerar senha provisoria para qualquer usuario.
--
--   export SPRING_MAIL_HOST=smtp.seuprovedor.com
--   export SPRING_MAIL_PORT=587
--   export SPRING_MAIL_USERNAME=usuario
--   export SPRING_MAIL_PASSWORD='senha do smtp'
--   export APP_EMAIL_REMETENTE='Catequese Admin <nao-responda@paroquia.org>'
--   export APP_URL_BASE=https://endereco-real-da-aplicacao
-- =====================================================

-- =====================================================
-- DIAGNOSTICO: lista de uma vez TODAS as colunas que a aplicacao espera em
-- tb_usuario e que ainda nao existem no banco.
--
-- Util porque o Hibernate (ddl-auto=validate) reclama de uma coluna por vez:
-- sem isso, cada tentativa de subir revela apenas o proximo campo faltante.
-- Se o resultado vier vazio, a tabela esta completa.
-- =====================================================

SELECT esperada.coluna AS coluna_faltando
  FROM (
        SELECT 'id_usuario'        AS coluna
  UNION SELECT 'nome'
  UNION SELECT 'username'
  UNION SELECT 'password_hash'
  UNION SELECT 'tipo'
  UNION SELECT 'email'
  UNION SELECT 'telefone'
  UNION SELECT 'senha_provisoria'
  UNION SELECT 'data_troca_senha'
  UNION SELECT 'ultimo_login'
  UNION SELECT 'tentativas_falhas'
  UNION SELECT 'bloqueado_ate'
  UNION SELECT 'id_catequista'
  UNION SELECT 'id_coordenador'
  UNION SELECT 'ativo'
  UNION SELECT 'data_criacao'
       ) esperada
  LEFT JOIN information_schema.COLUMNS c
         ON c.TABLE_SCHEMA = DATABASE()
        AND c.TABLE_NAME   = 'tb_usuario'
        AND c.COLUMN_NAME  = esperada.coluna
 WHERE c.COLUMN_NAME IS NULL;

-- Conferencia
DESC tb_usuario;
DESC tb_token_recuperacao;
DESC tb_configuracao;
SELECT id_usuario, nome, username, tipo, email, senha_provisoria, ativo FROM tb_usuario;
