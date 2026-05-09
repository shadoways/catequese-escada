package unit

import (
	"testing"
	"time"

	"catequese-escada/go-api/internal/auth"
)

func TestJWTServiceRoundTrip(t *testing.T) {
	svc, err := auth.NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 5*time.Minute)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	token, err := svc.GenerateAccessToken("admin@catequese.com", "Administrador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("unexpected token error: %v", err)
	}

	ok, claims := svc.ValidateToken(token)
	if !ok {
		t.Fatal("expected token to be valid")
	}
	if claims == nil || claims.Email != "admin@catequese.com" {
		t.Fatal("unexpected claims")
	}
}

func TestJWTServiceRejectsShortSecret(t *testing.T) {
	_, err := auth.NewJWTService("short-secret", time.Minute)
	if err == nil {
		t.Fatal("expected error for short jwt secret")
	}
}
