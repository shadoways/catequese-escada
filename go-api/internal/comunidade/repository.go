package comunidade

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

func (r *Repository) FindAll(ctx context.Context) ([]Comunidade, error) {
	const query = `SELECT id_comunidade, nome, COALESCE(descricao, ''), COALESCE(ativo, TRUE) FROM tb_comunidade ORDER BY id_comunidade`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Comunidade, 0)
	for rows.Next() {
		var c Comunidade
		if err := rows.Scan(&c.IDComunidade, &c.Nome, &c.Descricao, &c.Ativo); err != nil {
			return nil, err
		}
		items = append(items, c)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return items, nil
}

func (r *Repository) FindByID(ctx context.Context, id int64) (Comunidade, error) {
	const query = `SELECT id_comunidade, nome, COALESCE(descricao, ''), COALESCE(ativo, TRUE) FROM tb_comunidade WHERE id_comunidade = ? LIMIT 1`
	var c Comunidade
	err := r.db.QueryRowContext(ctx, query, id).Scan(&c.IDComunidade, &c.Nome, &c.Descricao, &c.Ativo)
	if err != nil {
		return Comunidade{}, err
	}
	return c, nil
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_comunidade WHERE id_comunidade = ?)`, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) Create(ctx context.Context, c Comunidade) (int64, error) {
	const query = `INSERT INTO tb_comunidade (nome, descricao, ativo) VALUES (?, NULLIF(?, ''), ?)`
	res, err := r.db.ExecContext(ctx, query, c.Nome, c.Descricao, c.Ativo)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, c Comunidade) error {
	const query = `UPDATE tb_comunidade SET nome = ?, descricao = NULLIF(?, ''), ativo = ? WHERE id_comunidade = ?`
	_, err := r.db.ExecContext(ctx, query, c.Nome, c.Descricao, c.Ativo, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_comunidade WHERE id_comunidade = ?`, id)
	return err
}
