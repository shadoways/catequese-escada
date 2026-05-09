package permissao

import (
	"context"
	"database/sql"
)

type Repository struct{ db *sql.DB }

func NewRepository(db *sql.DB) *Repository { return &Repository{db: db} }

func (r *Repository) FindAll(ctx context.Context) ([]Permissao, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT id_permissao, COALESCE(permissao, ''), id_login FROM tb_permissoes ORDER BY id_permissao`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Permissao, 0)
	for rows.Next() {
		item, err := scanPermissao(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (Permissao, error) {
	row := r.db.QueryRowContext(ctx, `SELECT id_permissao, COALESCE(permissao, ''), id_login FROM tb_permissoes WHERE id_permissao = ? LIMIT 1`, id)
	return scanPermissao(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_permissoes WHERE id_permissao = ?)`, id).Scan(&exists)
	return exists, err
}

func (r *Repository) ExistsLoginID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_login WHERE id_login = ?)`, id).Scan(&exists)
	return exists, err
}

func (r *Repository) Create(ctx context.Context, p Permissao) (int64, error) {
	loginID := int64(0)
	if p.Login != nil {
		loginID = p.Login.IDLogin
	}
	res, err := r.db.ExecContext(ctx, `INSERT INTO tb_permissoes (permissao, id_login) VALUES (NULLIF(?, ''), ?)`, p.Permissao, loginID)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, p Permissao) error {
	loginID := int64(0)
	if p.Login != nil {
		loginID = p.Login.IDLogin
	}
	_, err := r.db.ExecContext(ctx, `UPDATE tb_permissoes SET permissao=NULLIF(?, ''), id_login=? WHERE id_permissao=?`, p.Permissao, loginID, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_permissoes WHERE id_permissao = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanPermissao(s scanner) (Permissao, error) {
	var p Permissao
	var loginID sql.NullInt64
	if err := s.Scan(&p.IDPermissao, &p.Permissao, &loginID); err != nil {
		return Permissao{}, err
	}
	if loginID.Valid {
		p.Login = &LoginRef{IDLogin: loginID.Int64}
	}
	return p, nil
}
