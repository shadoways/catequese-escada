package conhecimento

import (
	"context"
	"database/sql"
)

type Repository struct{ db *sql.DB }

func NewRepository(db *sql.DB) *Repository { return &Repository{db: db} }

func (r *Repository) FindAll(ctx context.Context) ([]Conhecimento, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT id_conhecimento, COALESCE(area_conhecimento, ''), COALESCE(nivel, ''), COALESCE(descricao, ''), id_catequista FROM tb_conhecimento_catequista ORDER BY id_conhecimento`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Conhecimento, 0)
	for rows.Next() {
		item, err := scanConhecimento(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (Conhecimento, error) {
	row := r.db.QueryRowContext(ctx, `SELECT id_conhecimento, COALESCE(area_conhecimento, ''), COALESCE(nivel, ''), COALESCE(descricao, ''), id_catequista FROM tb_conhecimento_catequista WHERE id_conhecimento = ? LIMIT 1`, id)
	return scanConhecimento(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_conhecimento_catequista WHERE id_conhecimento = ?)`, id).Scan(&exists)
	return exists, err
}

func (r *Repository) ExistsCatequistaID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_catequista WHERE id_catequista = ?)`, id).Scan(&exists)
	return exists, err
}

func (r *Repository) Create(ctx context.Context, c Conhecimento) (int64, error) {
	cateqID := int64(0)
	if c.Catequista != nil {
		cateqID = c.Catequista.IDCatequista
	}
	res, err := r.db.ExecContext(ctx, `INSERT INTO tb_conhecimento_catequista (area_conhecimento, nivel, descricao, id_catequista) VALUES (NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?)`, c.AreaConhecimento, c.Nivel, c.Descricao, cateqID)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, c Conhecimento) error {
	cateqID := int64(0)
	if c.Catequista != nil {
		cateqID = c.Catequista.IDCatequista
	}
	_, err := r.db.ExecContext(ctx, `UPDATE tb_conhecimento_catequista SET area_conhecimento=NULLIF(?, ''), nivel=NULLIF(?, ''), descricao=NULLIF(?, ''), id_catequista=? WHERE id_conhecimento=?`, c.AreaConhecimento, c.Nivel, c.Descricao, cateqID, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_conhecimento_catequista WHERE id_conhecimento = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanConhecimento(s scanner) (Conhecimento, error) {
	var c Conhecimento
	var cateq sql.NullInt64
	if err := s.Scan(&c.IDConhecimento, &c.AreaConhecimento, &c.Nivel, &c.Descricao, &cateq); err != nil {
		return Conhecimento{}, err
	}
	if cateq.Valid {
		c.Catequista = &CatequistaRef{IDCatequista: cateq.Int64}
	}
	return c, nil
}
