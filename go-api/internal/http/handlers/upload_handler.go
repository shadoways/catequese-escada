package handlers

import (
	"io"
	"net/http"

	"catequese-escada/go-api/internal/http/response"
	"catequese-escada/go-api/internal/upload"
)

type UploadHandler struct {
	service     *upload.Service
	maxUploadMB int64
}

func NewUploadHandler(service *upload.Service, maxUploadMB int64) *UploadHandler {
	if maxUploadMB <= 0 {
		maxUploadMB = 10
	}
	return &UploadHandler{service: service, maxUploadMB: maxUploadMB}
}

func (h *UploadHandler) Upload(w http.ResponseWriter, r *http.Request) {
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

	fileType := r.FormValue("fileType")

	saved, err := h.service.Store(r.Context(), file, fileHeader.Filename, fileType, fileHeader.Header.Get("Content-Type"))
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro ao salvar arquivo")
		return
	}

	response.JSON(w, http.StatusOK, map[string]any{
		"filename": saved.FileName,
		"size":     fileHeader.Size,
		"path":     saved.Path,
		"url":      saved.URL,
	})
}

func (h *UploadHandler) UploadBatch(w http.ResponseWriter, r *http.Request) {
	requestLimit := h.maxUploadMB * 1024 * 1024 * 10
	r.Body = http.MaxBytesReader(w, r.Body, requestLimit)
	if err := r.ParseMultipartForm(requestLimit); err != nil {
		response.Error(w, http.StatusBadRequest, "Upload inválido ou excede tamanho máximo")
		return
	}

	files := r.MultipartForm.File["files"]
	if len(files) == 0 {
		response.Error(w, http.StatusBadRequest, "Arquivos não enviados")
		return
	}

	fileTypes := r.MultipartForm.Value["fileTypes"]
	payloads := make([]upload.UploadPayload, 0, len(files))

	for i, header := range files {
		opened, err := header.Open()
		if err != nil {
			response.Error(w, http.StatusBadRequest, "Falha ao ler arquivo do upload")
			return
		}

		data, err := io.ReadAll(opened)
		_ = opened.Close()
		if err != nil {
			response.Error(w, http.StatusBadRequest, "Falha ao ler conteúdo do arquivo")
			return
		}

		fileType := ""
		if i < len(fileTypes) {
			fileType = fileTypes[i]
		}

		payloads = append(payloads, upload.UploadPayload{
			Data:         data,
			OriginalName: header.Filename,
			FileType:     fileType,
			ContentType:  header.Header.Get("Content-Type"),
		})
	}

	saved, err := h.service.StoreMany(r.Context(), payloads)
	if err != nil {
		response.Error(w, http.StatusInternalServerError, "Erro ao salvar arquivos")
		return
	}

	response.JSON(w, http.StatusOK, map[string]any{"files": saved})
}
