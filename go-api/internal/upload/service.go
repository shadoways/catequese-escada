package upload

import (
	"context"
	"fmt"
	"io"
	"mime/multipart"
	"os"
	"regexp"
	"strings"
	"sync"

	"cloud.google.com/go/storage"
	"github.com/google/uuid"
	"google.golang.org/api/option"
)

type SavedFile struct {
	FileName string `json:"filename"`
	Path     string `json:"path"`
	URL      string `json:"url"`
}

type UploadPayload struct {
	Data         []byte
	OriginalName string
	FileType     string
	ContentType  string
}

type objectStore interface {
	Upload(ctx context.Context, objectName, contentType string, data []byte) error
	Delete(ctx context.Context, objectName string) error
}

type gcsObjectStore struct {
	client *storage.Client
	bucket string
}

func newGCSObjectStore(ctx context.Context, bucket string) (objectStore, error) {
	credJSON := strings.TrimSpace(os.Getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON"))
	options := make([]option.ClientOption, 0, 1)
	if strings.HasPrefix(credJSON, "{") {
		options = append(options, option.WithCredentialsJSON([]byte(credJSON)))
	}

	client, err := storage.NewClient(ctx, options...)
	if err != nil {
		return nil, err
	}
	return &gcsObjectStore{client: client, bucket: bucket}, nil
}

func (g *gcsObjectStore) Upload(ctx context.Context, objectName, contentType string, data []byte) error {
	w := g.client.Bucket(g.bucket).Object(objectName).NewWriter(ctx)
	if strings.TrimSpace(contentType) != "" {
		w.ContentType = contentType
	} else {
		w.ContentType = "application/octet-stream"
	}

	if _, err := w.Write(data); err != nil {
		_ = w.Close()
		return err
	}
	return w.Close()
}

func (g *gcsObjectStore) Delete(ctx context.Context, objectName string) error {
	return g.client.Bucket(g.bucket).Object(objectName).Delete(ctx)
}

type MemoryStore struct {
	mu      sync.Mutex
	objects map[string][]byte
}

func NewMemoryStore() *MemoryStore {
	return &MemoryStore{objects: make(map[string][]byte)}
}

func (m *MemoryStore) Upload(_ context.Context, objectName, _ string, data []byte) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	cp := make([]byte, len(data))
	copy(cp, data)
	m.objects[objectName] = cp
	return nil
}

func (m *MemoryStore) Delete(_ context.Context, objectName string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.objects, objectName)
	return nil
}

type Service struct {
	bucketName    string
	publicBaseURL string
	store         objectStore
}

var safeSegmentRegex = regexp.MustCompile(`[^a-zA-Z0-9_-]+`)

func NewService(bucketName, publicBaseURL string) (*Service, error) {
	bucketName = strings.TrimSpace(bucketName)
	if bucketName == "" {
		return nil, fmt.Errorf("GCS bucket não configurado")
	}

	store, err := newGCSObjectStore(context.Background(), bucketName)
	if err != nil {
		return nil, fmt.Errorf("erro ao inicializar cliente GCS: %w", err)
	}
	return NewServiceWithStore(bucketName, publicBaseURL, store)
}

func NewServiceWithStore(bucketName, publicBaseURL string, store objectStore) (*Service, error) {
	bucketName = strings.TrimSpace(bucketName)
	if bucketName == "" {
		return nil, fmt.Errorf("bucket não pode ser vazio")
	}
	if store == nil {
		return nil, fmt.Errorf("store não pode ser nil")
	}

	return &Service{
		bucketName:    bucketName,
		publicBaseURL: strings.TrimSpace(publicBaseURL),
		store:         store,
	}, nil
}

func (s *Service) Store(ctx context.Context, file multipart.File, originalName, fileType, contentType string) (SavedFile, error) {
	data, err := io.ReadAll(file)
	if err != nil {
		return SavedFile{}, err
	}
	results, err := s.StoreMany(ctx, []UploadPayload{{
		Data:         data,
		OriginalName: originalName,
		FileType:     fileType,
		ContentType:  contentType,
	}})
	if err != nil {
		return SavedFile{}, err
	}
	return results[0], nil
}

func (s *Service) StoreMany(ctx context.Context, payloads []UploadPayload) ([]SavedFile, error) {
	if len(payloads) == 0 {
		return nil, fmt.Errorf("nenhum arquivo informado")
	}

	saved := make([]SavedFile, 0, len(payloads))
	uploadedObjects := make([]string, 0, len(payloads))

	for _, p := range payloads {
		clean := sanitizeFilename(p.OriginalName)
		if clean == "" || clean == "." || clean == "/" {
			clean = "arquivo.bin"
		}

		safeType := strings.ToLower(sanitizeSegment(p.FileType))
		objectName := fmt.Sprintf("%s_%s", uuid.NewString(), clean)
		if safeType != "" {
			objectName = safeType + "/" + objectName
		}

		if err := s.store.Upload(ctx, objectName, p.ContentType, p.Data); err != nil {
			for i := len(uploadedObjects) - 1; i >= 0; i-- {
				_ = s.store.Delete(ctx, uploadedObjects[i])
			}
			return nil, err
		}

		uploadedObjects = append(uploadedObjects, objectName)
		gcsPath := "gs://" + s.bucketName + "/" + objectName
		saved = append(saved, SavedFile{FileName: objectName, Path: gcsPath, URL: s.publicURL(objectName)})
	}

	return saved, nil
}

func sanitizeSegment(v string) string {
	v = strings.TrimSpace(v)
	if v == "" {
		return ""
	}
	v = safeSegmentRegex.ReplaceAllString(v, "_")
	v = strings.Trim(v, "_-")
	return v
}

func sanitizeFilename(originalName string) string {
	base := originalName
	if i := strings.LastIndexAny(base, "/\\"); i >= 0 {
		base = base[i+1:]
	}
	dotIdx := strings.LastIndex(base, ".")
	namePart := base
	extPart := ""
	if dotIdx > 0 {
		namePart = base[:dotIdx]
		extPart = base[dotIdx:]
	}
	namePart = safeSegmentRegex.ReplaceAllString(namePart, "_")
	namePart = strings.Trim(namePart, "_-")
	if namePart == "" {
		namePart = "arquivo"
	}
	return namePart + extPart
}

func (s *Service) publicURL(objectName string) string {
	if s.publicBaseURL != "" {
		return strings.TrimRight(s.publicBaseURL, "/") + "/" + objectName
	}
	return "https://storage.googleapis.com/" + s.bucketName + "/" + objectName
}
