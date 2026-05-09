package conhecimento

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"testing"

	_ "modernc.org/sqlite"
)

func newSQLiteConhecimentoService(t *testing.T) *Service {
	t.Helper()
	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	schema := []string{
		`CREATE TABLE tb_catequista (id_catequista INTEGER PRIMARY KEY AUTOINCREMENT, nome TEXT, ativo BOOLEAN)`,
		`CREATE TABLE tb_conhecimento_catequista (id_conhecimento INTEGER PRIMARY KEY AUTOINCREMENT, area_conhecimento TEXT, nivel TEXT, descricao TEXT, id_catequista INTEGER)`,
		`INSERT INTO tb_catequista (nome, ativo) VALUES ('Catequista 1', TRUE)`,
	}
	for _, stmt := range schema {
		if _, err := db.Exec(stmt); err != nil {
			_ = db.Close()
			t.Fatalf("schema setup failed: %v", err)
		}
	}

	t.Cleanup(func() { _ = db.Close() })
	return NewService(NewRepository(db))
}

func TestConhecimentoServiceSQLiteLifecycle(t *testing.T) {
	svc := newSQLiteConhecimentoService(t)
	ctx := context.Background()

	created, err := svc.Create(ctx, Conhecimento{AreaConhecimento: "Liturgia", Catequista: &CatequistaRef{IDCatequista: 1}})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}

	updated, err := svc.Update(ctx, created.IDConhecimento, Conhecimento{AreaConhecimento: "Biblia", Catequista: &CatequistaRef{IDCatequista: 1}})
	if err != nil {
		t.Fatalf("update failed: %v", err)
	}
	if updated.AreaConhecimento != "Biblia" {
		t.Fatalf("unexpected area: %s", updated.AreaConhecimento)
	}

	if err := svc.Delete(ctx, created.IDConhecimento); err != nil {
		t.Fatalf("delete failed: %v", err)
	}
}

func TestConhecimentoServiceSQLiteCreateWithoutCatequista(t *testing.T) {
	svc := newSQLiteConhecimentoService(t)
	_, err := svc.Create(context.Background(), Conhecimento{AreaConhecimento: "Liturgia"})
	if !errors.Is(err, sql.ErrNoRows) {
		t.Fatalf("expected sql.ErrNoRows, got %v", err)
	}
}
