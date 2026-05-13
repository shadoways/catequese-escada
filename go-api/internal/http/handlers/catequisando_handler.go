package handlers

import (
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"strconv"

	"catequese-escada/go-api/internal/catequisando"
	"catequese-escada/go-api/internal/http/response"

	"github.com/go-chi/chi/v5"
)

type CatequisandoHandler struct {
	repo catequisandoRepository
}

type catequisandoRepository interface {
	FindAll(ctx context.Context) ([]catequisando.Catequisando, error)
	FindByID(ctx context.Context, id int64) (catequisando.Catequisando, error)
	Create(ctx context.Context, c catequisando.Catequisando) (int64, error)
	Update(ctx context.Context, id int64, c catequisando.Catequisando) error
	Delete(ctx context.Context, id int64) error
}

func NewCatequisandoHandler(repo catequisandoRepository) *CatequisandoHandler {
	return &CatequisandoHandler{repo: repo}
}

func (h *CatequisandoHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	items, err := h.repo.FindAll(r.Context())
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *CatequisandoHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	item, err := h.repo.FindByID(r.Context(), id)
	if err != nil {
		if err == sql.ErrNoRows {
			response.Error(w, http.StatusNotFound, "Catequisando não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *CatequisandoHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req catequisando.Catequisando
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	id, err := h.repo.Create(r.Context(), req)
	if err != nil {
		if err == catequisando.ErrDocumentoCivilDuplicado {
			response.Error(w, http.StatusConflict, "Documento civil já vinculado a outro catequisando")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	created, err := h.repo.FindByID(r.Context(), id)
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	w.Header().Set("Location", "/api/catequisandos/"+strconv.FormatInt(id, 10))
	response.JSON(w, http.StatusCreated, created)
}

func (h *CatequisandoHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	var req catequisando.Catequisando
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	if _, err := h.repo.FindByID(r.Context(), id); err != nil {
		if err == sql.ErrNoRows {
			response.Error(w, http.StatusNotFound, "Catequisando não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	if err := h.repo.Update(r.Context(), id, req); err != nil {
		if err == catequisando.ErrDocumentoCivilDuplicado {
			response.Error(w, http.StatusConflict, "Documento civil já vinculado a outro catequisando")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	updated, err := h.repo.FindByID(r.Context(), id)
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, updated)
}

func (h *CatequisandoHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}

	if err := h.repo.Delete(r.Context(), id); err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
