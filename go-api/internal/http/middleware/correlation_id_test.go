package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestCorrelationIDFromHeadersPrefersCanonicalHeader(t *testing.T) {
	headers := http.Header{}
	headers.Set("X-Request-ID", "legacy-id")
	headers.Set(CorrelationIDHeader, "canonical-id")

	got := CorrelationIDFromHeaders(headers)
	if got != "canonical-id" {
		t.Fatalf("expected canonical-id, got %q", got)
	}
}

func TestRequestLoggerAcceptsLegacyAndReturnsCanonicalHeader(t *testing.T) {
	h := RequestLogger()(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if got := CorrelationIDFromContext(r.Context()); got != "legacy-id" {
			t.Fatalf("expected correlation id in context to be legacy-id, got %q", got)
		}
		w.WriteHeader(http.StatusNoContent)
	}))

	req := httptest.NewRequest(http.MethodGet, "/health", nil)
	req.Header.Set("X-Request-ID", "legacy-id")
	w := httptest.NewRecorder()

	h.ServeHTTP(w, req)

	if got := w.Header().Get(CorrelationIDHeader); got != "legacy-id" {
		t.Fatalf("expected canonical response header with legacy-id, got %q", got)
	}
}
