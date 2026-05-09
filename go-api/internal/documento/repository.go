package documento

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

func (r *Repository) FindAll(ctx context.Context) ([]Documento, error) {
	const query = `SELECT id_documento, COALESCE(tipo_documento, ''), COALESCE(caminho_arquivo, ''), data_envio, id_catequisando, COALESCE(tipo_status, 'PENDENTE') FROM tb_documento ORDER BY id_documento`
	rows, err := r.db.QueryContext(ctx, query)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	items := make([]Documento, 0)
	for rows.Next() {
		item, err := scanDocumento(rows)
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

func (r *Repository) FindByID(ctx context.Context, id int64) (Documento, error) {
	const query = `SELECT id_documento, COALESCE(tipo_documento, ''), COALESCE(caminho_arquivo, ''), data_envio, id_catequisando, COALESCE(tipo_status, 'PENDENTE') FROM tb_documento WHERE id_documento = ? LIMIT 1`
	row := r.db.QueryRowContext(ctx, query, id)
	return scanDocumento(row)
}

func (r *Repository) ExistsByID(ctx context.Context, id int64) (bool, error) {
	var exists bool
	if err := r.db.QueryRowContext(ctx, `SELECT EXISTS(SELECT 1 FROM tb_documento WHERE id_documento = ?)`, id).Scan(&exists); err != nil {
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

func (r *Repository) Create(ctx context.Context, d Documento) (int64, error) {
	const query = `INSERT INTO tb_documento (tipo_documento, caminho_arquivo, data_envio, id_catequisando, tipo_status) VALUES (NULLIF(?, ''), NULLIF(?, ''), NULLIF(?, ''), ?, NULLIF(?, ''))`
	cateqID := int64(0)
	if d.Catequisando != nil {
		cateqID = d.Catequisando.IDCatequisando
	}
	res, err := r.db.ExecContext(ctx, query, d.TipoDocumento, d.CaminhoArquivo, d.DataEnvio, cateqID, d.TipoStatus)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func (r *Repository) Update(ctx context.Context, id int64, d Documento) error {
	const query = `UPDATE tb_documento SET tipo_documento = NULLIF(?, ''), caminho_arquivo = NULLIF(?, ''), data_envio = NULLIF(?, ''), id_catequisando = ?, tipo_status = NULLIF(?, '') WHERE id_documento = ?`
	cateqID := int64(0)
	if d.Catequisando != nil {
		cateqID = d.Catequisando.IDCatequisando
	}
	_, err := r.db.ExecContext(ctx, query, d.TipoDocumento, d.CaminhoArquivo, d.DataEnvio, cateqID, d.TipoStatus, id)
	return err
}

func (r *Repository) UpdateStatus(ctx context.Context, id int64, status string) error {
	_, err := r.db.ExecContext(ctx, `UPDATE tb_documento SET tipo_status = NULLIF(?, '') WHERE id_documento = ?`, status, id)
	return err
}

func (r *Repository) DeleteByID(ctx context.Context, id int64) error {
	_, err := r.db.ExecContext(ctx, `DELETE FROM tb_documento WHERE id_documento = ?`, id)
	return err
}

type scanner interface{ Scan(dest ...any) error }

func scanDocumento(s scanner) (Documento, error) {
	var item Documento
	var rawData any
	var cateqID sql.NullInt64
	if err := s.Scan(&item.IDDocumento, &item.TipoDocumento, &item.CaminhoArquivo, &rawData, &cateqID, &item.TipoStatus); err != nil {
		return Documento{}, err
	}
	item.DataEnvio = dateToString(rawData)
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
