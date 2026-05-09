package handlers

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"catequese-escada/go-api/internal/evento"
	"catequese-escada/go-api/internal/http/response"

	"github.com/go-chi/chi/v5"
)

type EventoHandler struct {
	service eventoService
}

type eventoService interface {
	FindAll(ctx context.Context) ([]evento.Evento, error)
	FindByID(ctx context.Context, id int64) (evento.Evento, error)
	Create(ctx context.Context, req evento.Evento) (evento.Evento, error)
	Update(ctx context.Context, id int64, req evento.Evento) (evento.Evento, error)
	Delete(ctx context.Context, id int64) error
}

func NewEventoHandler(service eventoService) *EventoHandler {
	return &EventoHandler{service: service}
}

func (h *EventoHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	items, err := h.service.FindAll(r.Context())
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *EventoHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.FindByID(r.Context(), id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Evento não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *EventoHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req evento.Evento
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Create(r.Context(), req)
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.Header().Set("Location", "/api/eventos/"+strconv.FormatInt(item.IDEvento, 10))
	response.JSON(w, http.StatusCreated, item)
}

func (h *EventoHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req evento.Evento
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Update(r.Context(), id, req)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Evento não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *EventoHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.Delete(r.Context(), id); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Evento não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
