package middleware

import (
	"fmt"
	"log"
	"net/http"
	"runtime/debug"

	"catequese-escada/go-api/internal/http/response"
)

func Recoverer() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			defer func() {
				if rec := recover(); rec != nil {
					stack := string(debug.Stack())
					correlationID := CorrelationIDFromContext(r.Context())
					log.Printf("panic recovered corr=%s method=%s path=%s panic=%v stack=%s", correlationID, r.Method, r.URL.Path, rec, stack)
					response.Error(w, http.StatusInternalServerError, fmt.Sprintf("panic: %v", rec))
				}
			}()

			next.ServeHTTP(w, r)
		})
	}
}
