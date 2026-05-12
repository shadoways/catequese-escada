package middleware

import (
	"log"
	"net/http"
	"sync/atomic"
	"time"
)

type loggingResponseWriter struct {
	http.ResponseWriter
	statusCode int
	bytes      int
}

func newLoggingResponseWriter(w http.ResponseWriter) *loggingResponseWriter {
	return &loggingResponseWriter{ResponseWriter: w, statusCode: http.StatusOK}
}

func (w *loggingResponseWriter) WriteHeader(statusCode int) {
	w.statusCode = statusCode
	w.ResponseWriter.WriteHeader(statusCode)
}

func (w *loggingResponseWriter) Write(b []byte) (int, error) {
	n, err := w.ResponseWriter.Write(b)
	w.bytes += n
	return n, err
}

var requestCounter uint64

func RequestLogger() func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			requestID := atomic.AddUint64(&requestCounter, 1)
			start := time.Now()
			log.Printf("[req:%d] started method=%s path=%s remote=%s", requestID, r.Method, r.URL.Path, r.RemoteAddr)

			wrapped := newLoggingResponseWriter(w)
			next.ServeHTTP(wrapped, r)

			duration := time.Since(start)
			log.Printf("[req:%d] completed status=%d bytes=%d duration=%s method=%s path=%s", requestID, wrapped.statusCode, wrapped.bytes, duration, r.Method, r.URL.Path)
		})
	}
}
