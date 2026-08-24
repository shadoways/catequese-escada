-- MIGRAR registros de tb_documentos (antiga) para tb_documento (nova)
-- 1) cria backup, 2) ajusta schema, 3) copia registros, 4) valida

SET autocommit=0;
START TRANSACTION;

-- 0. Informações iniciais
SELECT 'INICIANDO MIGRACAO' as info;

-- 1. Backup da tabela antiga (caso exista)
CREATE TABLE IF NOT EXISTS tb_documentos_backup AS SELECT * FROM tb_documentos;

-- 2. Assegurar que a tabela destino exista
-- (se não existir, isto falhará e você deve criar com CREATE_TABELAS_MAPEADAS.sql)

-- 3. Adicionar PK id_documento à tabela destino se não existir
ALTER TABLE tb_documento ADD COLUMN IF NOT EXISTS id_documento BIGINT AUTO_INCREMENT PRIMARY KEY FIRST;

-- 4. Copiar dados da tabela antiga para a nova mapeando colunas
-- Colunas em tb_documentos: id_documento, caminho_arquivo, data_envio, tipo_documento, id_catequisando
-- Colunas em tb_documento   : id_documento, tipo_documento, caminho_arquivo, data_envio, id_catequisando

INSERT INTO tb_documento (id_documento, tipo_documento, caminho_arquivo, data_envio, id_catequisando)
SELECT id_documento, tipo_documento, caminho_arquivo, data_envio, id_catequisando
FROM tb_documentos
ON DUPLICATE KEY UPDATE
    tipo_documento = VALUES(tipo_documento),
    caminho_arquivo = VALUES(caminho_arquivo),
    data_envio = VALUES(data_envio),
    id_catequisando = VALUES(id_catequisando);

-- 5. Verificar contagens
SELECT
    (SELECT COUNT(*) FROM tb_documentos) AS total_antiga,
    (SELECT COUNT(*) FROM tb_documento) AS total_nova;

COMMIT;
SET autocommit=1;

SELECT 'MIGRACAO_CONCLUIDA' as status;

