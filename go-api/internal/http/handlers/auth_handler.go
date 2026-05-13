package handlers

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/mail"
	"strings"

	"catequese-escada/go-api/internal/auth"
	"catequese-escada/go-api/internal/http/response"
)

type AuthHandler struct {
	jwtService  *auth.JWTService
	authService authUseCase
}

type authUseCase interface {
	Login(ctx context.Context, req auth.LoginRequest) (auth.LoginResponse, error)
	Refresh(ctx context.Context, req auth.RefreshTokenRequest) (auth.LoginResponse, error)
	Logout(ctx context.Context, req auth.RefreshTokenRequest) error
	RequestPasswordReset(ctx context.Context, req auth.PasswordResetRequest) error
	ResetPassword(ctx context.Context, req auth.PasswordResetConfirmRequest) error
}

func NewAuthHandler(jwtService *auth.JWTService, authService authUseCase) *AuthHandler {
	return &AuthHandler{jwtService: jwtService, authService: authService}
}

func (h *AuthHandler) Health(w http.ResponseWriter, _ *http.Request) {
	response.JSON(w, http.StatusOK, map[string]string{
		"status": "UP",
		"module": "authentication",
	})
}

func (h *AuthHandler) Validate(w http.ResponseWriter, r *http.Request) {
	token, ok := extractBearerToken(r.Header.Get("Authorization"))
	if !ok {
		response.JSON(w, http.StatusBadRequest, map[string]bool{"valid": false})
		return
	}

	valid, _ := h.jwtService.ValidateToken(token)
	response.JSON(w, http.StatusOK, map[string]bool{"valid": valid})
}

func extractBearerToken(authHeader string) (string, bool) {
	parts := strings.Fields(strings.TrimSpace(authHeader))
	if len(parts) != 2 || !strings.EqualFold(parts[0], "Bearer") {
		return "", false
	}
	if parts[1] == "" {
		return "", false
	}
	return parts[1], true
}

func (h *AuthHandler) Login(w http.ResponseWriter, r *http.Request) {
	var req auth.LoginRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	validation := map[string]string{}
	if strings.TrimSpace(req.Email) == "" {
		validation["email"] = "Email é obrigatório"
	} else if _, err := mail.ParseAddress(strings.TrimSpace(req.Email)); err != nil {
		validation["email"] = "Email inválido"
	}
	if strings.TrimSpace(req.Password) == "" {
		validation["password"] = "Senha é obrigatória"
	} else if len(strings.TrimSpace(req.Password)) < 6 {
		validation["password"] = "Senha deve ter no mínimo 6 caracteres"
	}
	if len(validation) > 0 {
		response.JSON(w, http.StatusBadRequest, map[string]any{
			"erro":     "Validação falhou",
			"detalhes": validation,
		})
		return
	}

	res, err := h.authService.Login(r.Context(), req)
	if err != nil {
		if errors.Is(err, auth.ErrInvalidCredentials) {
			response.Error(w, http.StatusUnauthorized, err.Error())
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	response.JSON(w, http.StatusOK, res)
}

func (h *AuthHandler) Refresh(w http.ResponseWriter, r *http.Request) {
	var req auth.RefreshTokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	if strings.TrimSpace(req.RefreshToken) == "" {
		response.JSON(w, http.StatusBadRequest, map[string]any{
			"erro":     "Validação falhou",
			"detalhes": map[string]string{"refreshToken": "Refresh token é obrigatório"},
		})
		return
	}

	res, err := h.authService.Refresh(r.Context(), req)
	if err != nil {
		if errors.Is(err, auth.ErrInvalidRefresh) || errors.Is(err, auth.ErrInvalidCredentials) {
			response.Error(w, http.StatusUnauthorized, err.Error())
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	response.JSON(w, http.StatusOK, res)
}

func (h *AuthHandler) Logout(w http.ResponseWriter, r *http.Request) {
	var req auth.RefreshTokenRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	if strings.TrimSpace(req.RefreshToken) == "" {
		response.JSON(w, http.StatusBadRequest, map[string]any{
			"erro":     "Validação falhou",
			"detalhes": map[string]string{"refreshToken": "Refresh token é obrigatório"},
		})
		return
	}

	if err := h.authService.Logout(r.Context(), req); err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	response.JSON(w, http.StatusOK, map[string]string{"message": "Logout realizado com sucesso"})
}

func (h *AuthHandler) RequestPasswordReset(w http.ResponseWriter, r *http.Request) {
	var req auth.PasswordResetRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	validation := map[string]string{}
	if strings.TrimSpace(req.Email) == "" {
		validation["email"] = "Email é obrigatório"
	} else if _, err := mail.ParseAddress(strings.TrimSpace(req.Email)); err != nil {
		validation["email"] = "Email inválido"
	}
	if len(validation) > 0 {
		response.JSON(w, http.StatusBadRequest, map[string]any{
			"erro":     "Validação falhou",
			"detalhes": validation,
		})
		return
	}

	if err := h.authService.RequestPasswordReset(r.Context(), req); err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	response.JSON(w, http.StatusOK, map[string]string{
		"message": "Se o email estiver cadastrado, você receberá instruções para redefinir sua senha.",
	})
}

func (h *AuthHandler) ConfirmPasswordReset(w http.ResponseWriter, r *http.Request) {
	var req auth.PasswordResetConfirmRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	validation := map[string]string{}
	if strings.TrimSpace(req.Token) == "" {
		validation["token"] = "Token é obrigatório"
	}
	if strings.TrimSpace(req.NewPassword) == "" {
		validation["newPassword"] = "Nova senha é obrigatória"
	} else if len(strings.TrimSpace(req.NewPassword)) < 6 {
		validation["newPassword"] = "Senha deve ter no mínimo 6 caracteres"
	}
	if len(validation) > 0 {
		response.JSON(w, http.StatusBadRequest, map[string]any{
			"erro":     "Validação falhou",
			"detalhes": validation,
		})
		return
	}

	err := h.authService.ResetPassword(r.Context(), req)
	if err != nil {
		switch {
		case errors.Is(err, auth.ErrInvalidResetToken), errors.Is(err, auth.ErrResetTokenUsed), errors.Is(err, auth.ErrResetTokenExpired), errors.Is(err, auth.ErrWeakPassword):
			response.Error(w, http.StatusBadRequest, err.Error())
		default:
			response.Error(w, http.StatusInternalServerError, "Erro interno")
		}
		return
	}

	response.JSON(w, http.StatusOK, map[string]string{"message": "Senha alterada com sucesso"})
}

func (h *AuthHandler) NotImplementedPublic(w http.ResponseWriter, _ *http.Request) {
	response.Error(w, http.StatusNotImplemented, "Endpoint ainda não migrado para Go")
}

func (h *AuthHandler) NotImplementedProtected(w http.ResponseWriter, _ *http.Request) {
	response.Error(w, http.StatusNotImplemented, "Endpoint protegido ainda não migrado para Go")
}
