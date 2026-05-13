package upload

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestUploadServiceStoreFile(t *testing.T) {
	svc, err := NewServiceWithStore("test-bucket", "", NewMemoryStore())
	if err != nil {
		t.Fatalf("new service failed: %v", err)
	}

	srcPath := filepath.Join(t.TempDir(), "origem.txt")
	if err := os.WriteFile(srcPath, []byte("conteudo de teste"), 0o644); err != nil {
		t.Fatalf("write source file: %v", err)
	}

	f, err := os.Open(srcPath)
	if err != nil {
		t.Fatalf("open source file: %v", err)
	}
	defer f.Close()

	saved, err := svc.Store(context.Background(), f, "origem.txt", "", "text/plain")
	if err != nil {
		t.Fatalf("store failed: %v", err)
	}
	if saved.FileName == "" {
		t.Fatal("expected generated filename")
	}
	if !strings.HasPrefix(saved.Path, "gs://test-bucket/") {
		t.Fatalf("expected gcs path, got %s", saved.Path)
	}
	if !strings.HasPrefix(saved.URL, "https://storage.googleapis.com/test-bucket/") {
		t.Fatalf("expected default public URL, got %s", saved.URL)
	}
}

func TestUploadServiceStoreFileWithTypeAndPublicURL(t *testing.T) {
	svc, err := NewServiceWithStore("test-bucket", "https://cdn.example.com", NewMemoryStore())
	if err != nil {
		t.Fatalf("new service failed: %v", err)
	}

	srcPath := filepath.Join(t.TempDir(), "foto.png")
	if err := os.WriteFile(srcPath, []byte("png fake"), 0o644); err != nil {
		t.Fatalf("write source file: %v", err)
	}

	f, err := os.Open(srcPath)
	if err != nil {
		t.Fatalf("open source file: %v", err)
	}
	defer f.Close()

	saved, err := svc.Store(context.Background(), f, "foto.png", "Documentos Gerais", "image/png")
	if err != nil {
		t.Fatalf("store failed: %v", err)
	}

	if !strings.Contains(saved.Path, "gs://test-bucket/documentos_gerais/") {
		t.Fatalf("expected typed path, got %s", saved.Path)
	}
	if !strings.HasPrefix(saved.URL, "https://cdn.example.com/") {
		t.Fatalf("expected public URL prefix, got %s", saved.URL)
	}
}

type failingStore struct {
	uploaded []string
}

func (f *failingStore) Upload(_ context.Context, objectName, _ string, _ []byte) error {
	if strings.Contains(objectName, "falha") {
		return os.ErrInvalid
	}
	f.uploaded = append(f.uploaded, objectName)
	return nil
}

func (f *failingStore) Delete(_ context.Context, objectName string) error {
	for i := 0; i < len(f.uploaded); i++ {
		if f.uploaded[i] == objectName {
			f.uploaded = append(f.uploaded[:i], f.uploaded[i+1:]...)
			break
		}
	}
	return nil
}

func TestUploadServiceStoreManyRollsBackOnFailure(t *testing.T) {
	fake := &failingStore{}
	svc, err := NewServiceWithStore("test-bucket", "", fake)
	if err != nil {
		t.Fatalf("new service failed: %v", err)
	}

	_, err = svc.StoreMany(context.Background(), []UploadPayload{
		{Data: []byte("ok"), OriginalName: "ok.png", FileType: "fotos", ContentType: "image/png"},
		{Data: []byte("x"), OriginalName: "falha.png", FileType: "fotos", ContentType: "image/png"},
	})
	if err == nil {
		t.Fatal("expected error in batch upload")
	}
	if len(fake.uploaded) != 0 {
		t.Fatalf("expected rollback to delete previously uploaded objects, found %d", len(fake.uploaded))
	}
}

func TestUploadServiceDeletePathFromGCSPath(t *testing.T) {
	mem := NewMemoryStore()
	svc, err := NewServiceWithStore("test-bucket", "", mem)
	if err != nil {
		t.Fatalf("new service failed: %v", err)
	}

	srcPath := filepath.Join(t.TempDir(), "doc.pdf")
	if err := os.WriteFile(srcPath, []byte("conteudo"), 0o644); err != nil {
		t.Fatalf("write source file: %v", err)
	}

	f, err := os.Open(srcPath)
	if err != nil {
		t.Fatalf("open source file: %v", err)
	}
	defer f.Close()

	saved, err := svc.Store(context.Background(), f, "doc.pdf", "RG", "application/pdf")
	if err != nil {
		t.Fatalf("store failed: %v", err)
	}

	if _, ok := mem.objects[saved.FileName]; !ok {
		t.Fatalf("expected object to exist before delete")
	}

	if err := svc.DeletePath(context.Background(), saved.Path); err != nil {
		t.Fatalf("delete path failed: %v", err)
	}

	if _, ok := mem.objects[saved.FileName]; ok {
		t.Fatalf("expected object to be deleted")
	}
}
