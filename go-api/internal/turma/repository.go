package turma

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

func (r *Repository) FindAll(ctx context.Context) ([]Turma, error) {
	const query = `SELECT id_turma, nome, COALESCE(descricao, ''), ano, COALESCE(nivel, ''), id_catequista FROM tb_turma ORDER BY id_turma`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]Turma, 0)
	for rows.Next() {
		item, err := scanTurma(rows)
		if err != nil {
			return nil, err
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return items, nil
}

func (r *Repository) FindByID(ctx context.Context, id int64) (Turma, error) {
	const query = `SELECT id_turma, nome, COALESCE(descricao, ''), ano, COALESCE(nivel, ''), id_catequista FROM tb_turma WHERE id_turma = ? LIMIT 1`
	row := r.db.QueryRowContext(ctx, query, id)
	return scanTurma(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_turma WHERE id_turma = ?)`, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) Create(ctx context.Context, t Turma) (int64, error) {
	const query = `INSERT INTO tb_turma (nome, descricao, ano, nivel, id_catequista) VALUES (?, NULLIF(?, ''), ?, NULLIF(?, ''), ?)`
	res, err := r.db.ExecContext(ctx, query, t.Nome, t.Descricao, t.Ano, t.Nivel, t.IDCatequista)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, t Turma) error {
	const query = `UPDATE tb_turma SET nome = ?, descricao = NULLIF(?, ''), ano = ?, nivel = NULLIF(?, ''), id_catequista = ? WHERE id_turma = ?`
	_, err := r.db.ExecContext(ctx, query, t.Nome, t.Descricao, t.Ano, t.Nivel, t.IDCatequista, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_turma WHERE id_turma = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanTurma(s scanner) (Turma, error) {
	var t Turma
	var ano sql.NullInt64
	var cateq sql.NullInt64
	if err := s.Scan(&t.IDTurma, &t.Nome, &t.Descricao, &ano, &t.Nivel, &cateq); err != nil {
		return Turma{}, err
	}
	if ano.Valid {
		v := ano.Int64
		t.Ano = &v
	}
	if cateq.Valid {
		v := cateq.Int64
		t.IDCatequista = &v
	}
	return t, nil
}
