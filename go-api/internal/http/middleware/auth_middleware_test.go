package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"catequese-escada/go-api/internal/auth"
)

func TestJWTAuthBypassesOptionsRequests(t *testing.T) {
	jwtSvc, err := auth.NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 10*time.Minute)
	if err != nil {
		t.Fatalf("jwt init: %v", err)
	}

	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	})
	h := JWTAuth(jwtSvc)(next)

	req := httptest.NewRequest(http.MethodOptions, "/api/catequisandos/", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("expected 204 for preflight, got %d", w.Code)
	}
}

func TestJWTAuthAcceptsLowercaseBearerPrefix(t *testing.T) {
	jwtSvc, err := auth.NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 10*time.Minute)
	if err != nil {
		t.Fatalf("jwt init: %v", err)
	}

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coord", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	h := JWTAuth(jwtSvc)(next)

	req := httptest.NewRequest(http.MethodGet, "/api/catequisandos/", nil)
	req.Header.Set("Authorization", "bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 with lowercase bearer prefix, got %d", w.Code)
	}
}

func TestJWTAuthRejectsMalformedAuthorizationHeader(t *testing.T) {
	jwtSvc, err := auth.NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 10*time.Minute)
	if err != nil {
		t.Fatalf("jwt init: %v", err)
	}

	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	h := JWTAuth(jwtSvc)(next)

	req := httptest.NewRequest(http.MethodGet, "/api/catequisandos/", nil)
	req.Header.Set("Authorization", "Bearer")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 for malformed Authorization header, got %d", w.Code)
	}
}
