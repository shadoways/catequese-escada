package ficha

import (
	"context"
	"database/sql"
	"fmt"
	"time"
)

type Repository struct {
	db *sql.DB
}

func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

func (r *Repository) FindAll(ctx context.Context) ([]fichaDB, error) {
	const query = `
SELECT id_ficha, data_inscricao, observacoes, id_catequisando
FROM tb_ficha_inscricao
ORDER BY id_ficha ASC`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	result := make([]fichaDB, 0)
	for rows.Next() {
		item, err := scanFicha(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (fichaDB, error) {
	const query = `
SELECT id_ficha, data_inscricao, observacoes, id_catequisando
FROM tb_ficha_inscricao
WHERE id_ficha = ?
LIMIT 1`
	row := r.db.QueryRowContext(ctx, query, id)
	return scanFicha(row)
}

func (r *Repository) Create(ctx context.Context, f fichaDB) (int64, error) {
	const query = `
INSERT INTO tb_ficha_inscricao (data_inscricao, observacoes, id_catequisando)
VALUES (NULLIF(?, ''), NULLIF(?, ''), ?)`
	res, err := r.db.ExecContext(ctx, query, f.DataInscricao, f.Observacoes, f.CatequisandoID)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, f fichaDB) error {
	const query = `
UPDATE tb_ficha_inscricao
SET data_inscricao = NULLIF(?, ''), observacoes = NULLIF(?, ''), id_catequisando = ?
WHERE id_ficha = ?`
	_, err := r.db.ExecContext(ctx, query, f.DataInscricao, f.Observacoes, f.CatequisandoID, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_ficha_inscricao WHERE id_ficha = ?`, id)
	return err
}

func (r *Repository) DeleteByCatequisandoID(ctx context.Context, catequisandoID int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_ficha_inscricao WHERE id_catequisando = ?`, catequisandoID)
	return err
}

func (r *Repository) ExistsCatequisandoID(ctx context.Context, catequisandoID int64) (bool, error) {
	const query = `SELECT EXISTS(SELECT 1 FROM tb_catequisando WHERE id_catequisando = ?)`
	var exists bool
	if err := r.db.QueryRowContext(ctx, query, catequisandoID).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

type scanner interface{ Scan(dest ...any) error }

func scanFicha(s scanner) (fichaDB, error) {
	var item fichaDB
	var dataRaw any
	var obs sql.NullString
	var cateq sql.NullInt64
	if err := s.Scan(&item.IDFicha, &dataRaw, &obs, &cateq); err != nil {
		return fichaDB{}, err
	}
	item.DataInscricao = dateToString(dataRaw)
	if obs.Valid {
		item.Observacoes = obs.String
	}
	if cateq.Valid {
		v := cateq.Int64
		item.CatequisandoID = &v
	}
	return item, nil
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
