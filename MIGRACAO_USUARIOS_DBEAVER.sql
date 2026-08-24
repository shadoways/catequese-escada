-- =====================================================
-- MIGRACAO_USUARIOS_DBEAVER.sql
-- Mesmo script de MIGRACAO_USUARIOS.sql, so que sem o comando DELIMITER.
--
-- Por que existe: o DBeaver ("Execute SQL Script") nao entende o comando
-- DELIMITER -- isso e um recurso do cliente "mysql" de linha de comando, nao
-- do protocolo SQL. Quando o DBeaver encontra DELIMITER $$ / DELIMITER ; no
-- meio do script, ele tenta separar as instrucoes do jeito errado e o
-- MariaDB acusa erro de sintaxe (foi o que aconteceu: erro 1064 logo nos
-- DROP PROCEDURE, antes mesmo de chegar nas procedures).
--
-- O DBeaver ja sabe identificar sozinho um bloco CREATE PROCEDURE ... BEGIN
-- ... END; e nao separa pelos ";" que ficam DENTRO do bloco -- por isso aqui
-- basta terminar cada procedure com "END;" normal, sem DELIMITER nenhum.
--
-- SEGURO PARA PRODUCAO (igual ao original):
--   * NUNCA executa DROP TABLE, DROP COLUMN, DELETE nem UPDATE de dados.
--   * So CRIA o que estiver faltando: tabela, coluna ou indice.
--   * Pode ser executado quantas vezes for preciso, em banco novo ou antigo.
--     Rodar duas vezes seguidas nao causa erro nem muda nada na segunda vez.
--
-- COMO USAR NO DBEAVER: abra este arquivo, selecione tudo (Ctrl+A) e rode
-- com "Execute SQL Script" (Alt+X) -- nao com "Execute SQL Statement"
-- (Ctrl+Enter), que roda so a instrucao onde o cursor esta.
--
-- Se preferir rodar pelo terminal com o cliente mysql, use o arquivo
-- MIGRACAO_USUARIOS.sql (esse sim precisa do DELIMITER).
--
-- Ao final ele imprime um relatorio do que ainda estiver faltando. Relatorio
-- vazio = banco pronto para a aplicacao subir (ela usa ddl-auto=validate e nao
-- inicia se faltar qualquer coluna).
-- =====================================================

USE catequese;

-- =====================================================
-- PASSO 1: ferramentas auxiliares
-- O MySQL nao aceita "ADD COLUMN IF NOT EXISTS" (o MariaDB aceita). A checagem
-- e feita via information_schema, que funciona igual nos dois.
-- =====================================================

DROP PROCEDURE IF EXISTS cria_coluna_se_faltar;
DROP PROCEDURE IF EXISTS cria_indice_se_faltar;
DROP PROCEDURE IF EXISTS cria_indice_unico_se_possivel;
DROP PROCEDURE IF EXISTS permite_nulo_se_precisar;
DROP PROCEDURE IF EXISTS amplia_texto_se_curto;

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
END;

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
END;

-- Indice unico exige cuidado extra: se a coluna ja tiver valores repetidos, o
-- ALTER falharia e derrubaria o restante do script. Aqui ele confere antes e,
-- havendo duplicados, apenas avisa e segue em frente.
CREATE PROCEDURE cria_indice_unico_se_possivel(
    IN p_tabela VARCHAR(64),
    IN p_indice VARCHAR(64),
    IN p_coluna VARCHAR(64)
)
BEGIN
    DECLARE v_tem_indice INT DEFAULT 0;
    DECLARE v_duplicados INT DEFAULT 0;

    SELECT COUNT(*) INTO v_tem_indice
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabela
       AND INDEX_NAME = p_indice;

    IF v_tem_indice = 0 THEN
        SET @consulta = CONCAT(
            'SELECT COUNT(*) INTO @qtd_dup FROM (SELECT ', p_coluna,
            ' FROM ', p_tabela, ' GROUP BY ', p_coluna, ' HAVING COUNT(*) > 1) d');
        PREPARE st FROM @consulta;
        EXECUTE st;
        DEALLOCATE PREPARE st;

        SET v_duplicados = IFNULL(@qtd_dup, 0);

        IF v_duplicados = 0 THEN
            SET @ddl = CONCAT('ALTER TABLE ', p_tabela,
                              ' ADD UNIQUE INDEX ', p_indice, ' (', p_coluna, ')');
            PREPARE st2 FROM @ddl;
            EXECUTE st2;
            DEALLOCATE PREPARE st2;
        ELSE
            SELECT CONCAT('AVISO: ', p_tabela, '.', p_coluna, ' tem valores repetidos. ',
                          'O indice unico ', p_indice, ' NAO foi criado. ',
                          'Resolva os duplicados e rode este script de novo.') AS aviso;
        END IF;
    END IF;
END;

-- Coluna que existe mas esta como NOT NULL sendo que a aplicacao grava nulo.
-- Afrouxar a obrigatoriedade nunca perde dado: o que ja esta preenchido
-- continua igual, apenas passa a aceitar nulo daqui em diante.
CREATE PROCEDURE permite_nulo_se_precisar(
    IN p_tabela VARCHAR(64),
    IN p_coluna VARCHAR(64),
    IN p_tipo VARCHAR(64)
)
BEGIN
    DECLARE v_obrigatoria INT DEFAULT 0;

    SELECT COUNT(*) INTO v_obrigatoria
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabela
       AND COLUMN_NAME = p_coluna
       AND IS_NULLABLE = 'NO';

    IF v_obrigatoria = 1 THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_tabela, ' MODIFY COLUMN ',
                          p_coluna, ' ', p_tipo, ' NULL');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END;

-- Coluna de texto menor do que a aplicacao precisa. Ampliar nunca perde dado.
-- O caso critico e password_hash: o hash BCrypt tem 60 caracteres e, numa
-- coluna menor, o MySQL trunca em silencio (fora do modo estrito). O login
-- passaria a falhar SEMPRE, com "usuario ou senha invalidos", porque o hash
-- guardado nunca mais confere.
CREATE PROCEDURE amplia_texto_se_curto(
    IN p_tabela VARCHAR(64),
    IN p_coluna VARCHAR(64),
    IN p_tamanho INT
)
BEGIN
    DECLARE v_atual INT DEFAULT NULL;

    SELECT CHARACTER_MAXIMUM_LENGTH INTO v_atual
      FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = p_tabela
       AND COLUMN_NAME = p_coluna;

    IF v_atual IS NOT NULL AND v_atual < p_tamanho THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_tabela, ' MODIFY COLUMN ', p_coluna,
                          ' VARCHAR(', p_tamanho, ')');
        PREPARE st FROM @ddl;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END;

-- =====================================================
-- PASSO 2: tabelas (so cria se nao existirem)
-- =====================================================

CREATE TABLE IF NOT EXISTS tb_usuario (
    id_usuario BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
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
    data_criacao DATETIME NULL
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
    ip_solicitante VARCHAR(45) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chaves temporarias que liberam o cadastro publico.
-- Sem uma chave valida, o formulario de inscricao nao aceita envio: evita que
-- qualquer um que descubra o endereco do sistema cadastre o que quiser.
CREATE TABLE IF NOT EXISTS tb_chave_inscricao (
    id_chave BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- Vai no link divulgado, no formato CAT-XXXX-XXXX.
    codigo VARCHAR(40) NOT NULL,
    descricao VARCHAR(255) NULL,
    expira_em DATETIME NOT NULL,
    -- Nulo = sem limite de quantidade de cadastros.
    limite_usos INT NULL,
    usos INT NOT NULL DEFAULT 0,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por VARCHAR(255) NULL,
    criado_em DATETIME NULL,
    revogada_em DATETIME NULL
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

-- Matricula: vinculo do catequisando com uma turma, num ano.
-- E o que da historico ao sistema. Sem ela, ao passar de Crisma I para
-- Crisma II o ano anterior se perderia.
CREATE TABLE IF NOT EXISTS tb_matricula (
    id_matricula BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_catequisando BIGINT NOT NULL,
    id_turma BIGINT NOT NULL,
    ano INT NOT NULL,
    -- Marco zero da frequencia: quem entrou em abril nao e cobrado por marco.
    data_matricula DATE NULL,
    -- CURSANDO | CONCLUIDO | NAO_CONCLUIDO | TRANSFERIDO | DESISTENTE
    situacao VARCHAR(30) NOT NULL DEFAULT 'CURSANDO',
    observacao VARCHAR(500) NULL,
    criado_em DATETIME NULL,
    atualizado_em DATETIME NULL,
    atualizado_por VARCHAR(255) NULL,
    INDEX idx_matricula_catequisando (id_catequisando),
    INDEX idx_matricula_turma_ano (id_turma, ano)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Encontro: a "aula" onde a chamada e feita.
CREATE TABLE IF NOT EXISTS tb_encontro (
    id_encontro BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_turma BIGINT NOT NULL,
    data DATE NOT NULL,
    -- Assunto do dia: vira o diario da turma.
    tema VARCHAR(255) NULL,
    -- ABERTO | FECHADO | CANCELADO
    situacao VARCHAR(20) NOT NULL DEFAULT 'ABERTO',
    -- Obrigatorio quando cancelado, inclusive ao fechar sem nenhuma presenca.
    motivo_cancelamento VARCHAR(500) NULL,
    -- Retiro, missa: presenca fora do encontro comum.
    id_evento BIGINT NULL,
    aberto_por VARCHAR(255) NULL,
    aberto_em DATETIME NULL,
    fechado_por VARCHAR(255) NULL,
    fechado_em DATETIME NULL,
    -- True quando quem fechou foi o sistema, e nao uma pessoa.
    fechamento_automatico BOOLEAN NOT NULL DEFAULT FALSE,
    INDEX idx_encontro_turma_data (id_turma, data),
    INDEX idx_encontro_situacao (situacao)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Historico de etapas do catecumenato, por pessoa.
-- E historico, e nao um campo unico, porque cada etapa tem apuracao propria de
-- frequencia e duracao variavel. A etapa em andamento e a de data_fim nula.
CREATE TABLE IF NOT EXISTS tb_etapa_catecumeno (
    id_etapa BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_catequisando BIGINT NOT NULL,
    -- PRE_CATECUMENATO | CATECUMENATO | PURIFICACAO_ILUMINACAO | MISTAGOGIA
    etapa VARCHAR(40) NOT NULL,
    data_inicio DATE NULL,
    data_fim DATE NULL,
    observacao VARCHAR(500) NULL,
    registrado_por VARCHAR(255) NULL,
    registrado_em DATETIME NULL,
    INDEX idx_etapa_catequisando (id_catequisando)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Equipe de catequistas da turma. A coluna id_catequista de tb_turma continua
-- valendo como responsavel principal; esta tabela acrescenta os demais.
CREATE TABLE IF NOT EXISTS tb_turma_catequista (
    id_turma_catequista BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_turma BIGINT NOT NULL,
    id_catequista BIGINT NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em DATETIME NULL,
    INDEX idx_turma_catequista_turma (id_turma),
    INDEX idx_turma_catequista_catequista (id_catequista)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================
-- PASSO 3: colunas
-- Cobre tanto tabela recem-criada quanto tabela antiga, incompleta ou criada
-- a mao.
--
-- Por que as colunas de texto entram como NULL aqui, se na criacao da tabela
-- sao NOT NULL? Porque acrescentar NOT NULL a uma tabela que ja tem linhas
-- falha quando o banco esta em modo estrito, e um DEFAULT de texto exigiria
-- aspas que quebram se o banco estiver com ANSI_QUOTES ligado. O Hibernate
-- valida existencia e tipo da coluna, nao a obrigatoriedade, entao NULL aqui
-- e seguro. Quem preenche esses campos e sempre a aplicacao.
-- =====================================================

CALL cria_coluna_se_faltar('tb_usuario', 'nome',              'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'username',          'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'password_hash',     'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'tipo',              'VARCHAR(40) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'email',             'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'telefone',          'VARCHAR(40) NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'senha_provisoria',  'BOOLEAN NOT NULL DEFAULT FALSE');
CALL cria_coluna_se_faltar('tb_usuario', 'data_troca_senha',  'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'ultimo_login',      'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'tentativas_falhas', 'INT NOT NULL DEFAULT 0');
CALL cria_coluna_se_faltar('tb_usuario', 'bloqueado_ate',     'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'id_catequista',     'BIGINT NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'id_coordenador',    'BIGINT NULL');
CALL cria_coluna_se_faltar('tb_usuario', 'ativo',             'BOOLEAN NOT NULL DEFAULT TRUE');
CALL cria_coluna_se_faltar('tb_usuario', 'data_criacao',      'DATETIME NULL');

CALL cria_coluna_se_faltar('tb_token_recuperacao', 'id_usuario',     'BIGINT NULL');
CALL cria_coluna_se_faltar('tb_token_recuperacao', 'token_hash',     'VARCHAR(64) NULL');
CALL cria_coluna_se_faltar('tb_token_recuperacao', 'expira_em',      'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_token_recuperacao', 'usado_em',       'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_token_recuperacao', 'criado_em',      'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_token_recuperacao', 'ip_solicitante', 'VARCHAR(45) NULL');

-- Comunidade do usuario: o coordenador so enxerga a propria.
CALL cria_coluna_se_faltar('tb_usuario', 'id_comunidade', 'BIGINT NULL');

-- Categoria e etapa da turma decidem a regra de frequencia.
-- PRE_CATEQUESE | EUCARISTIA | CRISMA | ADULTOS | CATECUMENATO | PERSEVERANCA
CALL cria_coluna_se_faltar('tb_turma', 'categoria', 'VARCHAR(40) NULL');
-- 1 = primeiro ano (Crisma I), 2 = segundo ano (Crisma II).
CALL cria_coluna_se_faltar('tb_turma', 'etapa',     'INT NULL');

-- Presenca ganha encontro, situacao e autoria. As colunas data e presente
-- continuam por causa dos registros anteriores ao conceito de encontro.
CALL cria_coluna_se_faltar('tb_presenca', 'id_encontro',    'BIGINT NULL');
-- PRESENTE | FALTA | JUSTIFICADA
CALL cria_coluna_se_faltar('tb_presenca', 'situacao',       'VARCHAR(20) NULL');
CALL cria_coluna_se_faltar('tb_presenca', 'justificativa',  'VARCHAR(500) NULL');
CALL cria_coluna_se_faltar('tb_presenca', 'marcado_por',    'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_presenca', 'marcado_em',     'DATETIME NULL');

CALL cria_coluna_se_faltar('tb_chave_inscricao', 'codigo',      'VARCHAR(40) NULL');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'descricao',   'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'expira_em',   'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'limite_usos', 'INT NULL');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'usos',        'INT NOT NULL DEFAULT 0');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'ativo',       'BOOLEAN NOT NULL DEFAULT TRUE');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'criado_por',  'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'criado_em',   'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_chave_inscricao', 'revogada_em', 'DATETIME NULL');

CALL cria_coluna_se_faltar('tb_configuracao', 'valor',          'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_configuracao', 'descricao',      'VARCHAR(255) NULL');
CALL cria_coluna_se_faltar('tb_configuracao', 'atualizado_em',  'DATETIME NULL');
CALL cria_coluna_se_faltar('tb_configuracao', 'atualizado_por', 'VARCHAR(255) NULL');

-- =====================================================
-- PASSO 4: obrigatoriedade das colunas
--
-- Estas colunas a aplicacao grava como nulas. Se no banco elas estiverem como
-- NOT NULL, o cadastro de usuario falha com "Column 'x' cannot be null".
-- Acontece quando a tabela foi criada a mao ou por um script antigo.
-- =====================================================

CALL permite_nulo_se_precisar('tb_usuario', 'email',            'VARCHAR(255)');
CALL permite_nulo_se_precisar('tb_usuario', 'telefone',         'VARCHAR(40)');
CALL permite_nulo_se_precisar('tb_usuario', 'data_troca_senha', 'DATETIME');
CALL permite_nulo_se_precisar('tb_usuario', 'ultimo_login',     'DATETIME');
CALL permite_nulo_se_precisar('tb_usuario', 'bloqueado_ate',    'DATETIME');
CALL permite_nulo_se_precisar('tb_usuario', 'id_catequista',    'BIGINT');
CALL permite_nulo_se_precisar('tb_usuario', 'id_coordenador',   'BIGINT');
CALL permite_nulo_se_precisar('tb_usuario', 'data_criacao',     'DATETIME');

CALL permite_nulo_se_precisar('tb_token_recuperacao', 'usado_em',       'DATETIME');
CALL permite_nulo_se_precisar('tb_token_recuperacao', 'ip_solicitante', 'VARCHAR(45)');

CALL permite_nulo_se_precisar('tb_configuracao', 'descricao',      'VARCHAR(255)');
CALL permite_nulo_se_precisar('tb_configuracao', 'atualizado_em',  'DATETIME');
CALL permite_nulo_se_precisar('tb_configuracao', 'atualizado_por', 'VARCHAR(255)');

-- Tamanhos minimos. password_hash e o critico: BCrypt ocupa 60 caracteres.
CALL amplia_texto_se_curto('tb_usuario', 'password_hash', 255);
CALL amplia_texto_se_curto('tb_usuario', 'username',      255);
CALL amplia_texto_se_curto('tb_usuario', 'nome',          255);
CALL amplia_texto_se_curto('tb_usuario', 'tipo',          40);
CALL amplia_texto_se_curto('tb_usuario', 'email',         255);
CALL amplia_texto_se_curto('tb_token_recuperacao', 'token_hash', 64);

-- =====================================================
-- PASSO 5: indices
-- =====================================================

CALL cria_indice_unico_se_possivel('tb_usuario', 'uk_usuario_username', 'username');
CALL cria_indice_se_faltar('tb_usuario', 'idx_usuario_tipo',  'tipo');
CALL cria_indice_se_faltar('tb_usuario', 'idx_usuario_email', 'email');

CALL cria_indice_unico_se_possivel('tb_chave_inscricao', 'uk_chave_codigo', 'codigo');

CALL cria_indice_se_faltar('tb_matricula', 'idx_matricula_catequisando', 'id_catequisando');
CALL cria_indice_se_faltar('tb_encontro',  'idx_encontro_turma_data',    'id_turma, data');
CALL cria_indice_se_faltar('tb_presenca',  'idx_presenca_encontro',      'id_encontro');

CALL cria_indice_se_faltar('tb_token_recuperacao', 'idx_token_hash',    'token_hash');
CALL cria_indice_se_faltar('tb_token_recuperacao', 'idx_token_usuario', 'id_usuario');

DROP PROCEDURE IF EXISTS cria_coluna_se_faltar;
DROP PROCEDURE IF EXISTS cria_indice_se_faltar;
DROP PROCEDURE IF EXISTS cria_indice_unico_se_possivel;
DROP PROCEDURE IF EXISTS permite_nulo_se_precisar;
DROP PROCEDURE IF EXISTS amplia_texto_se_curto;

-- =====================================================
-- PASSO 6: relatorio final
-- Lista tudo o que a aplicacao espera e ainda nao existe. Vazio = pronto.
--
-- Nao usei chave estrangeira de propriedade nenhuma neste script: o Hibernate
-- valida colunas e tipos, nao chaves estrangeiras, e criar FK em producao pode
-- falhar por diferenca de tipo ou por dado ja existente. Se quiser as FKs,
-- crie a mao depois, com calma:
--   ALTER TABLE tb_token_recuperacao ADD CONSTRAINT fk_token_usuario
--       FOREIGN KEY (id_usuario) REFERENCES tb_usuario(id_usuario) ON DELETE CASCADE;
--   ALTER TABLE tb_usuario ADD CONSTRAINT fk_usuario_catequista
--       FOREIGN KEY (id_catequista) REFERENCES tb_catequista(id_catequista) ON DELETE SET NULL;
--   ALTER TABLE tb_usuario ADD CONSTRAINT fk_usuario_coordenador
--       FOREIGN KEY (id_coordenador) REFERENCES tb_coordenador(id_coordenador) ON DELETE SET NULL;
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
  UNION ALL SELECT 'tb_usuario', 'id_comunidade'
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

-- Observacao sobre as colunas de chave primaria (id_usuario, id_token, chave):
-- elas nao podem ser acrescentadas por ALTER de forma segura. Se alguma
-- aparecer no relatorio acima, a tabela esta muito diferente do esperado --
-- nesse caso vale criar uma tabela nova com o nome correto e migrar os dados,
-- em vez de tentar consertar no lugar.

-- =====================================================
-- O primeiro administrador NAO e criado por SQL, porque a senha precisa ser
-- gravada como hash BCrypt. Quem cria e a propria aplicacao, na primeira
-- subida, se tb_usuario estiver vazia.
--
-- Sem configurar nada, ela cria o usuario "admin" com uma senha aleatoria e
-- imprime essa senha UMA UNICA VEZ no log da subida. A troca e exigida no
-- primeiro login.
--
-- Para escolher os dados do admin, defina antes de subir:
--   export ADMIN_INICIAL_USERNAME=coordenador
--   export ADMIN_INICIAL_NOME='Nome do Coordenador Paroquial'
--   export ADMIN_INICIAL_EMAIL=coordenador@paroquia.org
--   export ADMIN_INICIAL_PASSWORD='sua senha'    # opcional; sem isso o sistema gera
--
-- Outras variaveis de ambiente da aplicacao:
--   export JWT_SECRET="$(openssl rand -base64 48)"   # OBRIGATORIA, minimo 32 caracteres
--   export APP_SECURITY_ENABLED=false               # valvula de escape; o padrao e true
--   export SPRING_MAIL_HOST=smtp.seuprovedor.com     # opcional (recuperacao por e-mail)
--   export SPRING_MAIL_PORT=587
--   export SPRING_MAIL_USERNAME=usuario
--   export SPRING_MAIL_PASSWORD='senha do smtp'
--   export APP_EMAIL_REMETENTE='Catequese Admin <nao-responda@paroquia.org>'
--   export APP_URL_BASE=https://endereco-real-da-aplicacao
-- =====================================================
