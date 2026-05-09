package handlers

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"strconv"

	"catequese-escada/go-api/internal/documento"
	"catequese-escada/go-api/internal/http/response"

	"github.com/go-chi/chi/v5"
)

type DocumentoHandler struct {
	service documentoService
}

type documentoService interface {
	FindAll(ctx context.Context) ([]documento.Documento, error)
	FindByID(ctx context.Context, id int64) (documento.Documento, error)
	Create(ctx context.Context, req documento.Documento) (documento.Documento, error)
	Update(ctx context.Context, id int64, req documento.Documento) (documento.Documento, error)
	UpdateStatus(ctx context.Context, id int64, status string) (documento.Documento, error)
	Delete(ctx context.Context, id int64) error
}

func NewDocumentoHandler(service documentoService) *DocumentoHandler {
	return &DocumentoHandler{service: service}
}

func (h *DocumentoHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	items, err := h.service.FindAll(r.Context())
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *DocumentoHandler) GetByID(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.FindByID(r.Context(), id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Documento não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *DocumentoHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req documento.Documento
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Create(r.Context(), req)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Catequisando não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.Header().Set("Location", "/api/documentos/"+strconv.FormatInt(item.IDDocumento, 10))
	response.JSON(w, http.StatusCreated, item)
}

func (h *DocumentoHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req documento.Documento
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.Update(r.Context(), id, req)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Documento não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *DocumentoHandler) UpdateStatus(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req documento.StatusUpdateRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.UpdateStatus(r.Context(), id, req.NovoStatus)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Documento não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *DocumentoHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.Delete(r.Context(), id); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Documento não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
