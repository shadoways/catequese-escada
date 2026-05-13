package router

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"catequese-escada/go-api/internal/auth"
	"catequese-escada/go-api/internal/catequisando"
	"catequese-escada/go-api/internal/catequista"
	"catequese-escada/go-api/internal/comunidade"
	"catequese-escada/go-api/internal/conhecimento"
	"catequese-escada/go-api/internal/coordenador"
	"catequese-escada/go-api/internal/documento"
	"catequese-escada/go-api/internal/evento"
	"catequese-escada/go-api/internal/ficha"
	"catequese-escada/go-api/internal/permissao"
	"catequese-escada/go-api/internal/presenca"
	"catequese-escada/go-api/internal/turma"
	"catequese-escada/go-api/internal/upload"
	"catequese-escada/go-api/internal/usuario"

	_ "modernc.org/sqlite"
)

func newUsuariosRouterTestFixture(t *testing.T) (http.Handler, *auth.JWTService) {
	t.Helper()

	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	t.Cleanup(func() { _ = db.Close() })

	schema := []string{
		`CREATE TABLE tb_catequisando (
			id_catequisando INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT
		)`,
		`CREATE TABLE tb_ficha_inscricao (
			id_ficha INTEGER PRIMARY KEY AUTOINCREMENT,
			data_inscricao DATE,
			observacoes TEXT,
			id_catequisando INTEGER
		)`,
		`CREATE TABLE tb_comunidade (
			id_comunidade INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			descricao TEXT,
			ativo BOOLEAN NOT NULL
		)`,
		`CREATE TABLE tb_catequista (
			id_catequista INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			telefone TEXT,
			email TEXT,
			endereco TEXT,
			data_nascimento DATE,
			data_inicio DATE,
			ativo BOOLEAN NOT NULL
		)`,
		`CREATE TABLE tb_coordenador (
			id_coordenador INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			telefone TEXT,
			email TEXT,
			nivel_organizacional TEXT,
			data_nascimento DATE,
			data_inicio DATE,
			ativo BOOLEAN NOT NULL
		)`,
		`CREATE TABLE tb_turma (
			id_turma INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			descricao TEXT,
			ano INTEGER,
			nivel TEXT,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_evento (
			id_evento INTEGER PRIMARY KEY AUTOINCREMENT,
			titulo TEXT NOT NULL,
			nivel TEXT,
			publico_alvo TEXT,
			descricao TEXT,
			data_inicio DATE,
			data_fim DATE,
			local TEXT
		)`,
		`CREATE TABLE tb_presenca (
			id_presenca INTEGER PRIMARY KEY AUTOINCREMENT,
			data DATE,
			presente BOOLEAN,
			id_catequisando INTEGER NOT NULL
		)`,
		`CREATE TABLE tb_documentos (
			id_documento INTEGER PRIMARY KEY AUTOINCREMENT,
			tipo_documento TEXT,
			caminho_arquivo TEXT,
			data_envio DATE,
			id_catequisando INTEGER NOT NULL,
			tipo_status TEXT
		)`,
		`CREATE TABLE tb_login (
			id_login INTEGER PRIMARY KEY AUTOINCREMENT,
			username TEXT NOT NULL,
			password_hash TEXT NOT NULL,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_conhecimento_catequista (
			id_conhecimento INTEGER PRIMARY KEY AUTOINCREMENT,
			area_conhecimento TEXT,
			nivel TEXT,
			descricao TEXT,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_permissoes (
			id_permissao INTEGER PRIMARY KEY AUTOINCREMENT,
			permissao TEXT,
			id_login INTEGER
		)`,
		`INSERT INTO tb_catequista (nome, ativo) VALUES ('Catequista Seed', TRUE)`,
		`INSERT INTO tb_login (username, password_hash, id_catequista) VALUES ('seed', 'hash', 1)`,
		`CREATE TABLE tb_usuario (
			id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			email TEXT NOT NULL UNIQUE,
			password_hash TEXT NOT NULL,
			ativo BOOLEAN NOT NULL,
			id_comunidade INTEGER,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_usuario_role (
			id_usuario_role INTEGER PRIMARY KEY AUTOINCREMENT,
			id_usuario INTEGER NOT NULL,
			role TEXT NOT NULL
		)`,
		`INSERT INTO tb_usuario (nome, email, password_hash, ativo) VALUES ('Coord', 'coord@catequese.com', 'hash', TRUE)`,
		`INSERT INTO tb_usuario_role (id_usuario, role) VALUES (1, 'COORDENADOR_PAROQUIAL')`,
		`INSERT INTO tb_catequisando (nome) VALUES ('Catequisando A')`,
	}
	for _, stmt := range schema {
		if _, err := db.Exec(stmt); err != nil {
			t.Fatalf("schema setup failed: %v", err)
		}
	}

	jwtSvc, err := auth.NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 10*time.Minute)
	if err != nil {
		t.Fatalf("jwt init: %v", err)
	}

	usuarioSvc := usuario.NewService(db, usuario.NewRepository(db))
	fichaSvc := ficha.NewService(ficha.NewRepository(db))
	comunidadeSvc := comunidade.NewService(comunidade.NewRepository(db))
	turmaSvc := turma.NewService(turma.NewRepository(db))
	eventoSvc := evento.NewService(evento.NewRepository(db))
	presencaSvc := presenca.NewService(presenca.NewRepository(db))
	documentoSvc := documento.NewService(documento.NewRepository(db))
	catequistaSvc := catequista.NewService(catequista.NewRepository(db))
	coordenadorSvc := coordenador.NewService(coordenador.NewRepository(db))
	conhecimentoSvc := conhecimento.NewService(conhecimento.NewRepository(db))
	permissaoSvc := permissao.NewService(permissao.NewRepository(db))
	uploadSvc, err := upload.NewServiceWithStore("test-bucket", "", upload.NewMemoryStore())
	if err != nil {
		t.Fatalf("upload service init: %v", err)
	}
	h := New(jwtSvc, &auth.Service{}, catequisando.NewRepository(db), fichaSvc, comunidadeSvc, turmaSvc, eventoSvc, presencaSvc, documentoSvc, catequistaSvc, coordenadorSvc, conhecimentoSvc, permissaoSvc, uploadSvc, 10, usuarioSvc)
	return h, jwtSvc
}

func newAuthRouterTestFixture(t *testing.T) http.Handler {
	t.Helper()

	dbName := strings.ReplaceAll(strings.ToLower(t.Name()), "/", "_")
	db, err := sql.Open("sqlite", fmt.Sprintf("file:%s?mode=memory&cache=shared", dbName))
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	db.SetMaxOpenConns(1)
	db.SetMaxIdleConns(1)
	t.Cleanup(func() { _ = db.Close() })

	passwordHash, err := auth.HashPassword("admin123")
	if err != nil {
		t.Fatalf("hash password: %v", err)
	}

	schema := []string{
		`CREATE TABLE tb_catequisando (
			id_catequisando INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT
		)`,
		`CREATE TABLE tb_ficha_inscricao (
			id_ficha INTEGER PRIMARY KEY AUTOINCREMENT,
			data_inscricao DATE,
			observacoes TEXT,
			id_catequisando INTEGER
		)`,
		`CREATE TABLE tb_comunidade (
			id_comunidade INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			descricao TEXT,
			ativo BOOLEAN NOT NULL
		)`,
		`CREATE TABLE tb_catequista (
			id_catequista INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			telefone TEXT,
			email TEXT,
			endereco TEXT,
			data_nascimento DATE,
			data_inicio DATE,
			ativo BOOLEAN NOT NULL
		)`,
		`CREATE TABLE tb_coordenador (
			id_coordenador INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			telefone TEXT,
			email TEXT,
			nivel_organizacional TEXT,
			data_nascimento DATE,
			data_inicio DATE,
			ativo BOOLEAN NOT NULL
		)`,
		`CREATE TABLE tb_turma (
			id_turma INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			descricao TEXT,
			ano INTEGER,
			nivel TEXT,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_evento (
			id_evento INTEGER PRIMARY KEY AUTOINCREMENT,
			titulo TEXT NOT NULL,
			nivel TEXT,
			publico_alvo TEXT,
			descricao TEXT,
			data_inicio DATE,
			data_fim DATE,
			local TEXT
		)`,
		`CREATE TABLE tb_presenca (
			id_presenca INTEGER PRIMARY KEY AUTOINCREMENT,
			data DATE,
			presente BOOLEAN,
			id_catequisando INTEGER NOT NULL
		)`,
		`CREATE TABLE tb_documentos (
			id_documento INTEGER PRIMARY KEY AUTOINCREMENT,
			tipo_documento TEXT,
			caminho_arquivo TEXT,
			data_envio DATE,
			id_catequisando INTEGER NOT NULL,
			tipo_status TEXT
		)`,
		`CREATE TABLE tb_login (
			id_login INTEGER PRIMARY KEY AUTOINCREMENT,
			username TEXT NOT NULL,
			password_hash TEXT NOT NULL,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_conhecimento_catequista (
			id_conhecimento INTEGER PRIMARY KEY AUTOINCREMENT,
			area_conhecimento TEXT,
			nivel TEXT,
			descricao TEXT,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_permissoes (
			id_permissao INTEGER PRIMARY KEY AUTOINCREMENT,
			permissao TEXT,
			id_login INTEGER
		)`,
		`INSERT INTO tb_catequista (nome, ativo) VALUES ('Catequista Seed', TRUE)`,
		`INSERT INTO tb_login (username, password_hash, id_catequista) VALUES ('seed', 'hash', 1)`,
		`CREATE TABLE tb_usuario (
			id_usuario INTEGER PRIMARY KEY AUTOINCREMENT,
			nome TEXT NOT NULL,
			email TEXT NOT NULL UNIQUE,
			password_hash TEXT NOT NULL,
			ativo BOOLEAN NOT NULL,
			ultimo_login DATETIME,
			id_comunidade INTEGER,
			id_catequista INTEGER
		)`,
		`CREATE TABLE tb_usuario_role (
			id_usuario_role INTEGER PRIMARY KEY AUTOINCREMENT,
			id_usuario INTEGER NOT NULL,
			role TEXT NOT NULL
		)`,
		`CREATE TABLE tb_refresh_token (
			id_refresh_token INTEGER PRIMARY KEY AUTOINCREMENT,
			id_usuario INTEGER NOT NULL,
			token_hash TEXT NOT NULL UNIQUE,
			data_expiracao DATETIME NOT NULL,
			revogado BOOLEAN NOT NULL,
			data_revogacao DATETIME
		)`,
		`CREATE TABLE tb_password_reset_token (
			id_token INTEGER PRIMARY KEY AUTOINCREMENT,
			token TEXT NOT NULL UNIQUE,
			id_usuario INTEGER NOT NULL,
			data_expiracao DATETIME NOT NULL,
			usado BOOLEAN NOT NULL,
			data_criacao DATETIME DEFAULT CURRENT_TIMESTAMP
		)`,
		fmt.Sprintf(`INSERT INTO tb_usuario (nome, email, password_hash, ativo) VALUES ('Administrador', 'admin@catequese.com', '%s', TRUE)`, passwordHash),
		`INSERT INTO tb_usuario_role (id_usuario, role) VALUES (1, 'COORDENADOR_PAROQUIAL')`,
		`INSERT INTO tb_catequisando (nome) VALUES ('Catequisando A')`,
	}
	for _, stmt := range schema {
		if _, err := db.Exec(stmt); err != nil {
			t.Fatalf("schema setup failed: %v", err)
		}
	}

	jwtSvc, err := auth.NewJWTService("dev-only-change-this-secret-key-before-production-minimum-64-characters-1234567890", 10*time.Minute)
	if err != nil {
		t.Fatalf("jwt init: %v", err)
	}

	authSvc := auth.NewService(db, auth.NewRepository(), jwtSvc, 15*time.Minute, 24*time.Hour)
	usuarioSvc := usuario.NewService(db, usuario.NewRepository(db))
	fichaSvc := ficha.NewService(ficha.NewRepository(db))
	comunidadeSvc := comunidade.NewService(comunidade.NewRepository(db))
	turmaSvc := turma.NewService(turma.NewRepository(db))
	eventoSvc := evento.NewService(evento.NewRepository(db))
	presencaSvc := presenca.NewService(presenca.NewRepository(db))
	documentoSvc := documento.NewService(documento.NewRepository(db))
	catequistaSvc := catequista.NewService(catequista.NewRepository(db))
	coordenadorSvc := coordenador.NewService(coordenador.NewRepository(db))
	conhecimentoSvc := conhecimento.NewService(conhecimento.NewRepository(db))
	permissaoSvc := permissao.NewService(permissao.NewRepository(db))
	uploadSvc, err := upload.NewServiceWithStore("test-bucket", "", upload.NewMemoryStore())
	if err != nil {
		t.Fatalf("upload service init: %v", err)
	}
	return New(jwtSvc, authSvc, catequisando.NewRepository(db), fichaSvc, comunidadeSvc, turmaSvc, eventoSvc, presencaSvc, documentoSvc, catequistaSvc, coordenadorSvc, conhecimentoSvc, permissaoSvc, uploadSvc, 10, usuarioSvc)
}

func TestUsuariosRouteRequiresJWT(t *testing.T) {
	h, _ := newUsuariosRouterTestFixture(t)

	req := httptest.NewRequest(http.MethodGet, "/api/usuarios/", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", w.Code)
	}
	if !strings.Contains(w.Body.String(), "Não autenticado") {
		t.Fatalf("unexpected body: %s", w.Body.String())
	}
}

func TestUsuariosRouteRequiresCoordinatorRole(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("cat@catequese.com", "Catequista", []string{"CATEQUISTA"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/usuarios/", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusForbidden {
		t.Fatalf("expected 403, got %d", w.Code)
	}
	if !strings.Contains(w.Body.String(), "Acesso negado") {
		t.Fatalf("unexpected body: %s", w.Body.String())
	}
}

func TestUsuariosRouteWithCoordinatorRoleReturnsList(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/usuarios/", nil)
	req = req.WithContext(context.Background())
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Body.String(), "coord@catequese.com") {
		t.Fatalf("expected response to contain seeded user, got %s", w.Body.String())
	}
}

func TestAuthLoginEndpointSuccess(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	body := []byte(`{"email":"admin@catequese.com","password":"admin123"}`)
	req := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", w.Code, w.Body.String())
	}

	var payload map[string]any
	if err := json.Unmarshal(w.Body.Bytes(), &payload); err != nil {
		t.Fatalf("invalid json: %v", err)
	}
	if payload["token"] == "" || payload["refreshToken"] == "" {
		t.Fatalf("expected token and refreshToken in response: %v", payload)
	}
}

func TestAuthRefreshLogoutLifecycleViaHTTP(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	loginBody := []byte(`{"email":"admin@catequese.com","password":"admin123"}`)
	loginReq := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(loginBody))
	loginReq.Header.Set("Content-Type", "application/json")
	loginW := httptest.NewRecorder()
	h.ServeHTTP(loginW, loginReq)
	if loginW.Code != http.StatusOK {
		t.Fatalf("login expected 200, got %d body=%s", loginW.Code, loginW.Body.String())
	}

	var loginPayload map[string]any
	if err := json.Unmarshal(loginW.Body.Bytes(), &loginPayload); err != nil {
		t.Fatalf("invalid login json: %v", err)
	}
	refresh1, _ := loginPayload["refreshToken"].(string)
	if refresh1 == "" {
		t.Fatalf("expected refreshToken in login payload: %v", loginPayload)
	}

	refreshReqBody := []byte(fmt.Sprintf(`{"refreshToken":"%s"}`, refresh1))
	refreshReq := httptest.NewRequest(http.MethodPost, "/api/auth/refresh", bytes.NewReader(refreshReqBody))
	refreshReq.Header.Set("Content-Type", "application/json")
	refreshW := httptest.NewRecorder()
	h.ServeHTTP(refreshW, refreshReq)
	if refreshW.Code != http.StatusOK {
		t.Fatalf("refresh expected 200, got %d body=%s", refreshW.Code, refreshW.Body.String())
	}

	var refreshPayload map[string]any
	if err := json.Unmarshal(refreshW.Body.Bytes(), &refreshPayload); err != nil {
		t.Fatalf("invalid refresh json: %v", err)
	}
	accessToken, _ := refreshPayload["token"].(string)
	if accessToken == "" {
		accessToken, _ = loginPayload["token"].(string)
	}
	if accessToken == "" {
		t.Fatalf("expected access token in login/refresh payload: login=%v refresh=%v", loginPayload, refreshPayload)
	}
	refresh2, _ := refreshPayload["refreshToken"].(string)
	if refresh2 == "" || refresh2 == refresh1 {
		t.Fatalf("expected rotated refresh token, got payload: %v", refreshPayload)
	}

	logoutBody := []byte(fmt.Sprintf(`{"refreshToken":"%s"}`, refresh2))
	logoutReq := httptest.NewRequest(http.MethodPost, "/api/auth/logout", bytes.NewReader(logoutBody))
	logoutReq.Header.Set("Content-Type", "application/json")
	logoutReq.Header.Set("Authorization", "Bearer "+accessToken)
	logoutW := httptest.NewRecorder()
	h.ServeHTTP(logoutW, logoutReq)
	if logoutW.Code != http.StatusOK {
		t.Fatalf("logout expected 200, got %d body=%s", logoutW.Code, logoutW.Body.String())
	}

	refreshAfterLogoutBody := []byte(fmt.Sprintf(`{"refreshToken":"%s"}`, refresh2))
	refreshAfterLogoutReq := httptest.NewRequest(http.MethodPost, "/api/auth/refresh", bytes.NewReader(refreshAfterLogoutBody))
	refreshAfterLogoutReq.Header.Set("Content-Type", "application/json")
	refreshAfterLogoutW := httptest.NewRecorder()
	h.ServeHTTP(refreshAfterLogoutW, refreshAfterLogoutReq)
	if refreshAfterLogoutW.Code != http.StatusUnauthorized {
		t.Fatalf("refresh after logout expected 401, got %d body=%s", refreshAfterLogoutW.Code, refreshAfterLogoutW.Body.String())
	}
}

func TestAuthLogoutValidationErrorViaHTTP(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	loginBody := []byte(`{"email":"admin@catequese.com","password":"admin123"}`)
	loginReq := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(loginBody))
	loginReq.Header.Set("Content-Type", "application/json")
	loginW := httptest.NewRecorder()
	h.ServeHTTP(loginW, loginReq)
	if loginW.Code != http.StatusOK {
		t.Fatalf("login expected 200, got %d body=%s", loginW.Code, loginW.Body.String())
	}

	var loginPayload map[string]any
	if err := json.Unmarshal(loginW.Body.Bytes(), &loginPayload); err != nil {
		t.Fatalf("invalid login json: %v", err)
	}
	token, _ := loginPayload["token"].(string)
	if token == "" {
		t.Fatalf("expected token in login payload: %v", loginPayload)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/auth/logout", bytes.NewReader([]byte(`{"refreshToken":""}`)))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d body=%s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Body.String(), "Validação falhou") {
		t.Fatalf("unexpected body: %s", w.Body.String())
	}
}

func TestAuthValidateWithoutBearerReturnsFalse(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	req := httptest.NewRequest(http.MethodGet, "/api/auth/validate", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 due to auth middleware, got %d body=%s", w.Code, w.Body.String())
	}
}

func TestAuthValidateWithValidBearerReturnsTrue(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	loginBody := []byte(`{"email":"admin@catequese.com","password":"admin123"}`)
	loginReq := httptest.NewRequest(http.MethodPost, "/api/auth/login", bytes.NewReader(loginBody))
	loginReq.Header.Set("Content-Type", "application/json")
	loginW := httptest.NewRecorder()
	h.ServeHTTP(loginW, loginReq)
	if loginW.Code != http.StatusOK {
		t.Fatalf("login expected 200, got %d body=%s", loginW.Code, loginW.Body.String())
	}

	var loginPayload map[string]any
	if err := json.Unmarshal(loginW.Body.Bytes(), &loginPayload); err != nil {
		t.Fatalf("invalid login json: %v", err)
	}
	token, _ := loginPayload["token"].(string)
	if token == "" {
		t.Fatalf("expected token in login payload: %v", loginPayload)
	}

	validateReq := httptest.NewRequest(http.MethodGet, "/api/auth/validate", nil)
	validateReq.Header.Set("Authorization", "Bearer "+token)
	validateW := httptest.NewRecorder()
	h.ServeHTTP(validateW, validateReq)

	if validateW.Code != http.StatusOK {
		t.Fatalf("validate expected 200, got %d body=%s", validateW.Code, validateW.Body.String())
	}
	if !strings.Contains(validateW.Body.String(), `"valid":true`) {
		t.Fatalf("unexpected body for valid token: %s", validateW.Body.String())
	}
}

func TestAuthValidateWithInvalidBearerReturnsFalse(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	req := httptest.NewRequest(http.MethodGet, "/api/auth/validate", nil)
	req.Header.Set("Authorization", "Bearer token-invalido")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 from middleware for invalid token, got %d body=%s", w.Code, w.Body.String())
	}
}

func TestUsuariosCreateAndGetByEmailViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createBody := []byte(`{"nome":"Novo Usuario","email":"novo@catequese.com","password":"senha123","roles":["CATEQUISTA"]}`)
	createReq := httptest.NewRequest(http.MethodPost, "/api/usuarios/", bytes.NewReader(createBody))
	createReq.Header.Set("Content-Type", "application/json")
	createReq.Header.Set("Authorization", "Bearer "+token)
	createW := httptest.NewRecorder()
	h.ServeHTTP(createW, createReq)

	if createW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on create, got %d body=%s", createW.Code, createW.Body.String())
	}
	if !strings.Contains(createW.Body.String(), "novo@catequese.com") {
		t.Fatalf("expected created user in response: %s", createW.Body.String())
	}

	getReq := httptest.NewRequest(http.MethodGet, "/api/usuarios/email/novo@catequese.com", nil)
	getReq.Header.Set("Authorization", "Bearer "+token)
	getW := httptest.NewRecorder()
	h.ServeHTTP(getW, getReq)

	if getW.Code != http.StatusOK {
		t.Fatalf("expected 200 on get by email, got %d body=%s", getW.Code, getW.Body.String())
	}
	if !strings.Contains(getW.Body.String(), "novo@catequese.com") {
		t.Fatalf("expected fetched user by email in response: %s", getW.Body.String())
	}
}

func TestUsuariosCreateValidationErrorViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	invalidBody := []byte(`{"nome":"","email":"invalido","password":"123","roles":[]}`)
	req := httptest.NewRequest(http.MethodPost, "/api/usuarios/", bytes.NewReader(invalidBody))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d body=%s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Body.String(), "Validação falhou") || !strings.Contains(w.Body.String(), "detalhes") {
		t.Fatalf("expected validation payload with detalhes, got %s", w.Body.String())
	}
}

func TestFichasCRUDViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createBody := []byte(`{"dataInscricao":"2026-05-06","observacoes":"Primeira ficha","catequisandoId":1}`)
	createReq := httptest.NewRequest(http.MethodPost, "/api/fichas/", bytes.NewReader(createBody))
	createReq.Header.Set("Content-Type", "application/json")
	createReq.Header.Set("Authorization", "Bearer "+token)
	createW := httptest.NewRecorder()
	h.ServeHTTP(createW, createReq)

	if createW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on ficha create, got %d body=%s", createW.Code, createW.Body.String())
	}

	var created map[string]any
	if err := json.Unmarshal(createW.Body.Bytes(), &created); err != nil {
		t.Fatalf("invalid ficha create json: %v", err)
	}
	id, ok := created["idFicha"].(float64)
	if !ok || id <= 0 {
		t.Fatalf("expected idFicha in create payload, got %v", created)
	}

	getReq := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/fichas/%d", int64(id)), nil)
	getReq.Header.Set("Authorization", "Bearer "+token)
	getW := httptest.NewRecorder()
	h.ServeHTTP(getW, getReq)
	if getW.Code != http.StatusOK {
		t.Fatalf("expected 200 on ficha get, got %d body=%s", getW.Code, getW.Body.String())
	}

	updateBody := []byte(`{"dataInscricao":"2026-05-07","observacoes":"Ficha atualizada","catequisandoId":1}`)
	updateReq := httptest.NewRequest(http.MethodPut, fmt.Sprintf("/api/fichas/%d", int64(id)), bytes.NewReader(updateBody))
	updateReq.Header.Set("Content-Type", "application/json")
	updateReq.Header.Set("Authorization", "Bearer "+token)
	updateW := httptest.NewRecorder()
	h.ServeHTTP(updateW, updateReq)
	if updateW.Code != http.StatusOK {
		t.Fatalf("expected 200 on ficha update, got %d body=%s", updateW.Code, updateW.Body.String())
	}
	if !strings.Contains(updateW.Body.String(), "Ficha atualizada") {
		t.Fatalf("expected updated observacoes in response, got %s", updateW.Body.String())
	}

	deleteByCateqReq := httptest.NewRequest(http.MethodDelete, "/api/fichas/catequisando/1", nil)
	deleteByCateqReq.Header.Set("Authorization", "Bearer "+token)
	deleteByCateqW := httptest.NewRecorder()
	h.ServeHTTP(deleteByCateqW, deleteByCateqReq)
	if deleteByCateqW.Code != http.StatusNoContent {
		t.Fatalf("expected 204 on delete by catequisando, got %d body=%s", deleteByCateqW.Code, deleteByCateqW.Body.String())
	}

	getAfterDeleteReq := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/fichas/%d", int64(id)), nil)
	getAfterDeleteReq.Header.Set("Authorization", "Bearer "+token)
	getAfterDeleteW := httptest.NewRecorder()
	h.ServeHTTP(getAfterDeleteW, getAfterDeleteReq)
	if getAfterDeleteW.Code != http.StatusNotFound {
		t.Fatalf("expected 404 after ficha delete, got %d body=%s", getAfterDeleteW.Code, getAfterDeleteW.Body.String())
	}
}

func TestComunidadesAndTurmasCRUDViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createCom := httptest.NewRequest(http.MethodPost, "/api/comunidades/", bytes.NewReader([]byte(`{"nome":"Matriz","descricao":"Centro","ativo":true}`)))
	createCom.Header.Set("Content-Type", "application/json")
	createCom.Header.Set("Authorization", "Bearer "+token)
	createComW := httptest.NewRecorder()
	h.ServeHTTP(createComW, createCom)
	if createComW.Code != http.StatusOK {
		t.Fatalf("expected 200 on comunidade create, got %d body=%s", createComW.Code, createComW.Body.String())
	}

	listCom := httptest.NewRequest(http.MethodGet, "/api/comunidades/", nil)
	listCom.Header.Set("Authorization", "Bearer "+token)
	listComW := httptest.NewRecorder()
	h.ServeHTTP(listComW, listCom)
	if listComW.Code != http.StatusOK {
		t.Fatalf("expected 200 on comunidade list, got %d body=%s", listComW.Code, listComW.Body.String())
	}

	createTurma := httptest.NewRequest(http.MethodPost, "/api/turmas/", bytes.NewReader([]byte(`{"nome":"Turma 1","descricao":"Descrição","ano":2026,"nivel":"BASICO"}`)))
	createTurma.Header.Set("Content-Type", "application/json")
	createTurma.Header.Set("Authorization", "Bearer "+token)
	createTurmaW := httptest.NewRecorder()
	h.ServeHTTP(createTurmaW, createTurma)
	if createTurmaW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on turma create, got %d body=%s", createTurmaW.Code, createTurmaW.Body.String())
	}

	listTurma := httptest.NewRequest(http.MethodGet, "/api/turmas/", nil)
	listTurma.Header.Set("Authorization", "Bearer "+token)
	listTurmaW := httptest.NewRecorder()
	h.ServeHTTP(listTurmaW, listTurma)
	if listTurmaW.Code != http.StatusOK {
		t.Fatalf("expected 200 on turma list, got %d body=%s", listTurmaW.Code, listTurmaW.Body.String())
	}
}

func TestUsuariosCreateConflictEmailViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	body := []byte(`{"nome":"Duplicado","email":"coord@catequese.com","password":"senha123","roles":["CATEQUISTA"]}`)
	req := httptest.NewRequest(http.MethodPost, "/api/usuarios/", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d body=%s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Body.String(), "Email já cadastrado") {
		t.Fatalf("expected duplicate email message, got %s", w.Body.String())
	}
}

func TestUsuariosUpdateToggleDeleteViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createBody := []byte(`{"nome":"Usuario Fluxo","email":"fluxo@catequese.com","password":"senha123","roles":["CATEQUISTA"]}`)
	createReq := httptest.NewRequest(http.MethodPost, "/api/usuarios/", bytes.NewReader(createBody))
	createReq.Header.Set("Content-Type", "application/json")
	createReq.Header.Set("Authorization", "Bearer "+token)
	createW := httptest.NewRecorder()
	h.ServeHTTP(createW, createReq)
	if createW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on create, got %d body=%s", createW.Code, createW.Body.String())
	}

	var created map[string]any
	if err := json.Unmarshal(createW.Body.Bytes(), &created); err != nil {
		t.Fatalf("invalid create json: %v", err)
	}
	id, ok := created["idUsuario"].(float64)
	if !ok || id <= 0 {
		t.Fatalf("expected idUsuario in create payload: %v", created)
	}

	updateBody := []byte(`{"nome":"Usuario Atualizado","email":"fluxo2@catequese.com","ativo":true,"roles":["COORDENADOR_COMUNIDADE"]}`)
	updateReq := httptest.NewRequest(http.MethodPut, fmt.Sprintf("/api/usuarios/%d", int64(id)), bytes.NewReader(updateBody))
	updateReq.Header.Set("Content-Type", "application/json")
	updateReq.Header.Set("Authorization", "Bearer "+token)
	updateW := httptest.NewRecorder()
	h.ServeHTTP(updateW, updateReq)
	if updateW.Code != http.StatusOK {
		t.Fatalf("expected 200 on update, got %d body=%s", updateW.Code, updateW.Body.String())
	}
	if !strings.Contains(updateW.Body.String(), "fluxo2@catequese.com") {
		t.Fatalf("expected updated email in response, got %s", updateW.Body.String())
	}

	toggleReq := httptest.NewRequest(http.MethodPatch, fmt.Sprintf("/api/usuarios/%d/toggle-ativo", int64(id)), nil)
	toggleReq.Header.Set("Authorization", "Bearer "+token)
	toggleW := httptest.NewRecorder()
	h.ServeHTTP(toggleW, toggleReq)
	if toggleW.Code != http.StatusOK {
		t.Fatalf("expected 200 on toggle, got %d body=%s", toggleW.Code, toggleW.Body.String())
	}
	if !strings.Contains(toggleW.Body.String(), `"ativo":false`) {
		t.Fatalf("expected ativo false after toggle, got %s", toggleW.Body.String())
	}

	deleteReq := httptest.NewRequest(http.MethodDelete, fmt.Sprintf("/api/usuarios/%d", int64(id)), nil)
	deleteReq.Header.Set("Authorization", "Bearer "+token)
	deleteW := httptest.NewRecorder()
	h.ServeHTTP(deleteW, deleteReq)
	if deleteW.Code != http.StatusNoContent {
		t.Fatalf("expected 204 on delete, got %d body=%s", deleteW.Code, deleteW.Body.String())
	}

	getReq := httptest.NewRequest(http.MethodGet, fmt.Sprintf("/api/usuarios/%d", int64(id)), nil)
	getReq.Header.Set("Authorization", "Bearer "+token)
	getW := httptest.NewRecorder()
	h.ServeHTTP(getW, getReq)
	if getW.Code != http.StatusNotFound {
		t.Fatalf("expected 404 after delete, got %d body=%s", getW.Code, getW.Body.String())
	}
}

func TestUsuariosUpdateConflictEmailViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createUser := func(nome, email string) int64 {
		body := []byte(fmt.Sprintf(`{"nome":"%s","email":"%s","password":"senha123","roles":["CATEQUISTA"]}`, nome, email))
		req := httptest.NewRequest(http.MethodPost, "/api/usuarios/", bytes.NewReader(body))
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Authorization", "Bearer "+token)
		w := httptest.NewRecorder()
		h.ServeHTTP(w, req)
		if w.Code != http.StatusCreated {
			t.Fatalf("expected 201 creating %s, got %d body=%s", email, w.Code, w.Body.String())
		}
		var payload map[string]any
		if err := json.Unmarshal(w.Body.Bytes(), &payload); err != nil {
			t.Fatalf("invalid create json: %v", err)
		}
		id, _ := payload["idUsuario"].(float64)
		return int64(id)
	}

	id1 := createUser("User 1", "u1@catequese.com")
	_ = createUser("User 2", "u2@catequese.com")

	updateBody := []byte(`{"nome":"User 1","email":"u2@catequese.com","ativo":true,"roles":["CATEQUISTA"]}`)
	updateReq := httptest.NewRequest(http.MethodPut, fmt.Sprintf("/api/usuarios/%d", id1), bytes.NewReader(updateBody))
	updateReq.Header.Set("Content-Type", "application/json")
	updateReq.Header.Set("Authorization", "Bearer "+token)
	updateW := httptest.NewRecorder()
	h.ServeHTTP(updateW, updateReq)

	if updateW.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 on update conflict, got %d body=%s", updateW.Code, updateW.Body.String())
	}
	if !strings.Contains(updateW.Body.String(), "Email já cadastrado") {
		t.Fatalf("expected duplicate email message, got %s", updateW.Body.String())
	}
}

func TestAuthHealthEndpointViaRouter(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	req := httptest.NewRequest(http.MethodGet, "/api/auth/health", nil)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Body.String(), `"status":"UP"`) || !strings.Contains(w.Body.String(), `"module":"authentication"`) {
		t.Fatalf("unexpected health body: %s", w.Body.String())
	}
}

func TestAuthPasswordResetRequestViaRouter(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	req := httptest.NewRequest(http.MethodPost, "/api/auth/password-reset/request", bytes.NewReader([]byte(`{"email":"admin@catequese.com"}`)))
	req.Header.Set("Content-Type", "application/json")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d body=%s", w.Code, w.Body.String())
	}
	if !strings.Contains(w.Body.String(), "Se o email estiver cadastrado") {
		t.Fatalf("unexpected response body: %s", w.Body.String())
	}
}

func TestAuthPasswordResetConfirmViaRouter(t *testing.T) {
	h := newAuthRouterTestFixture(t)

	requestReq := httptest.NewRequest(http.MethodPost, "/api/auth/password-reset/request", bytes.NewReader([]byte(`{"email":"admin@catequese.com"}`)))
	requestReq.Header.Set("Content-Type", "application/json")
	requestW := httptest.NewRecorder()
	h.ServeHTTP(requestW, requestReq)
	if requestW.Code != http.StatusOK {
		t.Fatalf("request expected 200, got %d body=%s", requestW.Code, requestW.Body.String())
	}

	// The endpoint contract does not expose token; for router-level contract we validate validation and successful request endpoint.
	confirmReq := httptest.NewRequest(http.MethodPost, "/api/auth/password-reset/confirm", bytes.NewReader([]byte(`{"token":"","newPassword":"123"}`)))
	confirmReq.Header.Set("Content-Type", "application/json")
	confirmW := httptest.NewRecorder()
	h.ServeHTTP(confirmW, confirmReq)

	if confirmW.Code != http.StatusBadRequest {
		t.Fatalf("confirm expected 400 for validation, got %d body=%s", confirmW.Code, confirmW.Body.String())
	}
	if !strings.Contains(confirmW.Body.String(), "Validação falhou") || !strings.Contains(confirmW.Body.String(), "detalhes") {
		t.Fatalf("unexpected confirm validation body: %s", confirmW.Body.String())
	}
}

func TestUnknownProtectedRouteReturnsNotFound(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)
	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	req := httptest.NewRequest(http.MethodGet, "/api/nao-existe", nil)
	req.Header.Set("Authorization", "Bearer "+token)
	w := httptest.NewRecorder()

	h.ServeHTTP(w, req)

	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d body=%s", w.Code, w.Body.String())
	}
}

func TestEventosAndPresencasCRUDViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createEvento := httptest.NewRequest(http.MethodPost, "/api/eventos/", bytes.NewReader([]byte(`{"titulo":"Retiro","nivel":"JOVENS","publicoAlvo":"Crismandos","descricao":"Retiro anual","dataInicio":"2026-06-01","dataFim":"2026-06-02","local":"Salão"}`)))
	createEvento.Header.Set("Content-Type", "application/json")
	createEvento.Header.Set("Authorization", "Bearer "+token)
	createEventoW := httptest.NewRecorder()
	h.ServeHTTP(createEventoW, createEvento)
	if createEventoW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on evento create, got %d body=%s", createEventoW.Code, createEventoW.Body.String())
	}

	listEvento := httptest.NewRequest(http.MethodGet, "/api/eventos/", nil)
	listEvento.Header.Set("Authorization", "Bearer "+token)
	listEventoW := httptest.NewRecorder()
	h.ServeHTTP(listEventoW, listEvento)
	if listEventoW.Code != http.StatusOK {
		t.Fatalf("expected 200 on evento list, got %d body=%s", listEventoW.Code, listEventoW.Body.String())
	}

	createPresenca := httptest.NewRequest(http.MethodPost, "/api/presencas/", bytes.NewReader([]byte(`{"data":"2026-05-06","presente":true,"catequisando":{"idCatequisando":1}}`)))
	createPresenca.Header.Set("Content-Type", "application/json")
	createPresenca.Header.Set("Authorization", "Bearer "+token)
	createPresencaW := httptest.NewRecorder()
	h.ServeHTTP(createPresencaW, createPresenca)
	if createPresencaW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on presenca create, got %d body=%s", createPresencaW.Code, createPresencaW.Body.String())
	}

	listPresenca := httptest.NewRequest(http.MethodGet, "/api/presencas/", nil)
	listPresenca.Header.Set("Authorization", "Bearer "+token)
	listPresencaW := httptest.NewRecorder()
	h.ServeHTTP(listPresencaW, listPresenca)
	if listPresencaW.Code != http.StatusOK {
		t.Fatalf("expected 200 on presenca list, got %d body=%s", listPresencaW.Code, listPresencaW.Body.String())
	}
}

func TestDocumentosCRUDAndStatusViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)

	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createDoc := httptest.NewRequest(http.MethodPost, "/api/documentos/", bytes.NewReader([]byte(`{"tipoDocumento":"RG","caminhoArquivo":"uploads/rg.png","dataEnvio":"2026-05-06","catequisando":{"idCatequisando":1},"tipoStatus":"ENVIADO"}`)))
	createDoc.Header.Set("Content-Type", "application/json")
	createDoc.Header.Set("Authorization", "Bearer "+token)
	createDocW := httptest.NewRecorder()
	h.ServeHTTP(createDocW, createDoc)
	if createDocW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on documento create, got %d body=%s", createDocW.Code, createDocW.Body.String())
	}

	var created map[string]any
	if err := json.Unmarshal(createDocW.Body.Bytes(), &created); err != nil {
		t.Fatalf("invalid documento create json: %v", err)
	}
	id, ok := created["idDocumento"].(float64)
	if !ok || id <= 0 {
		t.Fatalf("expected idDocumento in payload, got %v", created)
	}

	updateStatusReq := httptest.NewRequest(http.MethodPut, fmt.Sprintf("/api/documentos/%d/status", int64(id)), bytes.NewReader([]byte(`{"novoStatus":"APROVADO"}`)))
	updateStatusReq.Header.Set("Content-Type", "application/json")
	updateStatusReq.Header.Set("Authorization", "Bearer "+token)
	updateStatusW := httptest.NewRecorder()
	h.ServeHTTP(updateStatusW, updateStatusReq)
	if updateStatusW.Code != http.StatusOK {
		t.Fatalf("expected 200 on status update, got %d body=%s", updateStatusW.Code, updateStatusW.Body.String())
	}
	if !strings.Contains(updateStatusW.Body.String(), "APROVADO") {
		t.Fatalf("expected updated status in response, got %s", updateStatusW.Body.String())
	}

	deleteReq := httptest.NewRequest(http.MethodDelete, fmt.Sprintf("/api/documentos/%d", int64(id)), nil)
	deleteReq.Header.Set("Authorization", "Bearer "+token)
	deleteW := httptest.NewRecorder()
	h.ServeHTTP(deleteW, deleteReq)
	if deleteW.Code != http.StatusNoContent {
		t.Fatalf("expected 204 on documento delete, got %d body=%s", deleteW.Code, deleteW.Body.String())
	}
}

func TestCatequistaCoordenadorConhecimentoPermissaoAndUploadViaRouter(t *testing.T) {
	h, jwtSvc := newUsuariosRouterTestFixture(t)
	token, err := jwtSvc.GenerateAccessToken("coord@catequese.com", "Coordenador", []string{"COORDENADOR_PAROQUIAL"})
	if err != nil {
		t.Fatalf("generate token: %v", err)
	}

	createCatequista := httptest.NewRequest(http.MethodPost, "/api/catequistas/", bytes.NewReader([]byte(`{"nome":"Novo Catequista","ativo":true}`)))
	createCatequista.Header.Set("Content-Type", "application/json")
	createCatequista.Header.Set("Authorization", "Bearer "+token)
	createCatequistaW := httptest.NewRecorder()
	h.ServeHTTP(createCatequistaW, createCatequista)
	if createCatequistaW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on catequista create, got %d body=%s", createCatequistaW.Code, createCatequistaW.Body.String())
	}

	createCoordenador := httptest.NewRequest(http.MethodPost, "/api/coordenadores/", bytes.NewReader([]byte(`{"nome":"Novo Coordenador","ativo":true}`)))
	createCoordenador.Header.Set("Content-Type", "application/json")
	createCoordenador.Header.Set("Authorization", "Bearer "+token)
	createCoordenadorW := httptest.NewRecorder()
	h.ServeHTTP(createCoordenadorW, createCoordenador)
	if createCoordenadorW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on coordenador create, got %d body=%s", createCoordenadorW.Code, createCoordenadorW.Body.String())
	}

	createConhecimento := httptest.NewRequest(http.MethodPost, "/api/conhecimentos/", bytes.NewReader([]byte(`{"areaConhecimento":"Liturgia","nivel":"INTERMEDIARIO","descricao":"Teste","catequista":{"idCatequista":1}}`)))
	createConhecimento.Header.Set("Content-Type", "application/json")
	createConhecimento.Header.Set("Authorization", "Bearer "+token)
	createConhecimentoW := httptest.NewRecorder()
	h.ServeHTTP(createConhecimentoW, createConhecimento)
	if createConhecimentoW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on conhecimento create, got %d body=%s", createConhecimentoW.Code, createConhecimentoW.Body.String())
	}

	createPermissao := httptest.NewRequest(http.MethodPost, "/api/permissoes/", bytes.NewReader([]byte(`{"permissao":"EDITAR","login":{"idLogin":1}}`)))
	createPermissao.Header.Set("Content-Type", "application/json")
	createPermissao.Header.Set("Authorization", "Bearer "+token)
	createPermissaoW := httptest.NewRecorder()
	h.ServeHTTP(createPermissaoW, createPermissao)
	if createPermissaoW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on permissao create, got %d body=%s", createPermissaoW.Code, createPermissaoW.Body.String())
	}

	body := &bytes.Buffer{}
	writer := multipart.NewWriter(body)
	part, err := writer.CreateFormFile("file", "teste.txt")
	if err != nil {
		t.Fatalf("create form file: %v", err)
	}
	if _, err := part.Write([]byte("conteudo")); err != nil {
		t.Fatalf("write form file: %v", err)
	}
	if err := writer.WriteField("idCatequisando", "1"); err != nil {
		t.Fatalf("write idCatequisando field: %v", err)
	}
	if err := writer.WriteField("tipoDocumento", "RG"); err != nil {
		t.Fatalf("write tipoDocumento field: %v", err)
	}
	if err := writer.WriteField("tipoStatus", "PENDENTE"); err != nil {
		t.Fatalf("write tipoStatus field: %v", err)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("close writer: %v", err)
	}

	uploadReq := httptest.NewRequest(http.MethodPost, "/api/documentos/upload", body)
	uploadReq.Header.Set("Content-Type", writer.FormDataContentType())
	uploadReq.Header.Set("Authorization", "Bearer "+token)
	uploadW := httptest.NewRecorder()
	h.ServeHTTP(uploadW, uploadReq)
	if uploadW.Code != http.StatusCreated {
		t.Fatalf("expected 201 on unified upload, got %d body=%s", uploadW.Code, uploadW.Body.String())
	}
	if !strings.Contains(uploadW.Body.String(), "documento") || !strings.Contains(uploadW.Body.String(), "upload") {
		t.Fatalf("expected unified upload payload with documento and upload, got %s", uploadW.Body.String())
	}
}
