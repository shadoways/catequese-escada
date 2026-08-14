package handlers

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"testing"

	"catequese-escada/go-api/internal/ficha"

	"github.com/go-chi/chi/v5"
)

type fakeFichaService struct {
	findByCatequisandoIDFn        func(ctx context.Context, catequisandoID int64) ([]ficha.FichaInscricao, error)
	findByIDAndCatequisandoIDFn   func(ctx context.Context, id int64, catequisandoID int64) (ficha.FichaInscricao, error)
	createForCatequisandoFn       func(ctx context.Context, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error)
	updateForCatequisandoFn       func(ctx context.Context, id int64, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error)
	deleteByIDAndCatequisandoIDFn func(ctx context.Context, id int64, catequisandoID int64) error
}

func (f *fakeFichaService) FindByCatequisandoID(ctx context.Context, catequisandoID int64) ([]ficha.FichaInscricao, error) {
	return f.findByCatequisandoIDFn(ctx, catequisandoID)
}

func (f *fakeFichaService) FindByIDAndCatequisandoID(ctx context.Context, id int64, catequisandoID int64) (ficha.FichaInscricao, error) {
	return f.findByIDAndCatequisandoIDFn(ctx, id, catequisandoID)
}

func (f *fakeFichaService) CreateForCatequisando(ctx context.Context, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error) {
	return f.createForCatequisandoFn(ctx, catequisandoID, req)
}

func (f *fakeFichaService) UpdateForCatequisando(ctx context.Context, id int64, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error) {
	return f.updateForCatequisandoFn(ctx, id, catequisandoID, req)
}

func (f *fakeFichaService) DeleteByIDAndCatequisandoID(ctx context.Context, id int64, catequisandoID int64) error {
	return f.deleteByIDAndCatequisandoIDFn(ctx, id, catequisandoID)
}

func TestFichaCreateReturnsBadRequestForInvalidDate(t *testing.T) {
	h := NewFichaHandler(&fakeFichaService{
		findByCatequisandoIDFn: func(ctx context.Context, catequisandoID int64) ([]ficha.FichaInscricao, error) {
			return nil, nil
		},
		findByIDAndCatequisandoIDFn: func(ctx context.Context, id int64, catequisandoID int64) (ficha.FichaInscricao, error) {
			return ficha.FichaInscricao{}, nil
		},
		createForCatequisandoFn: func(ctx context.Context, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error) {
			return ficha.FichaInscricao{}, ficha.ErrInvalidDataInscricao
		},
		updateForCatequisandoFn: func(ctx context.Context, id int64, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error) {
			return ficha.FichaInscricao{}, nil
		},
		deleteByIDAndCatequisandoIDFn: func(ctx context.Context, id int64, catequisandoID int64) error {
			return nil
		},
	})

	req := httptest.NewRequest(http.MethodPost, "/api/catequisandos/10/fichas", bytes.NewBufferString(`{"dataInscricao":"2026-02-30","observacoes":"x"}`))
	rctx := chi.NewRouteContext()
	rctx.URLParams.Add("id", "10")
	req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))
	w := httptest.NewRecorder()

	h.Create(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Code)
	}
}

func TestFichaUpdateReturnsBadRequestForInvalidDate(t *testing.T) {
	h := NewFichaHandler(&fakeFichaService{
		findByCatequisandoIDFn: func(ctx context.Context, catequisandoID int64) ([]ficha.FichaInscricao, error) {
			return nil, nil
		},
		findByIDAndCatequisandoIDFn: func(ctx context.Context, id int64, catequisandoID int64) (ficha.FichaInscricao, error) {
			return ficha.FichaInscricao{}, nil
		},
		createForCatequisandoFn: func(ctx context.Context, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error) {
			return ficha.FichaInscricao{}, nil
		},
		updateForCatequisandoFn: func(ctx context.Context, id int64, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error) {
			return ficha.FichaInscricao{}, ficha.ErrInvalidDataInscricao
		},
		deleteByIDAndCatequisandoIDFn: func(ctx context.Context, id int64, catequisandoID int64) error {
			return nil
		},
	})

	req := httptest.NewRequest(http.MethodPut, "/api/catequisandos/10/fichas/2", bytes.NewBufferString(`{"dataInscricao":"13/05/2026","observacoes":"x"}`))
	rctx := chi.NewRouteContext()
	rctx.URLParams.Add("id", "10")
	rctx.URLParams.Add("idFicha", "2")
	req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))
	w := httptest.NewRecorder()

	h.Update(w, req)

	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", w.Code)
	}
}
