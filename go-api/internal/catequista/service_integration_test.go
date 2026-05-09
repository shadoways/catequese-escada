package catequista

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"testing"

	_ "modernc.org/sqlite"
)

func newSQLiteCatequistaService(t *testing.T) (*Service, *sql.DB) {
	t.Helper()
	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	if _, err := db.Exec(`CREATE TABLE tb_catequista (
		id_catequista INTEGER PRIMARY KEY AUTOINCREMENT,
		nome TEXT NOT NULL,
		telefone TEXT,
		email TEXT,
		endereco TEXT,
		data_nascimento DATE,
		data_inicio DATE,
		ativo BOOLEAN NOT NULL
	)`); err != nil {
		_ = db.Close()
		t.Fatalf("schema setup failed: %v", err)
	}

	t.Cleanup(func() { _ = db.Close() })
	return NewService(NewRepository(db)), db
}

func TestCatequistaServiceSQLiteLifecycle(t *testing.T) {
	svc, _ := newSQLiteCatequistaService(t)
	ctx := context.Background()

	created, err := svc.Create(ctx, Catequista{Nome: "Joao", Ativo: true})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}
	if created.IDCatequista <= 0 {
		t.Fatalf("expected ID > 0, got %d", created.IDCatequista)
	}

	updated, err := svc.Update(ctx, created.IDCatequista, Catequista{Nome: "Joao Silva", Ativo: false})
	if err != nil {
		t.Fatalf("update failed: %v", err)
	}
	if updated.Nome != "Joao Silva" || updated.Ativo {
		t.Fatalf("unexpected updated result: %+v", updated)
	}

	if err := svc.Delete(ctx, created.IDCatequista); err != nil {
		t.Fatalf("delete failed: %v", err)
	}

	_, err = svc.FindByID(ctx, created.IDCatequista)
	if err == nil {
		t.Fatal("expected not found after delete")
	}
}
