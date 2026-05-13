package response

import (
	"encoding/json"
	"log"
	"net/http"
	"runtime/debug"
	"strings"
	"sync/atomic"
)

var appEnv atomic.Value

func init() {
	appEnv.Store("prod")
}

func SetAppEnv(env string) {
	normalized := strings.ToLower(strings.TrimSpace(env))
	if normalized == "" {
		normalized = "prod"
	}
	appEnv.Store(normalized)
}

func isDevEnv() bool {
	v, _ := appEnv.Load().(string)
	return v == "dev"
}

func JSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func Error(w http.ResponseWriter, status int, message string) {
	if status >= http.StatusInternalServerError {
		if isDevEnv() {
			stack := string(debug.Stack())
			log.Printf("http status=%d error=%q stack=%s", status, message, stack)
			JSON(w, status, map[string]any{"erro": message, "stackTrace": stack})
			return
		}

		log.Printf("http status=%d error=%q", status, message)
		JSON(w, status, map[string]string{"erro": "Erro interno"})
		return
	}

	JSON(w, status, map[string]string{"erro": message})
}
