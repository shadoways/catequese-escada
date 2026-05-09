package handlers

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"catequese-escada/go-api/internal/http/response"
	"catequese-escada/go-api/internal/turma"

	"github.com/go-chi/chi/v5"
)

type TurmaHandler struct {
	service turmaService
}

type turmaService interface {
	FindAll(ctx context.Context) ([]turma.Turma, error)
	FindByID(ctx context.Context, id int64) (turma.Turma, error)
	Create(ctx context.Context, req turma.Turma) (turma.Turma, error)
	Update(ctx context.Context, id int64, req turma.Turma) (turma.Turma, error)
	Delete(ctx context.Context, id int64) error
}

func NewTurmaHandler(service turmaService) *TurmaHandler {
	return &TurmaHandler{service: service}
}

func (h *TurmaHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	items, err := h.service.FindAll(r.Context())
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *TurmaHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.FindByID(r.Context(), id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Turma não encontrada")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *TurmaHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req turma.Turma
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Create(r.Context(), req)
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.Header().Set("Location", "/api/turmas/"+strconv.FormatInt(item.IDTurma, 10))
	response.JSON(w, http.StatusCreated, item)
}

func (h *TurmaHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req turma.Turma
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Update(r.Context(), id, req)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Turma não encontrada")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *TurmaHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.Delete(r.Context(), id); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Turma não encontrada")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
