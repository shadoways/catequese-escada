package catequista

import (
	"context"
	"database/sql"
	"fmt"
	"time"
)

type Repository struct{ db *sql.DB }

func NewRepository(db *sql.DB) *Repository { return &Repository{db: db} }

func (r *Repository) FindAll(ctx context.Context) ([]Catequista, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT id_catequista, nome, COALESCE(telefone, ''), COALESCE(email, ''), COALESCE(endereco, ''), data_nascimento, data_inicio, COALESCE(ativo, TRUE) FROM tb_catequista ORDER BY id_catequista`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Catequista, 0)
	for rows.Next() {
		item, err := scanCatequista(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (Catequista, error) {
	row := r.db.QueryRowContext(ctx, `SELECT id_catequista, nome, COALESCE(telefone, ''), COALESCE(email, ''), COALESCE(endereco, ''), data_nascimento, data_inicio, COALESCE(ativo, TRUE) FROM tb_catequista WHERE id_catequista = ? LIMIT 1`, id)
	return scanCatequista(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_catequista WHERE id_catequista = ?)`, id).Scan(&exists)
	return exists, err
}

func (r *Repository) Create(ctx context.Context, c Catequista) (int64, error) {
	res, err := r.db.ExecContext(ctx, `INSERT INTO tb_catequista (nome, telefone, email, endereco, data_nascimento, data_inicio, ativo) VALUES (?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?)`, c.Nome, c.Telefone, c.Email, c.Endereco, c.DataNascimento, c.DataInicio, c.Ativo)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, c Catequista) error {
	_, err := r.db.ExecContext(ctx, `UPDATE tb_catequista SET nome=?, telefone=NULLIF(?, ''), email=NULLIF(?, ''), endereco=NULLIF(?, ''), data_nascimento=NULLIF(?, ''), data_inicio=NULLIF(?, ''), ativo=? WHERE id_catequista=?`, c.Nome, c.Telefone, c.Email, c.Endereco, c.DataNascimento, c.DataInicio, c.Ativo, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_catequista WHERE id_catequista = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanCatequista(s scanner) (Catequista, error) {
	var c Catequista
	var nascimento any
	var inicio any
	if err := s.Scan(&c.IDCatequista, &c.Nome, &c.Telefone, &c.Email, &c.Endereco, &nascimento, &inicio, &c.Ativo); err != nil {
		return Catequista{}, err
	}
	c.DataNascimento = dateToString(nascimento)
	c.DataInicio = dateToString(inicio)
	return c, nil
}

func dateToString(raw any) string {
	switch v := raw.(type) {
	case nil:
		return ""
	case time.Time:
		return v.Format("2006-01-02")
	case []byte:
		return string(v)
	case string:
		return v
	default:
		return fmt.Sprintf("%v", v)
	}
}
