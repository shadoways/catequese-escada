package handlers

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"catequese-escada/go-api/internal/auth"
)

type fakeAuthUseCase struct {
	loginFn                func(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error)
	refreshFn              func(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error)
	logoutFn               func(ctx context.Context, req auth.RefreshTokenRequest) error
	requestPasswordResetFn func(ctx context.Context, req auth.PasswordResetRequest) error
	resetPasswordFn        func(ctx context.Context, req auth.PasswordResetConfirmRequest) error
}

func (f *fakeAuthUseCase) Login(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error) {
	return f.loginFn(ctx, req)
}

func (f *fakeAuthUseCase) Refresh(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error) {
	return f.refreshFn(ctx, req)
}

func (f *fakeAuthUseCase) Logout(ctx context.Context, req auth.RefreshTokenRequest) error {
	return f.logoutFn(ctx, req)
}

func (f *fakeAuthUseCase) RequestPasswordReset(ctx context.Context, req auth.PasswordResetRequest) error {
	return f.requestPasswordResetFn(ctx, req)
}

func (f *fakeAuthUseCase) ResetPassword(ctx context.Context, req auth.PasswordResetConfirmRequest) error {
	return f.resetPasswordFn(ctx, req)
}

func newHandlerForTest(t *testing.T, uc authUseCase) *AuthHandler {
	t.Helper()
	jwtSvc, err := auth.NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 5*time.Minute)
	if err != nil {
		t.Fatalf("jwt init error: %v", err)
	}
	return NewAuthHandler(jwtSvc, uc)
}

func TestLoginValidation(t *testing.T) {
	h := newHandlerForTest(t, &fakeAuthUseCase{
		loginFn: func(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		refreshFn: func(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		logoutFn: func(ctx context.Context, req auth.RefreshTokenRequest) error { return nil },
	})

	body := []byte(`{"email":"invalido","password":"123"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()

	h.Login(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Code)
	}
	if !bytes.Contains(w.Body.Bytes(), []byte(`"erro":"Validação falhou"`)) {
		t.Fatalf("unexpected body: %s", w.Body.String())
	}
}

func TestLoginUnauthorized(t *testing.T) {
	h := newHandlerForTest(t, &fakeAuthUseCase{
		loginFn: func(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, auth.ErrInvalidCredentials
		},
		refreshFn: func(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		logoutFn: func(ctx context.Context, req auth.RefreshTokenRequest) error { return nil },
	})

	body := []byte(`{"email":"admin@catequese.com","password":"senhaErrada"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(body))
	w := httptest.NewRecorder()

	h.Login(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestLoginSuccess(t *testing.T) {
	h := newHandlerForTest(t, &fakeAuthUseCase{
		loginFn: func(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{
				Token:            "jwt.token",
				Email:            "admin@catequese.com",
				Nome:             "Administrador",
				Roles:            []string{"COORDENADOR_PAROQUIAL"},
				ExpiresIn:        900000,
				RefreshToken:     "refresh.token",
				RefreshExpiresIn: 604800000,
			}, nil
		},
		refreshFn: func(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		logoutFn: func(ctx context.Context, req auth.RefreshTokenRequest) error { return nil },
	})

	body := []byte(`{"email":"admin@catequese.com","password":"admin123"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(body))
	w := httptest.NewRecorder()

	h.Login(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	if !bytes.Contains(w.Body.Bytes(), []byte(`"token":"jwt.token"`)) {
		t.Fatalf("unexpected body: %s", w.Body.String())
	}
}

func TestRefreshUnauthorized(t *testing.T) {
	h := newHandlerForTest(t, &fakeAuthUseCase{
		loginFn: func(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		refreshFn: func(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, auth.ErrInvalidRefresh
		},
		logoutFn: func(ctx context.Context, req auth.RefreshTokenRequest) error { return nil },
	})

	body := []byte(`{"refreshToken":"abc"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/refresh", bytes.NewReader(body))
	w := httptest.NewRecorder()

	h.Refresh(w, req)
	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Code)
	}
}

func TestLogoutSuccess(t *testing.T) {
	h := newHandlerForTest(t, &fakeAuthUseCase{
		loginFn: func(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		refreshFn: func(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		logoutFn: func(ctx context.Context, req auth.RefreshTokenRequest) error { return nil },
	})

	body := []byte(`{"refreshToken":"abc"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/logout", bytes.NewReader(body))
	w := httptest.NewRecorder()

	h.Logout(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}

	var payload map[string]string
	if err := json.Unmarshal(w.Body.Bytes(), &payload); err != nil {
		t.Fatalf("invalid json: %v", err)
	}
	if payload["message"] != "Logout realizado com sucesso" {
		t.Fatalf("unexpected payload: %v", payload)
	}
}

func TestLogoutInternalError(t *testing.T) {
	h := newHandlerForTest(t, &fakeAuthUseCase{
		loginFn: func(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		refreshFn: func(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error) {
			return auth.LoginResponse{}, nil
		},
		logoutFn: func(ctx context.Context, req auth.RefreshTokenRequest) error { return errors.New("db down") },
	})

	body := []byte(`{"refreshToken":"abc"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/logout", bytes.NewReader(body))
	w := httptest.NewRecorder()

	h.Logout(w, req)
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500, got %d", w.Code)
	}
}
