-- MIGRAR_DOCUMENTOS_FIX.sql
-- Corrige esquema de tb_documento e copia dados de tb_documentos

USE catequese;

-- 1) Adicionar coluna id_documento (PK AUTO_INCREMENT) se não existir
ALTER TABLE tb_documento
  ADD COLUMN IF NOT EXISTS id_documento BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST;

-- 2) Adicionar coluna tipo_documento se não existir
ALTER TABLE tb_documento
  ADD COLUMN IF NOT EXISTS tipo_documento VARCHAR(255) AFTER caminho_arquivo;

-- 3) Se existirem linhas na antiga tb_documentos, copiar para tb_documento
INSERT INTO tb_documento (id_documento, tipo_documento, caminho_arquivo, data_envio, id_catequisando)
SELECT id_documento, tipo_documento, caminho_arquivo, data_envio, id_catequisando
FROM tb_documentos
ON DUPLICATE KEY UPDATE
  tipo_documento = VALUES(tipo_documento),
  caminho_arquivo = VALUES(caminho_arquivo),
  data_envio = VALUES(data_envio),
  id_catequisando = VALUES(id_catequisando);

-- 4) Verificação final
SELECT 'VERIFY' as step;
SELECT COUNT(*) AS total_old FROM tb_documentos;
SELECT COUNT(*) AS total_new FROM tb_documento;
SELECT id_documento, tipo_documento, caminho_arquivo, data_envio, id_catequisando FROM tb_documento LIMIT 10;

