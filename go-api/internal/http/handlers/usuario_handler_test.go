package handlers

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"catequese-escada/go-api/internal/usuario"

	"github.com/go-chi/chi/v5"
)

type fakeUsuarioService struct {
	findAllFn     func(ctx context.Context) ([]usuario.UsuarioDTO, error)
	findByIDFn    func(ctx context.Context, id int64) (usuario.UsuarioDTO, error)
	findByEmailFn func(ctx context.Context, email string) (usuario.UsuarioDTO, error)
	createFn      func(ctx context.Context, req usuario.CreateUsuarioRequest) (usuario.UsuarioDTO, error)
	updateFn      func(ctx context.Context, id int64, req usuario.UpdateUsuarioRequest) (usuario.UsuarioDTO, error)
	toggleFn      func(ctx context.Context, id int64) (usuario.UsuarioDTO, error)
	deleteFn      func(ctx context.Context, id int64) error
}

func (f *fakeUsuarioService) FindAll(ctx context.Context) ([]usuario.UsuarioDTO, error) {
	return f.findAllFn(ctx)
}
func (f *fakeUsuarioService) FindByID(ctx context.Context, id int64) (usuario.UsuarioDTO, error) {
	return f.findByIDFn(ctx, id)
}
func (f *fakeUsuarioService) FindByEmail(ctx context.Context, email string) (usuario.UsuarioDTO, error) {
	return f.findByEmailFn(ctx, email)
}
func (f *fakeUsuarioService) Create(ctx context.Context, req usuario.CreateUsuarioRequest) (usuario.UsuarioDTO, error) {
	return f.createFn(ctx, req)
}
func (f *fakeUsuarioService) Update(ctx context.Context, id int64, req usuario.UpdateUsuarioRequest) (usuario.UsuarioDTO, error) {
	return f.updateFn(ctx, id, req)
}
func (f *fakeUsuarioService) ToggleAtivo(ctx context.Context, id int64) (usuario.UsuarioDTO, error) {
	return f.toggleFn(ctx, id)
}
func (f *fakeUsuarioService) Delete(ctx context.Context, id int64) error {
	return f.deleteFn(ctx, id)
}

func TestUsuarioCreateValidation(t *testing.T) {
	h := NewUsuarioHandler(&fakeUsuarioService{
		findAllFn:     func(ctx context.Context) ([]usuario.UsuarioDTO, error) { return nil, nil },
		findByIDFn:    func(ctx context.Context, id int64) (usuario.UsuarioDTO, error) { return usuario.UsuarioDTO{}, nil },
		findByEmailFn: func(ctx context.Context, email string) (usuario.UsuarioDTO, error) { return usuario.UsuarioDTO{}, nil },
		createFn: func(ctx context.Context, req usuario.CreateUsuarioRequest) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		updateFn: func(ctx context.Context, id int64, req usuario.UpdateUsuarioRequest) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		toggleFn: func(ctx context.Context, id int64) (usuario.UsuarioDTO, error) { return usuario.UsuarioDTO{}, nil },
		deleteFn: func(ctx context.Context, id int64) error { return nil },
	})

	body := []byte(`{"nome":"","email":"invalido","password":"123","roles":[]}`)
	req := httptest.NewRequest(http.MethodPost, "/api/usuarios", bytes.NewReader(body))
	w := httptest.NewRecorder()

	h.Create(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Code)
	}
	if !bytes.Contains(w.Body.Bytes(), []byte(`"erro":"Validação falhou"`)) {
		t.Fatalf("unexpected body: %s", w.Body.String())
	}
}

func TestUsuarioGetByIDNotFound(t *testing.T) {
	h := NewUsuarioHandler(&fakeUsuarioService{
		findAllFn: func(ctx context.Context) ([]usuario.UsuarioDTO, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, usuario.ErrNotFound
		},
		findByEmailFn: func(ctx context.Context, email string) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		createFn: func(ctx context.Context, req usuario.CreateUsuarioRequest) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		updateFn: func(ctx context.Context, id int64, req usuario.UpdateUsuarioRequest) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		toggleFn: func(ctx context.Context, id int64) (usuario.UsuarioDTO, error) { return usuario.UsuarioDTO{}, nil },
		deleteFn: func(ctx context.Context, id int64) error { return nil },
	})

	req := httptest.NewRequest(http.MethodGet, "/api/usuarios/12", nil)
	rctx := chi.NewRouteContext()
	rctx.URLParams.Add("id", "12")
	req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))
	w := httptest.NewRecorder()

	h.GetByID(w, req)

	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Code)
	}
}

func TestUsuarioDeleteNoContent(t *testing.T) {
	h := NewUsuarioHandler(&fakeUsuarioService{
		findAllFn:  func(ctx context.Context) ([]usuario.UsuarioDTO, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (usuario.UsuarioDTO, error) { return usuario.UsuarioDTO{}, nil },
		findByEmailFn: func(ctx context.Context, email string) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		createFn: func(ctx context.Context, req usuario.CreateUsuarioRequest) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		updateFn: func(ctx context.Context, id int64, req usuario.UpdateUsuarioRequest) (usuario.UsuarioDTO, error) {
			return usuario.UsuarioDTO{}, nil
		},
		toggleFn: func(ctx context.Context, id int64) (usuario.UsuarioDTO, error) { return usuario.UsuarioDTO{}, nil },
		deleteFn: func(ctx context.Context, id int64) error { return nil },
	})

	req := httptest.NewRequest(http.MethodDelete, "/api/usuarios/12", nil)
	rctx := chi.NewRouteContext()
	rctx.URLParams.Add("id", "12")
	req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))
	w := httptest.NewRecorder()

	h.Delete(w, req)

	if w.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", w.Code)
	}
}
