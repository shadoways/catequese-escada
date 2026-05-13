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
			// Preflight requests do not carry bearer credentials.
			if r.Method == http.MethodOptions {
				next.ServeHTTP(w, r)
				return
			}

			token, ok := extractBearerToken(r.Header.Get("Authorization"))
			if !ok {
				response.Error(w, http.StatusUnauthorized, "Não autenticado")
				return
			}

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
