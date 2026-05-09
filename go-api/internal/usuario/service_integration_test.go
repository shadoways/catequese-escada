package usuario

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"testing"

	_ "modernc.org/sqlite"
)

func newSQLiteUsuarioService(t *testing.T) (*Service, *sql.DB) {
	t.Helper()

	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	schema := []string{
		`CREATE TABLE tb_comunidade (
			id_comunidade INTEGER PRIMARY KEY,
			nome TEXT NOT NULL
		)`,
		`CREATE TABLE tb_catequista (
			id_catequista INTEGER PRIMARY KEY,
			nome TEXT NOT NULL
		)`,
		`CREATE TABLE tb_usuario (
			id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			email TEXT NOT NULL UNIQUE,
			password_hash TEXT NOT NULL,
			ativo BOOLEAN NOT NULL,
			id_comunidade INTEGER,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_usuario_role (
			id_usuario_role INTEGER PRIMARY KEY AUTOINCREMENT,
			id_usuario INTEGER NOT NULL,
			role TEXT NOT NULL
		)`,
		`INSERT INTO tb_comunidade (id_comunidade, nome) VALUES (10, 'Matriz')`,
		`INSERT INTO tb_catequista (id_catequista, nome) VALUES (20, 'Joao')`,
	}

	for _, stmt := range schema {
		if _, err := db.Exec(stmt); err != nil {
			_ = db.Close()
			t.Fatalf("schema setup failed: %v", err)
		}
	}

	t.Cleanup(func() {
		_ = db.Close()
	})

	repo := NewRepository(db)
	return NewService(db, repo), db
}

func TestUsuarioServiceSQLiteLifecycle(t *testing.T) {
	svc, db := newSQLiteUsuarioService(t)
	ctx := context.Background()

	comID := int64(10)
	catID := int64(20)
	created, err := svc.Create(ctx, CreateUsuarioRequest{
		Nome:         " Usuario Teste ",
		Email:        "USER@EXAMPLE.COM ",
		Password:     "segredo123",
		Roles:        []string{"CATEQUISTA"},
		IDComunidade: &comID,
		IDCatequista: &catID,
	})
	if err != nil {
		t.Fatalf("create failed: %v", err)
	}
	if created.Email != "user@example.com" {
		t.Fatalf("expected normalized email, got %q", created.Email)
	}
	if len(created.Roles) != 1 || created.Roles[0] != "CATEQUISTA" {
		t.Fatalf("unexpected roles after create: %+v", created.Roles)
	}

	var passwordHash string
	if err := db.QueryRowContext(ctx, `SELECT password_hash FROM tb_usuario WHERE id_usuario = ?`, created.IDUsuario).Scan(&passwordHash); err != nil {
		t.Fatalf("query password hash: %v", err)
	}
	if passwordHash == "segredo123" || passwordHash == "" {
		t.Fatalf("password should be hashed, got %q", passwordHash)
	}

	updated, err := svc.Update(ctx, created.IDUsuario, UpdateUsuarioRequest{
		Nome:         "Usuario Atualizado",
		Email:        "novo@example.com",
		Ativo:        false,
		Roles:        []string{"COORDENADOR_COMUNIDADE"},
		IDComunidade: &comID,
		IDCatequista: &catID,
	})
	if err != nil {
		t.Fatalf("update failed: %v", err)
	}
	if updated.Email != "novo@example.com" {
		t.Fatalf("expected updated email, got %q", updated.Email)
	}
	if len(updated.Roles) != 1 || updated.Roles[0] != "COORDENADOR_COMUNIDADE" {
		t.Fatalf("unexpected roles after update: %+v", updated.Roles)
	}
	if updated.Ativo {
		t.Fatal("expected user to be inactive after update")
	}

	toggled, err := svc.ToggleAtivo(ctx, created.IDUsuario)
	if err != nil {
		t.Fatalf("toggle failed: %v", err)
	}
	if !toggled.Ativo {
		t.Fatal("expected user to be active after toggle")
	}

	if err := svc.Delete(ctx, created.IDUsuario); err != nil {
		t.Fatalf("delete failed: %v", err)
	}
	_, err = svc.FindByID(ctx, created.IDUsuario)
	if !errors.Is(err, ErrNotFound) {
		t.Fatalf("expected ErrNotFound after delete, got %v", err)
	}
}

func TestUsuarioServiceSQLiteDuplicateEmail(t *testing.T) {
	svc, _ := newSQLiteUsuarioService(t)
	ctx := context.Background()

	_, err := svc.Create(ctx, CreateUsuarioRequest{
		Nome:     "Primeiro",
		Email:    "duplicado@example.com",
		Password: "segredo123",
		Roles:    []string{"CATEQUISTA"},
	})
	if err != nil {
		t.Fatalf("first create failed: %v", err)
	}

	_, err = svc.Create(ctx, CreateUsuarioRequest{
		Nome:     "Segundo",
		Email:    "DUPLICADO@example.com",
		Password: "segredo123",
		Roles:    []string{"CATEQUISTA"},
	})
	if !errors.Is(err, ErrEmailExists) {
		t.Fatalf("expected ErrEmailExists, got %v", err)
	}
}

func TestUsuarioServiceSQLiteValidateReferences(t *testing.T) {
	svc, _ := newSQLiteUsuarioService(t)
	ctx := context.Background()

	invalidComID := int64(999)
	_, err := svc.Create(ctx, CreateUsuarioRequest{
		Nome:         "Sem Comunidade",
		Email:        "semcom@example.com",
		Password:     "segredo123",
		Roles:        []string{"CATEQUISTA"},
		IDComunidade: &invalidComID,
	})
	if !errors.Is(err, ErrComunidadeNotFound) {
		t.Fatalf("expected ErrComunidadeNotFound, got %v", err)
	}

	invalidCatID := int64(999)
	_, err = svc.Create(ctx, CreateUsuarioRequest{
		Nome:         "Sem Catequista",
		Email:        "semcat@example.com",
		Password:     "segredo123",
		Roles:        []string{"CATEQUISTA"},
		IDCatequista: &invalidCatID,
	})
	if !errors.Is(err, ErrCatequistaNotFound) {
		t.Fatalf("expected ErrCatequistaNotFound, got %v", err)
	}
}

func TestUsuarioServiceSQLiteCreateRollsBackWhenRoleInsertFails(t *testing.T) {
	svc, db := newSQLiteUsuarioService(t)
	ctx := context.Background()

	if _, err := db.Exec(`DROP TABLE tb_usuario_role`); err != nil {
		t.Fatalf("drop role table failed: %v", err)
	}

	_, err := svc.Create(ctx, CreateUsuarioRequest{
		Nome:     "Usuario Com Falha",
		Email:    "rollback@catequese.com",
		Password: "segredo123",
		Roles:    []string{"CATEQUISTA"},
	})
	if err == nil {
		t.Fatal("expected create to fail when role table is missing")
	}

	var count int
	if err := db.QueryRowContext(ctx, `SELECT COUNT(1) FROM tb_usuario WHERE email = ?`, "rollback@catequese.com").Scan(&count); err != nil {
		t.Fatalf("count user after rollback failed: %v", err)
	}
	if count != 0 {
		t.Fatalf("expected rollback to remove user insert, found %d record(s)", count)
	}
}
