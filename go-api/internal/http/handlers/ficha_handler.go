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
	FindByCatequisandoID(ctx context.Context, catequisandoID int64) ([]ficha.FichaInscricao, error)
	FindByIDAndCatequisandoID(ctx context.Context, id int64, catequisandoID int64) (ficha.FichaInscricao, error)
	CreateForCatequisando(ctx context.Context, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error)
	UpdateForCatequisando(ctx context.Context, id int64, catequisandoID int64, req ficha.FichaInscricaoRequest) (ficha.FichaInscricao, error)
	DeleteByIDAndCatequisandoID(ctx context.Context, id int64, catequisandoID int64) error
}

func NewFichaHandler(service fichaService) *FichaHandler {
	return &FichaHandler{service: service}
}

func (h *FichaHandler) GetByCatequisandoID(w http.ResponseWriter, r *http.Request) {
	catequisandoID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	items, err := h.service.FindByCatequisandoID(r.Context(), catequisandoID)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *FichaHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	catequisandoID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	fichaID, err := strconv.ParseInt(chi.URLParam(r, "idFicha"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.FindByIDAndCatequisandoID(r.Context(), fichaID, catequisandoID)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *FichaHandler) Create(w http.ResponseWriter, r *http.Request) {
	catequisandoID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req ficha.FichaInscricaoRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.CreateForCatequisando(r.Context(), catequisandoID, req)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	w.Header().Set("Location", "/api/catequisandos/"+strconv.FormatInt(catequisandoID, 10)+"/fichas/"+strconv.FormatInt(item.IDFicha, 10))
	response.JSON(w, http.StatusCreated, item)
}

func (h *FichaHandler) Update(w http.ResponseWriter, r *http.Request) {
	catequisandoID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	fichaID, err := strconv.ParseInt(chi.URLParam(r, "idFicha"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req ficha.FichaInscricaoRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.UpdateForCatequisando(r.Context(), fichaID, catequisandoID, req)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *FichaHandler) DeleteByID(w http.ResponseWriter, r *http.Request) {
	catequisandoID, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	fichaID, err := strconv.ParseInt(chi.URLParam(r, "idFicha"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.DeleteByIDAndCatequisandoID(r.Context(), fichaID, catequisandoID); err != nil {
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
