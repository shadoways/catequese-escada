package middleware

import (
	"net/http"
	"net/url"
	"strings"
)

const corsAllowHeaders = "Authorization, Content-Type, X-Correlation-ID, X-Request-ID"
const corsAllowMethods = "GET, POST, PUT, PATCH, DELETE, OPTIONS"

func CORS(appEnv string, allowedOrigins []string) func(http.Handler) http.Handler {
	env := strings.ToLower(strings.TrimSpace(appEnv))
	normalized := make([]string, 0, len(allowedOrigins))
	for _, origin := range allowedOrigins {
		trimmed := strings.TrimSpace(origin)
		if trimmed != "" {
			normalized = append(normalized, trimmed)
		}
	}

	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			origin := strings.TrimSpace(r.Header.Get("Origin"))
			if origin == "" {
				next.ServeHTTP(w, r)
				return
			}

			if !isOriginAllowed(origin, env, normalized) {
				if r.Method == http.MethodOptions {
					w.WriteHeader(http.StatusForbidden)
					return
				}
				next.ServeHTTP(w, r)
				return
			}

			w.Header().Set("Access-Control-Allow-Origin", origin)
			w.Header().Set("Vary", "Origin")
			w.Header().Set("Access-Control-Allow-Headers", corsAllowHeaders)
			w.Header().Set("Access-Control-Allow-Methods", corsAllowMethods)

			if r.Method == http.MethodOptions {
				w.WriteHeader(http.StatusNoContent)
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}

func isOriginAllowed(origin, appEnv string, allowList []string) bool {
	for _, allowed := range allowList {
		if allowed == "*" || strings.EqualFold(allowed, origin) {
			return true
		}
	}

	if !isDevelopmentEnv(appEnv) {
		return false
	}

	u, err := url.Parse(origin)
	if err != nil {
		return false
	}
	hostname := strings.ToLower(strings.TrimSpace(u.Hostname()))
	return hostname == "localhost" || hostname == "127.0.0.1"
}

func isDevelopmentEnv(appEnv string) bool {
	switch strings.ToLower(strings.TrimSpace(appEnv)) {
	case "dev", "local", "development":
		return true
	default:
		return false
	}
}
