package coordenador

import (
	"context"
	"database/sql"
	"fmt"
	"time"
)

type Repository struct{ db *sql.DB }

func NewRepository(db *sql.DB) *Repository { return &Repository{db: db} }

func (r *Repository) FindAll(ctx context.Context) ([]Coordenador, error) {
	rows, err := r.db.QueryContext(ctx, `SELECT id_coordenador, nome, COALESCE(telefone, ''), COALESCE(email, ''), COALESCE(nivel_organizacional, ''), data_nascimento, data_inicio, COALESCE(ativo, TRUE) FROM tb_coordenador ORDER BY id_coordenador`)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := make([]Coordenador, 0)
	for rows.Next() {
		item, err := scanCoordenador(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (Coordenador, error) {
	row := r.db.QueryRowContext(ctx, `SELECT id_coordenador, nome, COALESCE(telefone, ''), COALESCE(email, ''), COALESCE(nivel_organizacional, ''), data_nascimento, data_inicio, COALESCE(ativo, TRUE) FROM tb_coordenador WHERE id_coordenador = ? LIMIT 1`, id)
	return scanCoordenador(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_coordenador WHERE id_coordenador = ?)`, id).Scan(&exists)
	return exists, err
}

func (r *Repository) Create(ctx context.Context, c Coordenador) (int64, error) {
	res, err := r.db.ExecContext(ctx, `INSERT INTO tb_coordenador (nome, telefone, email, nivel_organizacional, data_nascimento, data_inicio, ativo) VALUES (?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?)`, c.Nome, c.Telefone, c.Email, c.NivelOrganizacional, c.DataNascimento, c.DataInicio, c.Ativo)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, c Coordenador) error {
	_, err := r.db.ExecContext(ctx, `UPDATE tb_coordenador SET nome=?, telefone=NULLIF(?, ''), email=NULLIF(?, ''), nivel_organizacional=NULLIF(?, ''), data_nascimento=NULLIF(?, ''), data_inicio=NULLIF(?, ''), ativo=? WHERE id_coordenador=?`, c.Nome, c.Telefone, c.Email, c.NivelOrganizacional, c.DataNascimento, c.DataInicio, c.Ativo, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_coordenador WHERE id_coordenador = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanCoordenador(s scanner) (Coordenador, error) {
	var c Coordenador
	var nascimento any
	var inicio any
	if err := s.Scan(&c.IDCoordenador, &c.Nome, &c.Telefone, &c.Email, &c.NivelOrganizacional, &nascimento, &inicio, &c.Ativo); err != nil {
		return Coordenador{}, err
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
