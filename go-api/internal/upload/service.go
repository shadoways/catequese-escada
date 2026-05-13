package upload

import (
	"context"
	"fmt"
	"io"
	"log"
	"mime/multipart"
	"net/url"
	"os"
	"path/filepath"
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
	storageType   string
	bucketName    string
	localDir      string
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
	service, err := NewServiceWithStore(bucketName, publicBaseURL, store)
	if err != nil {
		return nil, err
	}
	service.storageType = "gcs"
	return service, nil
}

type localObjectStore struct {
	rootDir string
}

func newLocalObjectStore(rootDir string) (objectStore, error) {
	clean := strings.TrimSpace(rootDir)
	if clean == "" {
		return nil, fmt.Errorf("diretório local de upload não configurado")
	}
	if err := os.MkdirAll(clean, 0o755); err != nil {
		return nil, err
	}
	return &localObjectStore{rootDir: clean}, nil
}

func (l *localObjectStore) Upload(_ context.Context, objectName, _ string, data []byte) error {
	target := filepath.Join(l.rootDir, filepath.FromSlash(objectName))
	if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
		return err
	}
	return os.WriteFile(target, data, 0o644)
}

func (l *localObjectStore) Delete(_ context.Context, objectName string) error {
	target := filepath.Join(l.rootDir, filepath.FromSlash(objectName))
	err := os.Remove(target)
	if err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func NewLocalService(localDir, publicBaseURL string) (*Service, error) {
	store, err := newLocalObjectStore(localDir)
	if err != nil {
		return nil, fmt.Errorf("erro ao inicializar storage local: %w", err)
	}
	service, err := NewServiceWithStore("local", publicBaseURL, store)
	if err != nil {
		return nil, err
	}
	service.storageType = "local"
	service.localDir = strings.TrimSpace(localDir)
	return service, nil
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
		storageType:   "gcs",
		bucketName:    bucketName,
		publicBaseURL: strings.TrimSpace(publicBaseURL),
		store:         store,
	}, nil
}

func (s *Service) Store(ctx context.Context, file multipart.File, originalName, fileType, contentType string) (SavedFile, error) {
	log.Printf("upload.store started name=%s fileType=%s storage=%s", originalName, fileType, s.storageType)
	data, err := io.ReadAll(file)
	if err != nil {
		log.Printf("upload.store failed name=%s reason=read_error err=%v", originalName, err)
		return SavedFile{}, err
	}
	results, err := s.StoreMany(ctx, []UploadPayload{{
		Data:         data,
		OriginalName: originalName,
		FileType:     fileType,
		ContentType:  contentType,
	}})
	if err != nil {
		log.Printf("upload.store failed name=%s reason=store_many_error err=%v", originalName, err)
		return SavedFile{}, err
	}
	log.Printf("upload.store completed name=%s path=%s", originalName, results[0].Path)
	return results[0], nil
}

func (s *Service) StoreMany(ctx context.Context, payloads []UploadPayload) ([]SavedFile, error) {
	log.Printf("upload.store_many started count=%d storage=%s", len(payloads), s.storageType)
	if len(payloads) == 0 {
		log.Printf("upload.store_many failed reason=no_files")
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
			log.Printf("upload.store_many failed object=%s err=%v rollback_count=%d", objectName, err, len(uploadedObjects))
			for i := len(uploadedObjects) - 1; i >= 0; i-- {
				if delErr := s.store.Delete(ctx, uploadedObjects[i]); delErr != nil {
					log.Printf("upload.store_many rollback_failed object=%s err=%v", uploadedObjects[i], delErr)
				} else {
					log.Printf("upload.store_many rollback_deleted object=%s", uploadedObjects[i])
				}
			}
			return nil, err
		}

		uploadedObjects = append(uploadedObjects, objectName)
		saved = append(saved, SavedFile{FileName: objectName, Path: s.storagePath(objectName), URL: s.publicURL(objectName)})
	}

	log.Printf("upload.store_many completed count=%d", len(saved))

	return saved, nil
}

func (s *Service) DeleteObject(ctx context.Context, objectName string) error {
	if strings.TrimSpace(objectName) == "" {
		return fmt.Errorf("objectName não pode ser vazio")
	}
	return s.store.Delete(ctx, objectName)
}

func (s *Service) DeletePath(ctx context.Context, storedPath string) error {
	objectName, err := s.objectNameFromStoredPath(storedPath)
	if err != nil {
		return err
	}
	return s.DeleteObject(ctx, objectName)
}

func (s *Service) objectNameFromStoredPath(storedPath string) (string, error) {
	path := strings.TrimSpace(storedPath)
	if path == "" {
		return "", fmt.Errorf("storedPath não pode ser vazio")
	}

	if strings.HasPrefix(path, "gs://") {
		rest := strings.TrimPrefix(path, "gs://")
		parts := strings.SplitN(rest, "/", 2)
		if len(parts) != 2 || strings.TrimSpace(parts[1]) == "" {
			return "", fmt.Errorf("storedPath inválido: %s", storedPath)
		}
		return parts[1], nil
	}

	if strings.HasPrefix(path, "https://storage.googleapis.com/") {
		rest := strings.TrimPrefix(path, "https://storage.googleapis.com/")
		parts := strings.SplitN(rest, "/", 2)
		if len(parts) != 2 || strings.TrimSpace(parts[1]) == "" {
			return "", fmt.Errorf("storedPath inválido: %s", storedPath)
		}
		return parts[1], nil
	}

	if strings.TrimSpace(s.publicBaseURL) != "" {
		base := strings.TrimRight(strings.TrimSpace(s.publicBaseURL), "/") + "/"
		if strings.HasPrefix(path, base) {
			candidate := strings.TrimPrefix(path, base)
			if candidate == "" {
				return "", fmt.Errorf("storedPath inválido: %s", storedPath)
			}
			return candidate, nil
		}
	}

	if strings.HasPrefix(path, "/uploads/") {
		candidate := strings.TrimPrefix(path, "/uploads/")
		if candidate == "" {
			return "", fmt.Errorf("storedPath inválido: %s", storedPath)
		}
		return candidate, nil
	}

	if parsed, err := url.Parse(path); err == nil && parsed.Scheme != "" && parsed.Path != "" {
		candidate := strings.TrimPrefix(parsed.Path, "/")
		parts := strings.SplitN(candidate, "/", 2)
		if len(parts) == 2 && strings.TrimSpace(parts[1]) != "" {
			return parts[1], nil
		}
	}

	if s.storageType == "local" && strings.TrimSpace(s.localDir) != "" {
		normalizedPath := filepath.ToSlash(filepath.Clean(path))
		normalizedRoot := filepath.ToSlash(filepath.Clean(s.localDir))
		prefix := normalizedRoot + "/"
		if strings.HasPrefix(normalizedPath, prefix) {
			candidate := strings.TrimPrefix(normalizedPath, prefix)
			if candidate == "" {
				return "", fmt.Errorf("storedPath inválido: %s", storedPath)
			}
			return candidate, nil
		}
	}

	if strings.HasPrefix(path, "/") {
		path = strings.TrimPrefix(path, "/")
	}
	if path == "" {
		return "", fmt.Errorf("storedPath inválido: %s", storedPath)
	}

	return path, nil
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
	if s.storageType == "local" {
		return "/uploads/" + objectName
	}
	return "https://storage.googleapis.com/" + s.bucketName + "/" + objectName
}

func (s *Service) storagePath(objectName string) string {
	if s.storageType == "local" {
		return filepath.ToSlash(filepath.Join(s.localDir, filepath.FromSlash(objectName)))
	}
	return "gs://" + s.bucketName + "/" + objectName
}
