package auth

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"testing"
	"time"

	_ "modernc.org/sqlite"
)

func newSQLiteAuthService(t *testing.T) (*Service, *sql.DB) {
	t.Helper()

	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)

	schema := []string{
		`CREATE TABLE tb_usuario (
			id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			email TEXT NOT NULL UNIQUE,
			password_hash TEXT NOT NULL,
			ativo BOOLEAN NOT NULL,
			ultimo_login DATETIME
		)`,
		`CREATE TABLE tb_usuario_role (
			id_usuario_role INTEGER PRIMARY KEY AUTOINCREMENT,
			id_usuario INTEGER NOT NULL,
			role TEXT NOT NULL
		)`,
		`CREATE TABLE tb_refresh_token (
			id_refresh_token INTEGER PRIMARY KEY AUTOINCREMENT,
			id_usuario INTEGER NOT NULL,
			token_hash TEXT NOT NULL UNIQUE,
			data_expiracao DATETIME NOT NULL,
			revogado BOOLEAN NOT NULL,
			data_revogacao DATETIME
		)`,
		`CREATE TABLE tb_password_reset_token (
			id_token INTEGER PRIMARY KEY AUTOINCREMENT,
			token TEXT NOT NULL UNIQUE,
			id_usuario INTEGER NOT NULL,
			data_expiracao DATETIME NOT NULL,
			usado BOOLEAN NOT NULL,
			data_criacao DATETIME DEFAULT CURRENT_TIMESTAMP
		)`,
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

	jwtSvc, err := NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 5*time.Minute)
	if err != nil {
		t.Fatalf("jwt init error: %v", err)
	}
	return NewService(db, NewRepository(), jwtSvc, 15*time.Minute, 24*time.Hour), db
}

func seedAuthUser(t *testing.T, db *sql.DB, email, rawPassword string, ativo bool, roles []string) int64 {
	t.Helper()

	hash, err := HashPassword(rawPassword)
	if err != nil {
		t.Fatalf("hash password: %v", err)
	}

	res, err := db.Exec(`INSERT INTO tb_usuario (nome, email, password_hash, ativo) VALUES (?, ?, ?, ?)`, "Administrador", email, hash, ativo)
	if err != nil {
		t.Fatalf("insert user: %v", err)
	}
	userID, err := res.LastInsertId()
	if err != nil {
		t.Fatalf("last insert id: %v", err)
	}
	for _, role := range roles {
		if _, err := db.Exec(`INSERT INTO tb_usuario_role (id_usuario, role) VALUES (?, ?)`, userID, role); err != nil {
			t.Fatalf("insert role: %v", err)
		}
	}
	return userID
}

func TestAuthServiceSQLiteLoginRefreshLogoutLifecycle(t *testing.T) {
	svc, db := newSQLiteAuthService(t)
	ctx := context.Background()
	seedAuthUser(t, db, "admin@catequese.com", "admin123", true, []string{"COORDENADOR_PAROQUIAL"})

	loginRes, err := svc.Login(ctx, LoginRequest{Email: " ADMIN@CATEQUESE.COM ", Password: "admin123"})
	if err != nil {
		t.Fatalf("login failed: %v", err)
	}
	if loginRes.Token == "" || loginRes.RefreshToken == "" {
		t.Fatal("expected token and refresh token on login")
	}

	oldRefreshHash := hashToken(loginRes.RefreshToken)
	var oldRevogado bool
	if err := db.QueryRowContext(ctx, `SELECT revogado FROM tb_refresh_token WHERE token_hash = ?`, oldRefreshHash).Scan(&oldRevogado); err != nil {
		t.Fatalf("query login refresh token: %v", err)
	}
	if oldRevogado {
		t.Fatal("expected refresh token to start as not revoked")
	}

	refreshRes, err := svc.Refresh(ctx, RefreshTokenRequest{RefreshToken: loginRes.RefreshToken})
	if err != nil {
		t.Fatalf("refresh failed: %v", err)
	}
	if refreshRes.RefreshToken == "" || refreshRes.RefreshToken == loginRes.RefreshToken {
		t.Fatal("expected rotated refresh token")
	}

	if err := db.QueryRowContext(ctx, `SELECT revogado FROM tb_refresh_token WHERE token_hash = ?`, oldRefreshHash).Scan(&oldRevogado); err != nil {
		t.Fatalf("query old refresh token after rotation: %v", err)
	}
	if !oldRevogado {
		t.Fatal("expected old refresh token to be revoked after rotation")
	}

	newRefreshHash := hashToken(refreshRes.RefreshToken)
	if err := svc.Logout(ctx, RefreshTokenRequest{RefreshToken: refreshRes.RefreshToken}); err != nil {
		t.Fatalf("logout failed: %v", err)
	}
	var newRevogado bool
	if err := db.QueryRowContext(ctx, `SELECT revogado FROM tb_refresh_token WHERE token_hash = ?`, newRefreshHash).Scan(&newRevogado); err != nil {
		t.Fatalf("query new refresh token after logout: %v", err)
	}
	if !newRevogado {
		t.Fatal("expected refresh token to be revoked on logout")
	}

	_, err = svc.Refresh(ctx, RefreshTokenRequest{RefreshToken: refreshRes.RefreshToken})
	if !errors.Is(err, ErrInvalidRefresh) {
		t.Fatalf("expected ErrInvalidRefresh after logout, got %v", err)
	}
}

func TestAuthServiceSQLiteInvalidCredentials(t *testing.T) {
	svc, db := newSQLiteAuthService(t)
	ctx := context.Background()
	seedAuthUser(t, db, "admin@catequese.com", "admin123", true, []string{"COORDENADOR_PAROQUIAL"})

	_, err := svc.Login(ctx, LoginRequest{Email: "admin@catequese.com", Password: "senha-errada"})
	if !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("expected ErrInvalidCredentials, got %v", err)
	}
}

func TestAuthServiceSQLiteRefreshInactiveUser(t *testing.T) {
	svc, db := newSQLiteAuthService(t)
	ctx := context.Background()
	userID := seedAuthUser(t, db, "admin@catequese.com", "admin123", false, []string{"COORDENADOR_PAROQUIAL"})

	expiresAt := time.Now().UTC().Add(1 * time.Hour)
	rawRefresh := "refresh-token-para-inativo"
	if _, err := db.Exec(
		`INSERT INTO tb_refresh_token (id_usuario, token_hash, data_expiracao, revogado) VALUES (?, ?, ?, FALSE)`,
		userID,
		hashToken(rawRefresh),
		expiresAt,
	); err != nil {
		t.Fatalf("insert refresh token: %v", err)
	}

	_, err := svc.Refresh(ctx, RefreshTokenRequest{RefreshToken: rawRefresh})
	if !errors.Is(err, ErrInvalidCredentials) {
		t.Fatalf("expected ErrInvalidCredentials, got %v", err)
	}

	var revoked bool
	if err := db.QueryRowContext(ctx, `SELECT revogado FROM tb_refresh_token WHERE token_hash = ?`, hashToken(rawRefresh)).Scan(&revoked); err != nil {
		t.Fatalf("query refresh token revoked: %v", err)
	}
	if !revoked {
		t.Fatal("expected refresh token to be revoked for inactive user")
	}
}

func TestAuthServiceSQLitePasswordResetRequestAndConfirm(t *testing.T) {
	svc, db := newSQLiteAuthService(t)
	ctx := context.Background()
	userID := seedAuthUser(t, db, "admin@catequese.com", "admin123", true, []string{"COORDENADOR_PAROQUIAL"})

	if err := svc.RequestPasswordReset(ctx, PasswordResetRequest{Email: "ADMIN@CATEQUESE.COM"}); err != nil {
		t.Fatalf("request password reset failed: %v", err)
	}

	var token string
	if err := db.QueryRowContext(ctx, `SELECT token FROM tb_password_reset_token WHERE id_usuario = ? AND usado = FALSE LIMIT 1`, userID).Scan(&token); err != nil {
		t.Fatalf("query reset token failed: %v", err)
	}
	if token == "" {
		t.Fatal("expected a generated reset token")
	}

	if err := svc.ResetPassword(ctx, PasswordResetConfirmRequest{Token: token, NewPassword: "novaSenha123"}); err != nil {
		t.Fatalf("reset password failed: %v", err)
	}

	var used bool
	if err := db.QueryRowContext(ctx, `SELECT usado FROM tb_password_reset_token WHERE token = ?`, token).Scan(&used); err != nil {
		t.Fatalf("query token used failed: %v", err)
	}
	if !used {
		t.Fatal("expected token to be marked as used")
	}

	_, err := svc.Login(ctx, LoginRequest{Email: "admin@catequese.com", Password: "novaSenha123"})
	if err != nil {
		t.Fatalf("expected login with new password to succeed, got %v", err)
	}
}

func TestAuthServiceSQLitePasswordResetInvalidToken(t *testing.T) {
	svc, _ := newSQLiteAuthService(t)
	ctx := context.Background()

	err := svc.ResetPassword(ctx, PasswordResetConfirmRequest{Token: "nao-existe", NewPassword: "novaSenha123"})
	if !errors.Is(err, ErrInvalidResetToken) {
		t.Fatalf("expected ErrInvalidResetToken, got %v", err)
	}
}
