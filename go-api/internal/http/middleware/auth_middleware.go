package middleware

import (
	"context"
	"net/http"
	"strings"

	"catequese-escada/go-api/internal/auth"
	"catequese-escada/go-api/internal/http/response"
)

type contextKey string

const (
	contextEmail contextKey = "email"
	contextRoles contextKey = "roles"
)

func JWTAuth(jwtService *auth.JWTService) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			authHeader := strings.TrimSpace(r.Header.Get("Authorization"))
			if !strings.HasPrefix(authHeader, "Bearer ") {
				response.Error(w, http.StatusUnauthorized, "Não autenticado")
				return
			}

			token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
			valid, claims := jwtService.ValidateToken(token)
			if !valid || claims == nil {
				response.Error(w, http.StatusUnauthorized, "Não autenticado")
				return
			}

			ctx := context.WithValue(r.Context(), contextEmail, claims.Email)
			ctx = context.WithValue(ctx, contextRoles, claims.Roles)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func RequireRole(required string) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			roles, _ := r.Context().Value(contextRoles).([]string)
			for _, role := range roles {
				if role == required {
					next.ServeHTTP(w, r)
					return
				}
			}
			response.Error(w, http.StatusForbidden, "Acesso negado")
		})
	}
}
