package handlers

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"catequese-escada/go-api/internal/comunidade"
	"catequese-escada/go-api/internal/http/response"

	"github.com/go-chi/chi/v5"
)

type ComunidadeHandler struct {
	service comunidadeService
}

type comunidadeService interface {
	FindAll(ctx context.Context) ([]comunidade.Comunidade, error)
	FindByID(ctx context.Context, id int64) (comunidade.Comunidade, error)
	Create(ctx context.Context, req comunidade.Comunidade) (comunidade.Comunidade, error)
	Update(ctx context.Context, id int64, req comunidade.Comunidade) (comunidade.Comunidade, error)
	Delete(ctx context.Context, id int64) error
}

func NewComunidadeHandler(service comunidadeService) *ComunidadeHandler {
	return &ComunidadeHandler{service: service}
}

func (h *ComunidadeHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	items, err := h.service.FindAll(r.Context())
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *ComunidadeHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.FindByID(r.Context(), id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *ComunidadeHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req comunidade.Comunidade
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Create(r.Context(), req)
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *ComunidadeHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req comunidade.Comunidade
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Update(r.Context(), id, req)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *ComunidadeHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.Delete(r.Context(), id); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
