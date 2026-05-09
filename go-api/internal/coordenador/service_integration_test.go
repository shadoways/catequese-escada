package coordenador

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"testing"

	_ "modernc.org/sqlite"
)

func newSQLiteCoordenadorService(t *testing.T) *Service {
	t.Helper()
	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	if _, err := db.Exec(`CREATE TABLE tb_coordenador (
		id_coordenador INTEGER PRIMARY KEY AUTOINCREMENT,
		nome TEXT NOT NULL,
		telefone TEXT,
		email TEXT,
		nivel_organizacional TEXT,
		data_nascimento DATE,
		data_inicio DATE,
		ativo BOOLEAN NOT NULL
	)`); err != nil {
		_ = db.Close()
		t.Fatalf("schema setup failed: %v", err)
	}

	t.Cleanup(func() { _ = db.Close() })
	return NewService(NewRepository(db))
}

func TestCoordenadorServiceSQLiteLifecycle(t *testing.T) {
	svc := newSQLiteCoordenadorService(t)
	ctx := context.Background()

	created, err := svc.Create(ctx, Coordenador{Nome: "Maria", Ativo: true})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}

	updated, err := svc.Update(ctx, created.IDCoordenador, Coordenador{Nome: "Maria A.", Ativo: false})
	if err != nil {
		t.Fatalf("update failed: %v", err)
	}
	if updated.Nome != "Maria A." || updated.Ativo {
		t.Fatalf("unexpected updated result: %+v", updated)
	}

	if err := svc.Delete(ctx, created.IDCoordenador); err != nil {
		t.Fatalf("delete failed: %v", err)
	}
}
