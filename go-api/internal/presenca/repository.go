package presenca

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

func (r *Repository) FindAll(ctx context.Context) ([]Presenca, error) {
	const query = `SELECT id_presenca, data, COALESCE(presente, FALSE), id_catequisando FROM tb_presenca ORDER BY id_presenca`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]Presenca, 0)
	for rows.Next() {
		item, err := scanPresenca(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (Presenca, error) {
	const query = `SELECT id_presenca, data, COALESCE(presente, FALSE), id_catequisando FROM tb_presenca WHERE id_presenca = ? LIMIT 1`
	row := r.db.QueryRowContext(ctx, query, id)
	return scanPresenca(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_presenca WHERE id_presenca = ?)`, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) ExistsCatequisandoID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_catequisando WHERE id_catequisando = ?)`, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) Create(ctx context.Context, p Presenca) (int64, error) {
	const query = `INSERT INTO tb_presenca (data, presente, id_catequisando) VALUES (NULLIF(?, ''), ?, ?)`
	cateqID := int64(0)
	if p.Catequisando != nil {
		cateqID = p.Catequisando.IDCatequisando
	}
	res, err := r.db.ExecContext(ctx, query, p.Data, p.Presente, cateqID)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, p Presenca) error {
	const query = `UPDATE tb_presenca SET data = NULLIF(?, ''), presente = ?, id_catequisando = ? WHERE id_presenca = ?`
	cateqID := int64(0)
	if p.Catequisando != nil {
		cateqID = p.Catequisando.IDCatequisando
	}
	_, err := r.db.ExecContext(ctx, query, p.Data, p.Presente, cateqID, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_presenca WHERE id_presenca = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanPresenca(s scanner) (Presenca, error) {
	var item Presenca
	var rawData any
	var presente bool
	var cateqID sql.NullInt64
	if err := s.Scan(&item.IDPresenca, &rawData, &presente, &cateqID); err != nil {
		return Presenca{}, err
	}
	item.Data = dateToString(rawData)
	item.Presente = &presente
	if cateqID.Valid {
		item.Catequisando = &CatequisandoRef{IDCatequisando: cateqID.Int64}
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
