-- ================================================================
-- SCRIPT DE VERIFICAÇÃO - SISTEMA DE AUTENTICAÇÃO
-- ================================================================
-- Use este script para verificar se as tabelas já existem
-- antes de executar sql/CREATE_AUTH_TABLES.sql
-- Data: 2026-03-03
-- ================================================================

-- ================================================================
-- 1. VERIFICAR TABELAS EXISTENTES
-- ================================================================

SELECT
    'Verificando tabelas de autenticação...' AS Status;

-- Verificar se tb_usuario existe
SELECT
    CASE
        WHEN COUNT(*) > 0 THEN '✅ tb_usuario JÁ EXISTE'
        ELSE '❌ tb_usuario NÃO EXISTE'
    END AS Verificacao
FROM information_schema.tables
WHERE table_schema = 'catequese'
  AND table_name = 'tb_usuario';

-- Verificar se tb_usuario_role existe
SELECT
    CASE
        WHEN COUNT(*) > 0 THEN '✅ tb_usuario_role JÁ EXISTE'
        ELSE '❌ tb_usuario_role NÃO EXISTE'
    END AS Verificacao
FROM information_schema.tables
WHERE table_schema = 'catequese'
  AND table_name = 'tb_usuario_role';

-- Verificar se tb_password_reset_token existe
SELECT
    CASE
        WHEN COUNT(*) > 0 THEN '✅ tb_password_reset_token JÁ EXISTE'
        ELSE '❌ tb_password_reset_token NÃO EXISTE'
    END AS Verificacao
FROM information_schema.tables
WHERE table_schema = 'catequese'
  AND table_name = 'tb_password_reset_token';

-- ================================================================
-- 2. CONTAR REGISTROS (se tabelas existirem)
-- ================================================================

-- Contar usuários (se existir)
SELECT COUNT(*) AS total_usuarios
FROM tb_usuario
WHERE 1=0
UNION ALL
SELECT COUNT(*) FROM tb_usuario;

-- Contar roles (se existir)
SELECT COUNT(*) AS total_roles
FROM tb_usuario_role
WHERE 1=0
UNION ALL
SELECT COUNT(*) FROM tb_usuario_role;

-- Contar tokens de reset (se existir)
SELECT COUNT(*) AS total_tokens_reset
FROM tb_password_reset_token
WHERE 1=0
UNION ALL
SELECT COUNT(*) FROM tb_password_reset_token;

-- ================================================================
-- 3. LISTAR USUÁRIOS EXISTENTES (se tabela existir)
-- ================================================================

SELECT
    u.id_usuario,
    u.nome,
    u.email,
    u.ativo,
    u.ultimo_login,
    GROUP_CONCAT(ur.role SEPARATOR ', ') AS roles
FROM tb_usuario u
LEFT JOIN tb_usuario_role ur ON u.id_usuario = ur.id_usuario
WHERE 1=0
UNION ALL
SELECT
    u.id_usuario,
    u.nome,
    u.email,
    u.ativo,
    u.ultimo_login,
    GROUP_CONCAT(ur.role SEPARATOR ', ') AS roles
FROM tb_usuario u
LEFT JOIN tb_usuario_role ur ON u.id_usuario = ur.id_usuario
GROUP BY u.id_usuario, u.nome, u.email, u.ativo, u.ultimo_login
ORDER BY id_usuario;

-- ================================================================
-- 4. VERIFICAR ESTRUTURA DAS TABELAS (se existirem)
-- ================================================================

-- Estrutura de tb_usuario
DESCRIBE tb_usuario;

-- Estrutura de tb_usuario_role
DESCRIBE tb_usuario_role;

-- Estrutura de tb_password_reset_token
DESCRIBE tb_password_reset_token;

-- ================================================================
-- 5. VERIFICAR CONSTRAINTS E INDEXES
-- ================================================================

-- Foreign Keys de tb_usuario
SELECT
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'catequese'
  AND TABLE_NAME = 'tb_usuario'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- Foreign Keys de tb_usuario_role
SELECT
    CONSTRAINT_NAME,
    COLUMN_NAME,
    REFERENCED_TABLE_NAME,
    REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'catequese'
  AND TABLE_NAME = 'tb_usuario_role'
  AND REFERENCED_TABLE_NAME IS NOT NULL;

-- Indexes de tb_usuario
SHOW INDEX FROM tb_usuario;

-- Indexes de tb_usuario_role
SHOW INDEX FROM tb_usuario_role;

-- ================================================================
-- 6. VERIFICAR USUÁRIO ADMIN PADRÃO
-- ================================================================

SELECT
    CASE
        WHEN EXISTS (
            SELECT 1 FROM tb_usuario WHERE email = 'admin@catequese.com'
        ) THEN '✅ Usuário admin@catequese.com EXISTE'
        ELSE '❌ Usuário admin@catequese.com NÃO EXISTE'
    END AS VerificacaoAdmin;

-- Detalhes do admin (se existir)
SELECT
    u.id_usuario,
    u.nome,
    u.email,
    u.ativo,
    GROUP_CONCAT(ur.role SEPARATOR ', ') AS roles,
    u.data_criacao,
    u.ultimo_login
FROM tb_usuario u
LEFT JOIN tb_usuario_role ur ON u.id_usuario = ur.id_usuario
WHERE u.email = 'admin@catequese.com'
GROUP BY u.id_usuario, u.nome, u.email, u.ativo, u.data_criacao, u.ultimo_login;

-- ================================================================
-- 7. VERIFICAR INTEGRIDADE DOS DADOS
-- ================================================================

-- Usuários sem roles
SELECT
    u.id_usuario,
    u.nome,
    u.email,
    'USUÁRIO SEM ROLES' AS problema
FROM tb_usuario u
LEFT JOIN tb_usuario_role ur ON u.id_usuario = ur.id_usuario
WHERE ur.id_usuario_role IS NULL;

-- Tokens expirados
SELECT
    COUNT(*) AS tokens_expirados
FROM tb_password_reset_token
WHERE data_expiracao < NOW()
  AND usado = FALSE;

-- Tokens usados
SELECT
    COUNT(*) AS tokens_usados
FROM tb_password_reset_token
WHERE usado = TRUE;

-- ================================================================
-- INSTRUÇÕES
-- ================================================================

/*
Se as tabelas NÃO EXISTEM:
    1. Execute: sql/CREATE_AUTH_TABLES.sql
    2. Execute: sql/POPULATE_AUTH_TEST_DATA.sql (opcional)

Se as tabelas JÁ EXISTEM:
    1. Verifique os dados existentes
    2. Execute: sql/POPULATE_AUTH_TEST_DATA.sql (se quiser dados de teste)

Para limpar tokens expirados:
    DELETE FROM tb_password_reset_token
    WHERE data_expiracao < NOW();
*/

-- ================================================================
-- FIM DO SCRIPT
-- ================================================================


