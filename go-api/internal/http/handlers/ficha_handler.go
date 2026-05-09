package handlers

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"catequese-escada/go-api/internal/ficha"
	"catequese-escada/go-api/internal/http/response"

	"github.com/go-chi/chi/v5"
)

type FichaHandler struct {
	service fichaService
}

type fichaService interface {
	FindAll(ctx context.Context) ([]ficha.FichaInscricao, error)
	FindByID(ctx context.Context, id int64) (ficha.FichaInscricao, error)
	Create(ctx context.Context, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error)
	Update(ctx context.Context, id int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error)
	DeleteByID(ctx context.Context, id int64) error
	DeleteByCatequisandoID(ctx context.Context, catequisandoID int64) error
}

func NewFichaHandler(service fichaService) *FichaHandler {
	return &FichaHandler{service: service}
}

func (h *FichaHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	items, err := h.service.FindAll(r.Context())
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *FichaHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.FindByID(r.Context(), id)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *FichaHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req ficha.FichaInscricaoRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Create(r.Context(), req)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	w.Header().Set("Location", "/api/fichas/"+strconv.FormatInt(item.IDFicha, 10))
	response.JSON(w, http.StatusCreated, item)
}

func (h *FichaHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req ficha.FichaInscricaoRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Update(r.Context(), id, req)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *FichaHandler) DeleteByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.DeleteByID(r.Context(), id); err != nil {
		h.mapDomainError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *FichaHandler) DeleteByCatequisandoID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "catequisandoId"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.DeleteByCatequisandoID(r.Context(), id); err != nil {
		h.mapDomainError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *FichaHandler) mapDomainError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, ficha.ErrNotFound):
		response.Error(w, http.StatusNotFound, err.Error())
	case errors.Is(err, ficha.ErrCatequisandoNotFound):
		response.Error(w, http.StatusNotFound, err.Error())
	default:
		response.Error(w, http.StatusInternalServerError, "Erro interno")
	}
}
