package config

import (
	"fmt"
	"net/url"
	"os"
	"strconv"
	"strings"
	"time"
)

type Config struct {
	AppEnv               string
	Port                 string
	DBDSN                string
	JWTSecret            string
	JWTExpiration        time.Duration
	JWTRefreshExpiration time.Duration
	GCSBucket            string
	UploadPublicBaseURL  string
	UploadMaxMB          int64
}

func Load() (Config, error) {
	cfg := Config{
		AppEnv:              getEnv("APP_ENV", "dev"),
		Port:                getEnv("PORT", "8080"),
		JWTSecret:           getEnv("JWT_SECRET", "dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890"),
		GCSBucket:           getEnv("GCS_BUCKET", "catequese-escada-storage"),
		UploadPublicBaseURL: getEnv("UPLOAD_PUBLIC_BASE_URL", ""),
	}

	jwtExpMs, err := getEnvAsInt64("JWT_EXPIRATION_MS", 900000)
	if err != nil {
		return Config{}, fmt.Errorf("invalid JWT_EXPIRATION_MS: %w", err)
	}
	refreshExpMs, err := getEnvAsInt64("JWT_REFRESH_EXPIRATION_MS", 604800000)
	if err != nil {
		return Config{}, fmt.Errorf("invalid JWT_REFRESH_EXPIRATION_MS: %w", err)
	}
	cfg.JWTExpiration = time.Duration(jwtExpMs) * time.Millisecond
	cfg.JWTRefreshExpiration = time.Duration(refreshExpMs) * time.Millisecond
	uploadMaxMB, err := getEnvAsInt64("UPLOAD_MAX_MB", 10)
	if err != nil {
		return Config{}, fmt.Errorf("invalid UPLOAD_MAX_MB: %w", err)
	}
	cfg.UploadMaxMB = uploadMaxMB

	dsn := strings.TrimSpace(os.Getenv("DB_DSN"))
	if dsn == "" {
		dsn, err = buildDSNFromSpringVars()
		if err != nil {
			return Config{}, err
		}
	}
	cfg.DBDSN = dsn

	return cfg, nil
}

func buildDSNFromSpringVars() (string, error) {
	jdbcURL := getEnv("SPRING_DATASOURCE_URL", "jdbc:mariadb://localhost:3306/catequese?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true")
	user := getEnv("SPRING_DATASOURCE_USERNAME", "root")
	pass := os.Getenv("SPRING_DATASOURCE_PASSWORD")

	hostPort, dbName, params, err := parseJDBCURL(jdbcURL)
	if err != nil {
		return "", err
	}

	base := fmt.Sprintf("%s:%s@tcp(%s)/%s", user, pass, hostPort, dbName)
	if params == "" {
		return base + "?parseTime=true", nil
	}
	return base + "?parseTime=true&" + params, nil
}

func parseJDBCURL(jdbcURL string) (hostPort string, dbName string, params string, err error) {
	clean := strings.TrimSpace(jdbcURL)
	clean = strings.TrimPrefix(clean, "jdbc:mariadb://")
	clean = strings.TrimPrefix(clean, "jdbc:mysql://")

	u, err := url.Parse("mysql://" + clean)
	if err != nil {
		return "", "", "", fmt.Errorf("invalid datasource URL: %w", err)
	}

	if u.Host == "" {
		return "", "", "", fmt.Errorf("invalid datasource URL host")
	}
	name := strings.TrimPrefix(u.Path, "/")
	if name == "" {
		return "", "", "", fmt.Errorf("invalid datasource URL database name")
	}

	q := u.Query()
	q.Del("serverTimezone")
	q.Set("loc", "UTC")

	return u.Host, name, q.Encode(), nil
}

func getEnv(key, fallback string) string {
	v := strings.TrimSpace(os.Getenv(key))
	if v == "" {
		return fallback
	}
	return v
}

func getEnvAsInt64(key string, fallback int64) (int64, error) {
	raw := strings.TrimSpace(os.Getenv(key))
	if raw == "" {
		return fallback, nil
	}
	v, err := strconv.ParseInt(raw, 10, 64)
	if err != nil {
		return 0, err
	}
	return v, nil
}
