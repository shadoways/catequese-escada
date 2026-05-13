package middleware

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"strings"
)

const CorrelationIDHeader = "X-Correlation-ID"

var acceptedCorrelationIDHeaders = []string{
	CorrelationIDHeader,
	"X-Request-ID",
}

type correlationIDContextKey struct{}

func WithCorrelationID(ctx context.Context, correlationID string) context.Context {
	return context.WithValue(ctx, correlationIDContextKey{}, strings.TrimSpace(correlationID))
}

func CorrelationIDFromContext(ctx context.Context) string {
	if ctx == nil {
		return ""
	}
	id, _ := ctx.Value(correlationIDContextKey{}).(string)
	return strings.TrimSpace(id)
}

func EnsureCorrelationID(id string) string {
	trimmed := strings.TrimSpace(id)
	if trimmed != "" {
		return trimmed
	}
	return generateCorrelationID()
}

func CorrelationIDFromHeaders(header http.Header) string {
	for _, key := range acceptedCorrelationIDHeaders {
		if id := strings.TrimSpace(header.Get(key)); id != "" {
			return id
		}
	}
	return ""
}

func generateCorrelationID() string {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		// Fallback ID should still be deterministic in shape for logs.
		return "00000000000000000000000000000000"
	}
	return hex.EncodeToString(buf)
}
