-- ================================================================
-- DADOS DE TESTE - SISTEMA DE AUTENTICAÇÃO
-- ================================================================
-- Script para popular dados de exemplo
-- ATENÇÃO: Use apenas em ambiente de desenvolvimento/teste
-- Data: 2026-03-03
-- ================================================================

-- ================================================================
-- LIMPAR DADOS EXISTENTES (CUIDADO EM PRODUÇÃO!)
-- ================================================================
-- DELETE FROM tb_password_reset_token;
-- DELETE FROM tb_usuario_role;
-- DELETE FROM tb_usuario WHERE email != 'admin@catequese.com';

-- ================================================================
-- USUÁRIOS DE TESTE
-- ================================================================

-- Coordenador Paroquial
INSERT INTO tb_usuario (nome, email, password_hash, ativo)
VALUES (
    'João Silva',
    'joao.silva@catequese.com',
    '$2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq', -- senha: admin123
    TRUE
);

INSERT INTO tb_usuario_role (id_usuario, role)
VALUES (
    (SELECT id_usuario FROM tb_usuario WHERE email = 'joao.silva@catequese.com'),
    'COORDENADOR_PAROQUIAL'
);

-- Coordenador de Comunidade (vinculado à primeira comunidade)
INSERT INTO tb_usuario (nome, email, password_hash, ativo, id_comunidade)
VALUES (
    'Maria Santos',
    'maria.santos@catequese.com',
    '$2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq', -- senha: admin123
    TRUE,
    (SELECT id_comunidade FROM tb_comunidade LIMIT 1)
);

INSERT INTO tb_usuario_role (id_usuario, role)
VALUES (
    (SELECT id_usuario FROM tb_usuario WHERE email = 'maria.santos@catequese.com'),
    'COORDENADOR_COMUNIDADE'
);

-- Catequista (vinculado ao primeiro catequista cadastrado)
INSERT INTO tb_usuario (nome, email, password_hash, ativo, id_catequista)
VALUES (
    'Pedro Oliveira',
    'pedro.oliveira@catequese.com',
    '$2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq', -- senha: admin123
    TRUE,
    (SELECT id_catequista FROM tb_catequista LIMIT 1)
);

INSERT INTO tb_usuario_role (id_usuario, role)
VALUES (
    (SELECT id_usuario FROM tb_usuario WHERE email = 'pedro.oliveira@catequese.com'),
    'CATEQUISTA'
);

-- Usuário com múltiplas roles (Coordenador de Comunidade + Catequista)
INSERT INTO tb_usuario (nome, email, password_hash, ativo, id_comunidade, id_catequista)
VALUES (
    'Ana Costa',
    'ana.costa@catequese.com',
    '$2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq', -- senha: admin123
    TRUE,
    (SELECT id_comunidade FROM tb_comunidade LIMIT 1),
    (SELECT id_catequista FROM tb_catequista LIMIT 1 OFFSET 1)
);

INSERT INTO tb_usuario_role (id_usuario, role)
VALUES
    ((SELECT id_usuario FROM tb_usuario WHERE email = 'ana.costa@catequese.com'), 'COORDENADOR_COMUNIDADE'),
    ((SELECT id_usuario FROM tb_usuario WHERE email = 'ana.costa@catequese.com'), 'CATEQUISTA');

-- Usuário inativo (para testes de login)
INSERT INTO tb_usuario (nome, email, password_hash, ativo)
VALUES (
    'Carlos Inativo',
    'carlos.inativo@catequese.com',
    '$2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq', -- senha: admin123
    FALSE
);

INSERT INTO tb_usuario_role (id_usuario, role)
VALUES (
    (SELECT id_usuario FROM tb_usuario WHERE email = 'carlos.inativo@catequese.com'),
    'CATEQUISTA'
);

-- ================================================================
-- VERIFICAR DADOS INSERIDOS
-- ================================================================

-- Listar todos os usuários com suas roles
SELECT
    u.id_usuario,
    u.nome,
    u.email,
    u.ativo,
    GROUP_CONCAT(ur.role SEPARATOR ', ') AS roles,
    c.nome AS comunidade,
    cat.nome AS catequista
FROM tb_usuario u
LEFT JOIN tb_usuario_role ur ON u.id_usuario = ur.id_usuario
LEFT JOIN tb_comunidade c ON u.id_comunidade = c.id_comunidade
LEFT JOIN tb_catequista cat ON u.id_catequista = cat.id_catequista
GROUP BY u.id_usuario, u.nome, u.email, u.ativo, c.nome, cat.nome
ORDER BY u.id_usuario;

-- ================================================================
-- INFORMAÇÕES IMPORTANTES
-- ================================================================

-- Todos os usuários de teste têm a senha: admin123
-- Hash BCrypt: $2a$10$SlVZrKwUmK8qIL2yDySe.aVbgvMdQx/k4U6S4vqKoXlQE7lJZGjPq

-- Credenciais de teste:
-- admin@catequese.com / admin123 (COORDENADOR_PAROQUIAL)
-- joao.silva@catequese.com / admin123 (COORDENADOR_PAROQUIAL)
-- maria.santos@catequese.com / admin123 (COORDENADOR_COMUNIDADE)
-- pedro.oliveira@catequese.com / admin123 (CATEQUISTA)
-- ana.costa@catequese.com / admin123 (COORDENADOR_COMUNIDADE + CATEQUISTA)

-- ================================================================
-- FIM DO SCRIPT
-- ================================================================

