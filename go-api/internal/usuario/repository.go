package usuario

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

func (r *Repository) FindAll(ctx context.Context) ([]usuarioDB, error) {
	const query = `
SELECT id_usuario, nome, email, password_hash, ativo, id_comunidade, id_catequista
FROM tb_usuario
ORDER BY id_usuario ASC`

	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]usuarioDB, 0)
	for rows.Next() {
		item, err := scanUsuarioDB(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (usuarioDB, error) {
	const query = `
SELECT id_usuario, nome, email, password_hash, ativo, id_comunidade, id_catequista
FROM tb_usuario
WHERE id_usuario = ?
LIMIT 1`
	row := r.db.QueryRowContext(ctx, query, id)
	return scanUsuarioDB(row)
}

func (r *Repository) FindByEmail(ctx context.Context, email string) (usuarioDB, error) {
	const query = `
SELECT id_usuario, nome, email, password_hash, ativo, id_comunidade, id_catequista
FROM tb_usuario
WHERE email = ?
LIMIT 1`
	row := r.db.QueryRowContext(ctx, query, email)
	return scanUsuarioDB(row)
}

func (r *Repository) ExistsByEmail(ctx context.Context, email string) (bool, error) {
	const query = `SELECT EXISTS(SELECT 1 FROM tb_usuario WHERE email = ?)`
	var exists bool
	if err := r.db.QueryRowContext(ctx, query, email).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	const query = `SELECT EXISTS(SELECT 1 FROM tb_usuario WHERE id_usuario = ?)`
	var exists bool
	if err := r.db.QueryRowContext(ctx, query, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) Insert(ctx context.Context, tx *sql.Tx, u usuarioDB) (int64, error) {
	const query = `
INSERT INTO tb_usuario (nome, email, password_hash, ativo, id_comunidade, id_catequista)
VALUES (?, ?, ?, ?, ?, ?)`
	res, err := tx.ExecContext(ctx, query, u.Nome, u.Email, u.PasswordHash, u.Ativo, u.IDComunidade, u.IDCatequista)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, tx *sql.Tx, id int64, u usuarioDB) error {
	const query = `
UPDATE tb_usuario
SET nome = ?, email = ?, ativo = ?, id_comunidade = ?, id_catequista = ?
WHERE id_usuario = ?`
	_, err := tx.ExecContext(ctx, query, u.Nome, u.Email, u.Ativo, u.IDComunidade, u.IDCatequista, id)
	return err
}

func (r *Repository) ToggleAtivo(ctx context.Context, id int64, ativo bool) error {
	_, err := r.db.ExecContext(ctx, `UPDATE tb_usuario SET ativo = ? WHERE id_usuario = ?`, ativo, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_usuario WHERE id_usuario = ?`, id)
	return err
}

func (r *Repository) ListRolesByUserID(ctx context.Context, userID int64) ([]string, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT role FROM tb_usuario_role WHERE id_usuario = ? ORDER BY id_usuario_role`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	roles := make([]string, 0)
	for rows.Next() {
		var role string
		if err := rows.Scan(&role); err != nil {
			return nil, err
		}
		roles = append(roles, role)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return roles, nil
}

func (r *Repository) ReplaceRoles(ctx context.Context, tx *sql.Tx, userID int64, roles []string) error {
	if _, err := tx.ExecContext(ctx, `DELETE FROM tb_usuario_role WHERE id_usuario = ?`, userID); err != nil {
		return err
	}
	for _, role := range roles {
		if _, err := tx.ExecContext(ctx, `INSERT INTO tb_usuario_role (id_usuario, role) VALUES (?, ?)`, userID, role); err != nil {
			return err
		}
	}
	return nil
}

func (r *Repository) ExistsComunidade(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_comunidade WHERE id_comunidade = ?)`, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) ExistsCatequista(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_catequista WHERE id_catequista = ?)`, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

type scanner interface{ Scan(dest ...any) error }

func scanUsuarioDB(s scanner) (usuarioDB, error) {
	var u usuarioDB
	var idComunidade sql.NullInt64
	var idCatequista sql.NullInt64
	if err := s.Scan(&u.IDUsuario, &u.Nome, &u.Email, &u.PasswordHash, &u.Ativo, &idComunidade, &idCatequista); err != nil {
		return usuarioDB{}, err
	}
	if idComunidade.Valid {
		v := idComunidade.Int64
		u.IDComunidade = &v
	}
	if idCatequista.Valid {
		v := idCatequista.Int64
		u.IDCatequista = &v
	}
	return u, nil
}
