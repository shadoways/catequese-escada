package catequisando

import (
	"context"
	"database/sql"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) FindAll(ctx context.Context) ([]Catequisando, error) {
	const query = `
SELECT
    c.id_catequisando,
    c.nome,
    COALESCE(c.telefone, ''),
    COALESCE(c.email, ''),
    COALESCE(DATE_FORMAT(c.data_nascimento, '%Y-%m-%d'), ''),
    COALESCE(c.nome_responsavel, ''),
    COALESCE(c.telefone_responsavel, ''),
    COALESCE(c.endereco, ''),
    COALESCE(c.numero_documento, ''),
    COALESCE(c.tipo_documento, ''),
    COALESCE(c.intolerante_gluten, FALSE),
    COALESCE(c.foi_batizado, FALSE),
    COALESCE(c.fez_primeira_eucaristia, FALSE),
    COALESCE(c.estado_conjugal, ''),
    COALESCE(c.ativo, TRUE),
    t.id_turma,
    COALESCE(t.nome, ''),
    m.id_comunidade,
    COALESCE(m.nome, '')
FROM tb_catequisando c
LEFT JOIN tb_turma t ON t.id_turma = c.id_turma
LEFT JOIN tb_comunidade m ON m.id_comunidade = c.id_comunidade
ORDER BY c.id_catequisando ASC`

	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make([]Catequisando, 0)
	for rows.Next() {
		item, err := scanCatequisando(rows)
		if err != nil {
			return nil, err
		}
		result = append(result, item)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return result, nil
}

func (r *Repository) FindByID(ctx context.Context, id int64) (Catequisando, error) {
	const query = `
SELECT
    c.id_catequisando,
    c.nome,
    COALESCE(c.telefone, ''),
    COALESCE(c.email, ''),
    COALESCE(DATE_FORMAT(c.data_nascimento, '%Y-%m-%d'), ''),
    COALESCE(c.nome_responsavel, ''),
    COALESCE(c.telefone_responsavel, ''),
    COALESCE(c.endereco, ''),
    COALESCE(c.numero_documento, ''),
    COALESCE(c.tipo_documento, ''),
    COALESCE(c.intolerante_gluten, FALSE),
    COALESCE(c.foi_batizado, FALSE),
    COALESCE(c.fez_primeira_eucaristia, FALSE),
    COALESCE(c.estado_conjugal, ''),
    COALESCE(c.ativo, TRUE),
    t.id_turma,
    COALESCE(t.nome, ''),
    m.id_comunidade,
    COALESCE(m.nome, '')
FROM tb_catequisando c
LEFT JOIN tb_turma t ON t.id_turma = c.id_turma
LEFT JOIN tb_comunidade m ON m.id_comunidade = c.id_comunidade
WHERE c.id_catequisando = ?
LIMIT 1`

	row := r.db.QueryRowContext(ctx, query, id)
	return scanCatequisando(row)
}

func (r *Repository) Create(ctx context.Context, c Catequisando) (int64, error) {
	const query = `
INSERT INTO tb_catequisando (
    nome, telefone, email, data_nascimento,
    nome_responsavel, telefone_responsavel,
    endereco, numero_documento, tipo_documento,
    intolerante_gluten, foi_batizado, fez_primeira_eucaristia,
    estado_conjugal, ativo, id_turma, id_comunidade
) VALUES (?, ?, ?, NULLIF(?, ''), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULLIF(?, 0), NULLIF(?, 0))`

	turmaID := int64(0)
	if c.Turma != nil {
		turmaID = c.Turma.IDTurma
	}
	comunidadeID := int64(0)
	if c.Comunidade != nil {
		comunidadeID = c.Comunidade.IDComunidade
	}

	res, err := r.db.ExecContext(ctx, query,
		c.Nome,
		nullIfEmpty(c.Telefone),
		nullIfEmpty(c.Email),
		nullIfEmpty(c.DataNascimento),
		nullIfEmpty(c.NomeResponsavel),
		nullIfEmpty(c.TelefoneResponsavel),
		nullIfEmpty(c.Endereco),
		nullIfEmpty(c.NumeroDocumento),
		nullIfEmpty(c.TipoDocumento),
		c.IntoleranteGluten,
		c.FoiBatizado,
		c.FezPrimeiraEucaristia,
		nullIfEmpty(c.EstadoConjugal),
		c.Ativo,
		turmaID,
		comunidadeID,
	)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, c Catequisando) error {
	const query = `
UPDATE tb_catequisando
SET nome = ?,
    telefone = ?,
    email = ?,
    data_nascimento = NULLIF(?, ''),
    nome_responsavel = ?,
    telefone_responsavel = ?,
    endereco = ?,
    numero_documento = ?,
    tipo_documento = ?,
    intolerante_gluten = ?,
    foi_batizado = ?,
    fez_primeira_eucaristia = ?,
    estado_conjugal = ?,
    ativo = ?,
    id_turma = NULLIF(?, 0),
    id_comunidade = NULLIF(?, 0)
WHERE id_catequisando = ?`

	turmaID := int64(0)
	if c.Turma != nil {
		turmaID = c.Turma.IDTurma
	}
	comunidadeID := int64(0)
	if c.Comunidade != nil {
		comunidadeID = c.Comunidade.IDComunidade
	}

	_, err := r.db.ExecContext(ctx, query,
		c.Nome,
		nullIfEmpty(c.Telefone),
		nullIfEmpty(c.Email),
		nullIfEmpty(c.DataNascimento),
		nullIfEmpty(c.NomeResponsavel),
		nullIfEmpty(c.TelefoneResponsavel),
		nullIfEmpty(c.Endereco),
		nullIfEmpty(c.NumeroDocumento),
		nullIfEmpty(c.TipoDocumento),
		c.IntoleranteGluten,
		c.FoiBatizado,
		c.FezPrimeiraEucaristia,
		nullIfEmpty(c.EstadoConjugal),
		c.Ativo,
		turmaID,
		comunidadeID,
		id,
	)
	return err
}

func (r *Repository) Delete(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_catequisando WHERE id_catequisando = ?`, id)
	return err
}

type rowScanner interface {
	Scan(dest ...any) error
}

func scanCatequisando(scanner rowScanner) (Catequisando, error) {
	var c Catequisando
	var turmaID sql.NullInt64
	var turmaNome string
	var comunidadeID sql.NullInt64
	var comunidadeNome string
	if err := scanner.Scan(
		&c.IDCatequisando,
		&c.Nome,
		&c.Telefone,
		&c.Email,
		&c.DataNascimento,
		&c.NomeResponsavel,
		&c.TelefoneResponsavel,
		&c.Endereco,
		&c.NumeroDocumento,
		&c.TipoDocumento,
		&c.IntoleranteGluten,
		&c.FoiBatizado,
		&c.FezPrimeiraEucaristia,
		&c.EstadoConjugal,
		&c.Ativo,
		&turmaID,
		&turmaNome,
		&comunidadeID,
		&comunidadeNome,
	); err != nil {
		return Catequisando{}, err
	}

	if turmaID.Valid {
		c.Turma = &TurmaRef{IDTurma: turmaID.Int64, Nome: turmaNome}
	}
	if comunidadeID.Valid {
		c.Comunidade = &ComunidadeRef{IDComunidade: comunidadeID.Int64, Nome: comunidadeNome}
	}
	return c, nil
}

func nullIfEmpty(v string) any {
	if v == "" {
		return nil
	}
	return v
}
