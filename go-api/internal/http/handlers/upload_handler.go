package handlers

import (
	"context"
	"database/sql"
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"

	"catequese-escada/go-api/internal/documento"
	"catequese-escada/go-api/internal/http/middleware"
	"catequese-escada/go-api/internal/http/response"
	"catequese-escada/go-api/internal/upload"
)

type UploadHandler struct {
	service          *upload.Service
	documentoService documentoCreatorService
	maxUploadMB      int64
}

type documentoCreatorService interface {
	FindByCatequisandoAndTipoDocumento(ctx context.Context, catequisandoID int64, tipoDocumento string) (documento.Documento, error)
	Save(ctx context.Context, req documento.Documento) (documento.Documento, error)
}

func NewUploadHandler(service *upload.Service, documentoService documentoCreatorService, maxUploadMB int64) *UploadHandler {
	if maxUploadMB <= 0 {
		maxUploadMB = 10
	}
	return &UploadHandler{service: service, documentoService: documentoService, maxUploadMB: maxUploadMB}
}

func (h *UploadHandler) UploadDocumento(w http.ResponseWriter, r *http.Request) {
	if h.documentoService == nil {
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	r.Body = http.MaxBytesReader(w, r.Body, h.maxUploadMB*1024*1024)
	if err := r.ParseMultipartForm(h.maxUploadMB * 1024 * 1024); err != nil {
		response.Error(w, http.StatusBadRequest, "Upload inválido ou excede tamanho máximo")
		return
	}

	file, fileHeader, err := r.FormFile("file")
	if err != nil {
		response.Error(w, http.StatusBadRequest, "Arquivo não enviado")
		return
	}
	defer file.Close()

	idCatequisando, err := strconv.ParseInt(strings.TrimSpace(r.FormValue("idCatequisando")), 10, 64)
	if err != nil || idCatequisando <= 0 {
		response.Error(w, http.StatusBadRequest, "idCatequisando inválido")
		return
	}

	fileType := r.FormValue("fileType")
	tipoDocumento := strings.TrimSpace(r.FormValue("tipoDocumento"))
	tipoStatus := strings.TrimSpace(r.FormValue("tipoStatus"))
	dataEnvio := strings.TrimSpace(r.FormValue("dataEnvio"))
	correlationID := middleware.CorrelationIDFromContext(r.Context())

	previousPath := ""
	if tipoDocumento != "" {
		existing, lookupErr := h.documentoService.FindByCatequisandoAndTipoDocumento(r.Context(), idCatequisando, tipoDocumento)
		if lookupErr != nil && !errors.Is(lookupErr, sql.ErrNoRows) {
			response.Error(w, http.StatusInternalServerError, "Erro interno")
			return
		}
		if lookupErr == nil {
			previousPath = strings.TrimSpace(existing.CaminhoArquivo)
		}
	}

	saved, err := h.service.Store(r.Context(), file, fileHeader.Filename, fileType, fileHeader.Header.Get("Content-Type"))
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro ao salvar arquivo")
		return
	}

	created, err := h.documentoService.Save(r.Context(), documento.Documento{
		TipoDocumento:  tipoDocumento,
		CaminhoArquivo: saved.Path,
		DataEnvio:      dataEnvio,
		TipoStatus:     tipoStatus,
		Catequisando:   &documento.CatequisandoRef{IDCatequisando: idCatequisando},
	})
	if err != nil {
		if delErr := h.service.DeleteObject(r.Context(), saved.FileName); delErr != nil {
			log.Printf("warn: corr=%s failed to rollback uploaded object=%s after documento create failure: %v", correlationID, saved.FileName, delErr)
		}
		if errors.Is(err, sql.ErrNoRows) {
			response.Error(w, http.StatusNotFound, "Catequisando não encontrado")
			return
		}
		response.Error(w, http.StatusInternalServerError, "Erro interno")
		return
	}

	if previousPath != "" && previousPath != created.CaminhoArquivo {
		if delErr := h.service.DeletePath(r.Context(), previousPath); delErr != nil {
			log.Printf("warn: corr=%s failed to delete previous uploaded path=%s after upsert documento id=%d: %v", correlationID, previousPath, created.IDDocumento, delErr)
		}
	}

	w.Header().Set("Location", "/api/documentos/"+strconv.FormatInt(created.IDDocumento, 10))
	response.JSON(w, http.StatusCreated, map[string]any{
		"documento": created,
		"upload": map[string]any{
			"filename": saved.FileName,
			"path":     saved.Path,
			"url":      saved.URL,
			"size":     fileHeader.Size,
		},
	})
}
