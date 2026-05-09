package handlers

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/mail"
	"strconv"
	"strings"

	"catequese-escada/go-api/internal/http/response"
	"catequese-escada/go-api/internal/usuario"

	"github.com/go-chi/chi/v5"
)

type UsuarioHandler struct {
	service usuarioService
}

type usuarioService interface {
	FindAll(ctx context.Context) ([]usuario.UsuarioDTO, error)
	FindByID(ctx context.Context, id int64) (usuario.UsuarioDTO, error)
	FindByEmail(ctx context.Context, email string) (usuario.UsuarioDTO, error)
	Create(ctx context.Context, req usuario.CreateUsuarioRequest) (usuario.UsuarioDTO, error)
	Update(ctx context.Context, id int64, req usuario.UpdateUsuarioRequest) (usuario.UsuarioDTO, error)
	ToggleAtivo(ctx context.Context, id int64) (usuario.UsuarioDTO, error)
	Delete(ctx context.Context, id int64) error
}

func NewUsuarioHandler(service usuarioService) *UsuarioHandler {
	return &UsuarioHandler{service: service}
}

func (h *UsuarioHandler) GetAll(w http.ResponseWriter, r *http.Request) {
	items, err := h.service.FindAll(r.Context())
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}
	response.JSON(w, http.StatusOK, items)
}

func (h *UsuarioHandler) GetByID(w http.ResponseWriter, r *http.Request) {
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

func (h *UsuarioHandler) GetByEmail(w http.ResponseWriter, r *http.Request) {
	email := chi.URLParam(r, "email")
	item, err := h.service.FindByEmail(r.Context(), email)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *UsuarioHandler) Create(w http.ResponseWriter, r *http.Request) {
	var req usuario.CreateUsuarioRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if details := validateCreateUsuario(req); len(details) > 0 {
		response.JSON(w, http.StatusBadRequest, map[string]any{"erro": "Validação falhou", "detalhes": details})
		return
	}

	item, err := h.service.Create(r.Context(), req)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}

	w.Header().Set("Location", "/api/usuarios/"+strconv.FormatInt(item.IDUsuario, 10))
	response.JSON(w, http.StatusCreated, item)
}

func (h *UsuarioHandler) Update(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	var req usuario.UpdateUsuarioRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if details := validateUpdateUsuario(req); len(details) > 0 {
		response.JSON(w, http.StatusBadRequest, map[string]any{"erro": "Validação falhou", "detalhes": details})
		return
	}

	item, err := h.service.Update(r.Context(), id, req)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *UsuarioHandler) ToggleAtivo(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	item, err := h.service.ToggleAtivo(r.Context(), id)
	if err != nil {
		h.mapDomainError(w, err)
		return
	}
	response.JSON(w, http.StatusOK, item)
}

func (h *UsuarioHandler) Delete(w http.ResponseWriter, r *http.Request) {
	id, err := strconv.ParseInt(chi.URLParam(r, "id"), 10, 64)
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Requisição inválida")
		return
	}
	if err := h.service.Delete(r.Context(), id); err != nil {
		h.mapDomainError(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (h *UsuarioHandler) mapDomainError(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, usuario.ErrNotFound):
		response.Error(w, http.StatusNotFound, err.Error())
	case errors.Is(err, usuario.ErrEmailExists):
		response.Error(w, http.StatusBadRequest, err.Error())
	case errors.Is(err, usuario.ErrInvalidPassword):
		response.Error(w, http.StatusBadRequest, err.Error())
	case errors.Is(err, usuario.ErrInvalidRole):
		response.Error(w, http.StatusBadRequest, "Role inválida")
	case errors.Is(err, usuario.ErrComunidadeNotFound):
		response.Error(w, http.StatusNotFound, err.Error())
	case errors.Is(err, usuario.ErrCatequistaNotFound):
		response.Error(w, http.StatusNotFound, err.Error())
	default:
		response.Error(w, http.StatusInternalServerError, "Erro interno")
	}
}

func validateCreateUsuario(req usuario.CreateUsuarioRequest) map[string]string {
	errs := map[string]string{}
	if strings.TrimSpace(req.Nome) == "" {
		errs["nome"] = "Nome é obrigatório"
	}
	email := strings.TrimSpace(req.Email)
	if email == "" {
		errs["email"] = "Email é obrigatório"
	} else if _, err := mail.ParseAddress(email); err != nil {
		errs["email"] = "Email inválido"
	}
	if strings.TrimSpace(req.Password) == "" {
		errs["password"] = "Senha é obrigatória"
	} else if len(strings.TrimSpace(req.Password)) < 6 {
		errs["password"] = "Senha deve ter no mínimo 6 caracteres"
	}
	if len(req.Roles) == 0 {
		errs["roles"] = "Pelo menos uma role deve ser informada"
	}
	return errs
}

func validateUpdateUsuario(req usuario.UpdateUsuarioRequest) map[string]string {
	errs := map[string]string{}
	if strings.TrimSpace(req.Nome) == "" {
		errs["nome"] = "Nome é obrigatório"
	}
	email := strings.TrimSpace(req.Email)
	if email == "" {
		errs["email"] = "Email é obrigatório"
	} else if _, err := mail.ParseAddress(email); err != nil {
		errs["email"] = "Email inválido"
	}
	return errs
}
