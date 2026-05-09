package evento

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

func (r *Repository) FindAll(ctx context.Context) ([]Evento, error) {
	const query = `SELECT id_evento, titulo, COALESCE(nivel, ''), COALESCE(publico_alvo, ''), COALESCE(descricao, ''), data_inicio, data_fim, COALESCE(local, '') FROM tb_evento ORDER BY id_evento`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]Evento, 0)
	for rows.Next() {
		item, err := scanEvento(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (Evento, error) {
	const query = `SELECT id_evento, titulo, COALESCE(nivel, ''), COALESCE(publico_alvo, ''), COALESCE(descricao, ''), data_inicio, data_fim, COALESCE(local, '') FROM tb_evento WHERE id_evento = ? LIMIT 1`
	row := r.db.QueryRowContext(ctx, query, id)
	return scanEvento(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_evento WHERE id_evento = ?)`, id).Scan(&exists); err != nil {
		return false, err
	}
	return exists, nil
}

func (r *Repository) Create(ctx context.Context, e Evento) (int64, error) {
	const query = `INSERT INTO tb_evento (titulo, nivel, publico_alvo, descricao, data_inicio, data_fim, local) VALUES (?, NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''))`
	res, err := r.db.ExecContext(ctx, query, e.Titulo, e.Nivel, e.PublicoAlvo, e.Descricao, e.DataInicio, e.DataFim, e.Local)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, e Evento) error {
	const query = `UPDATE tb_evento SET titulo = ?, nivel = NULLIF(?, ''), publico_alvo = NULLIF(?, ''), descricao = NULLIF(?, ''), data_inicio = NULLIF(?, ''), data_fim = NULLIF(?, ''), local = NULLIF(?, '') WHERE id_evento = ?`
	_, err := r.db.ExecContext(ctx, query, e.Titulo, e.Nivel, e.PublicoAlvo, e.Descricao, e.DataInicio, e.DataFim, e.Local, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_evento WHERE id_evento = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanEvento(s scanner) (Evento, error) {
	var item Evento
	var dataInicio any
	var dataFim any
	if err := s.Scan(&item.IDEvento, &item.Titulo, &item.Nivel, &item.PublicoAlvo, &item.Descricao, &dataInicio, &dataFim, &item.Local); err != nil {
		return Evento{}, err
	}
	item.DataInicio = dateToString(dataInicio)
	item.DataFim = dateToString(dataFim)
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
