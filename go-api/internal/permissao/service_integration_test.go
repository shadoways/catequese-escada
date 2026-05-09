package permissao

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"testing"

	_ "modernc.org/sqlite"
)

func newSQLitePermissaoService(t *testing.T) *Service {
	t.Helper()
	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	schema := []string{
		`CREATE TABLE tb_login (id_login INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT, password_hash TEXT, id_catequista INTEGER)`,
		`CREATE TABLE tb_permissoes (id_permissao INTEGER PRIMARY KEY AUTOINCREMENT, permissao TEXT, id_login INTEGER)`,
		`INSERT INTO tb_login (username, password_hash) VALUES ('user', 'hash')`,
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

func TestPermissaoServiceSQLiteLifecycle(t *testing.T) {
	svc := newSQLitePermissaoService(t)
	ctx := context.Background()

	created, err := svc.Create(ctx, Permissao{Permissao: "EDITAR", Login: &LoginRef{IDLogin: 1}})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}

	updated, err := svc.Update(ctx, created.IDPermissao, Permissao{Permissao: "LER", Login: &LoginRef{IDLogin: 1}})
	if err != nil {
		t.Fatalf("update failed: %v", err)
	}
	if updated.Permissao != "LER" {
		t.Fatalf("unexpected permissao: %s", updated.Permissao)
	}

	if err := svc.Delete(ctx, created.IDPermissao); err != nil {
		t.Fatalf("delete failed: %v", err)
	}
}

func TestPermissaoServiceSQLiteCreateWithoutLogin(t *testing.T) {
	svc := newSQLitePermissaoService(t)
	_, err := svc.Create(context.Background(), Permissao{Permissao: "EDITAR"})
	if !errors.Is(err, sql.ErrNoRows) {
		t.Fatalf("expected sql.ErrNoRows, got %v", err)
	}
}
