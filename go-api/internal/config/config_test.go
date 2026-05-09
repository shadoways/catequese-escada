package config

import "testing"

func TestParseJDBCURLMariaDB(t *testing.T) {
	host, db, params, err := parseJDBCURL("jdbc:mariadb://localhost:3306/catequese?useSSL=false&serverTimezone=UTC")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if host != "localhost:3306" {
		t.Fatalf("unexpected host: %s", host)
	}
	if db != "catequese" {
		t.Fatalf("unexpected db: %s", db)
	}
	if params == "" {
		t.Fatal("expected params not empty")
	}
}

func TestParseJDBCURLInvalid(t *testing.T) {
	_, _, _, err := parseJDBCURL("jdbc:mariadb://")
	if err == nil {
		t.Fatal("expected error for invalid jdbc url")
	}
}
