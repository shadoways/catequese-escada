USE catequese;

-- Alterar tamanho da coluna caminho_arquivo para 500 (se for diferente)
ALTER TABLE tb_documento MODIFY COLUMN caminho_arquivo VARCHAR(500);

-- Verificar
DESCRIBE tb_documento;
SELECT id_documento, caminho_arquivo FROM tb_documento LIMIT 5;

