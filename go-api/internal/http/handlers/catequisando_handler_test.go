package handlers

import (
	"bytes"
	"context"
	"database/sql"
	"net/http"
	"net/http/httptest"
	"testing"

	"catequese-escada/go-api/internal/catequisando"

	"github.com/go-chi/chi/v5"
)

type fakeCateqRepo struct {
	findAllFn  func(ctx context.Context) ([]catequisando.Catequisando, error)
	findByIDFn func(ctx context.Context, id int64) (catequisando.Catequisando, error)
	createFn   func(ctx context.Context, c catequisando.Catequisando) (int64, error)
	updateFn   func(ctx context.Context, id int64, c catequisando.Catequisando) error
	deleteFn   func(ctx context.Context, id int64) error
}

func (f *fakeCateqRepo) FindAll(ctx context.Context) ([]catequisando.Catequisando, error) {
	return f.findAllFn(ctx)
}
func (f *fakeCateqRepo) FindByID(ctx context.Context, id int64) (catequisando.Catequisando, error) {
	return f.findByIDFn(ctx, id)
}
func (f *fakeCateqRepo) Create(ctx context.Context, c catequisando.Catequisando) (int64, error) {
	return f.createFn(ctx, c)
}
func (f *fakeCateqRepo) Update(ctx context.Context, id int64, c catequisando.Catequisando) error {
	return f.updateFn(ctx, id, c)
}
func (f *fakeCateqRepo) Delete(ctx context.Context, id int64) error {
	return f.deleteFn(ctx, id)
}

func TestCatequisandoGetByIDNotFound(t *testing.T) {
	h := NewCatequisandoHandler(&fakeCateqRepo{
		findAllFn: func(ctx context.Context) ([]catequisando.Catequisando, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (catequisando.Catequisando, error) {
			return catequisando.Catequisando{}, sql.ErrNoRows
		},
		createFn: func(ctx context.Context, c catequisando.Catequisando) (int64, error) { return 0, nil },
		updateFn: func(ctx context.Context, id int64, c catequisando.Catequisando) error { return nil },
		deleteFn: func(ctx context.Context, id int64) error { return nil },
	})

	req := httptest.NewRequest(http.MethodGet, "/api/catequisandos/10", nil)
	rctx := chi.NewRouteContext()
	rctx.URLParams.Add("id", "10")
	req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))
	w := httptest.NewRecorder()

	h.GetByID(w, req)

	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d", w.Code)
	}
}

func TestCatequisandoCreateBadJSON(t *testing.T) {
	h := NewCatequisandoHandler(&fakeCateqRepo{
		findAllFn: func(ctx context.Context) ([]catequisando.Catequisando, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (catequisando.Catequisando, error) {
			return catequisando.Catequisando{}, nil
		},
		createFn: func(ctx context.Context, c catequisando.Catequisando) (int64, error) { return 0, nil },
		updateFn: func(ctx context.Context, id int64, c catequisando.Catequisando) error { return nil },
		deleteFn: func(ctx context.Context, id int64) error { return nil },
	})

	req := httptest.NewRequest(http.MethodPost, "/api/catequisandos", bytes.NewBufferString("{"))
	w := httptest.NewRecorder()
	h.Create(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Code)
	}
}
