package catequisando

import (
	"context"
	"errors"
	"testing"

	"catequese-escada/go-api/internal/documento"
)

type fakeBaseRepository struct {
	findAllFn   func(ctx context.Context) ([]Catequisando, error)
	findByIDFn  func(ctx context.Context, id int64) (Catequisando, error)
	createFn    func(ctx context.Context, c Catequisando) (int64, error)
	updateFn    func(ctx context.Context, id int64, c Catequisando) error
	deleteFn    func(ctx context.Context, id int64) error
	existsDocFn func(ctx context.Context, numeroDocumento, tipoDocumento string, excludeID int64) (bool, error)
}

func (f *fakeBaseRepository) FindAll(ctx context.Context) ([]Catequisando, error) {
	return f.findAllFn(ctx)
}

func (f *fakeBaseRepository) FindByID(ctx context.Context, id int64) (Catequisando, error) {
	return f.findByIDFn(ctx, id)
}

func (f *fakeBaseRepository) Create(ctx context.Context, c Catequisando) (int64, error) {
	return f.createFn(ctx, c)
}

func (f *fakeBaseRepository) Update(ctx context.Context, id int64, c Catequisando) error {
	return f.updateFn(ctx, id, c)
}

func (f *fakeBaseRepository) Delete(ctx context.Context, id int64) error {
	return f.deleteFn(ctx, id)
}

func (f *fakeBaseRepository) ExistsDocumentoCivilEmOutroCatequisando(ctx context.Context, numeroDocumento, tipoDocumento string, excludeID int64) (bool, error) {
	if f.existsDocFn == nil {
		return false, nil
	}
	return f.existsDocFn(ctx, numeroDocumento, tipoDocumento, excludeID)
}

type fakeDocumentoProvider struct {
	findByCatequisandoIDsFn func(ctx context.Context, ids []int64) (map[int64][]documento.Documento, error)
}

func TestServiceCreateRejectsDuplicateDocumentoCivil(t *testing.T) {
	repo := &fakeBaseRepository{
		findAllFn:  func(ctx context.Context) ([]Catequisando, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (Catequisando, error) { return Catequisando{}, nil },
		createFn:   func(ctx context.Context, c Catequisando) (int64, error) { return 1, nil },
		updateFn:   func(ctx context.Context, id int64, c Catequisando) error { return nil },
		deleteFn:   func(ctx context.Context, id int64) error { return nil },
		existsDocFn: func(ctx context.Context, numeroDocumento, tipoDocumento string, excludeID int64) (bool, error) {
			if numeroDocumento != "1234567" || tipoDocumento != "RG" || excludeID != 0 {
				t.Fatalf("unexpected args numero=%s tipo=%s excludeID=%d", numeroDocumento, tipoDocumento, excludeID)
			}
			return true, nil
		},
	}

	svc := NewService(repo, nil)
	_, err := svc.Create(context.Background(), Catequisando{Nome: "A", NumeroDocumento: "1234567", TipoDocumento: "RG"})
	if !errors.Is(err, ErrDocumentoCivilDuplicado) {
		t.Fatalf("expected ErrDocumentoCivilDuplicado, got %v", err)
	}
}

func TestServiceUpdateRejectsDuplicateDocumentoCivil(t *testing.T) {
	repo := &fakeBaseRepository{
		findAllFn:  func(ctx context.Context) ([]Catequisando, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (Catequisando, error) { return Catequisando{}, nil },
		createFn:   func(ctx context.Context, c Catequisando) (int64, error) { return 1, nil },
		updateFn:   func(ctx context.Context, id int64, c Catequisando) error { return nil },
		deleteFn:   func(ctx context.Context, id int64) error { return nil },
		existsDocFn: func(ctx context.Context, numeroDocumento, tipoDocumento string, excludeID int64) (bool, error) {
			if excludeID != 42 {
				t.Fatalf("expected excludeID=42, got %d", excludeID)
			}
			return true, nil
		},
	}

	svc := NewService(repo, nil)
	err := svc.Update(context.Background(), 42, Catequisando{Nome: "A", NumeroDocumento: "1234567", TipoDocumento: "RG"})
	if !errors.Is(err, ErrDocumentoCivilDuplicado) {
		t.Fatalf("expected ErrDocumentoCivilDuplicado, got %v", err)
	}
}

func (f *fakeDocumentoProvider) FindByCatequisandoIDs(ctx context.Context, ids []int64) (map[int64][]documento.Documento, error) {
	return f.findByCatequisandoIDsFn(ctx, ids)
}

func TestServiceFindAllEnrichesArquivos(t *testing.T) {
	repo := &fakeBaseRepository{
		findAllFn: func(ctx context.Context) ([]Catequisando, error) {
			return []Catequisando{
				{IDCatequisando: 1, Nome: "A"},
				{IDCatequisando: 2, Nome: "B"},
			}, nil
		},
		findByIDFn: func(ctx context.Context, id int64) (Catequisando, error) { return Catequisando{}, nil },
		createFn:   func(ctx context.Context, c Catequisando) (int64, error) { return 0, nil },
		updateFn:   func(ctx context.Context, id int64, c Catequisando) error { return nil },
		deleteFn:   func(ctx context.Context, id int64) error { return nil },
	}

	docs := &fakeDocumentoProvider{
		findByCatequisandoIDsFn: func(ctx context.Context, ids []int64) (map[int64][]documento.Documento, error) {
			if len(ids) != 2 || ids[0] != 1 || ids[1] != 2 {
				t.Fatalf("unexpected ids: %v", ids)
			}
			return map[int64][]documento.Documento{
				1: {
					{IDDocumento: 10, TipoDocumento: "RG", CaminhoArquivo: "uploads/rg.pdf", DataEnvio: "2026-05-12", TipoStatus: "PENDENTE"},
				},
			}, nil
		},
	}

	svc := NewService(repo, docs)
	items, err := svc.FindAll(context.Background())
	if err != nil {
		t.Fatalf("expected nil error, got %v", err)
	}
	if len(items) != 2 {
		t.Fatalf("expected 2 catequisandos, got %d", len(items))
	}

	if items[0].Arquivos.Total != 1 {
		t.Fatalf("expected total=1 for first catequisando, got %d", items[0].Arquivos.Total)
	}
	if len(items[0].Arquivos.Itens) != 1 || items[0].Arquivos.Itens[0].IDDocumento != 10 {
		t.Fatalf("unexpected arquivos payload for first catequisando: %+v", items[0].Arquivos)
	}
	if items[0].Arquivos.Parcial {
		t.Fatalf("expected parcial=false when enrichment succeeds")
	}

	if items[1].Arquivos.Total != 0 || len(items[1].Arquivos.Itens) != 0 {
		t.Fatalf("expected empty arquivos for second catequisando, got %+v", items[1].Arquivos)
	}
}

func TestServiceFindAllMarksParcialOnDocumentoError(t *testing.T) {
	repo := &fakeBaseRepository{
		findAllFn: func(ctx context.Context) ([]Catequisando, error) {
			return []Catequisando{{IDCatequisando: 1, Nome: "A"}}, nil
		},
		findByIDFn: func(ctx context.Context, id int64) (Catequisando, error) { return Catequisando{}, nil },
		createFn:   func(ctx context.Context, c Catequisando) (int64, error) { return 0, nil },
		updateFn:   func(ctx context.Context, id int64, c Catequisando) error { return nil },
		deleteFn:   func(ctx context.Context, id int64) error { return nil },
	}

	docs := &fakeDocumentoProvider{
		findByCatequisandoIDsFn: func(ctx context.Context, ids []int64) (map[int64][]documento.Documento, error) {
			return nil, errors.New("boom")
		},
	}

	svc := NewService(repo, docs)
	items, err := svc.FindAll(context.Background())
	if err != nil {
		t.Fatalf("expected nil error, got %v", err)
	}
	if len(items) != 1 {
		t.Fatalf("expected 1 item, got %d", len(items))
	}
	if !items[0].Arquivos.Parcial {
		t.Fatalf("expected parcial=true when documento enrichment fails")
	}
	if items[0].Arquivos.Total != 0 || len(items[0].Arquivos.Itens) != 0 {
		t.Fatalf("expected empty arquivos on partial response, got %+v", items[0].Arquivos)
	}
}

func TestServiceFindByIDMarksParcialOnDocumentoError(t *testing.T) {
	repo := &fakeBaseRepository{
		findAllFn: func(ctx context.Context) ([]Catequisando, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (Catequisando, error) {
			return Catequisando{IDCatequisando: id, Nome: "A"}, nil
		},
		createFn: func(ctx context.Context, c Catequisando) (int64, error) { return 0, nil },
		updateFn: func(ctx context.Context, id int64, c Catequisando) error { return nil },
		deleteFn: func(ctx context.Context, id int64) error { return nil },
	}

	docs := &fakeDocumentoProvider{
		findByCatequisandoIDsFn: func(ctx context.Context, ids []int64) (map[int64][]documento.Documento, error) {
			return nil, errors.New("boom")
		},
	}

	svc := NewService(repo, docs)
	item, err := svc.FindByID(context.Background(), 99)
	if err != nil {
		t.Fatalf("expected nil error, got %v", err)
	}
	if item.IDCatequisando != 99 {
		t.Fatalf("expected ID 99, got %d", item.IDCatequisando)
	}
	if !item.Arquivos.Parcial {
		t.Fatalf("expected parcial=true when enrichment fails")
	}
}

func TestServiceCreateUpdateDeleteDelegateToRepository(t *testing.T) {
	created := false
	updated := false
	deleted := false

	repo := &fakeBaseRepository{
		findAllFn:  func(ctx context.Context) ([]Catequisando, error) { return nil, nil },
		findByIDFn: func(ctx context.Context, id int64) (Catequisando, error) { return Catequisando{}, nil },
		createFn: func(ctx context.Context, c Catequisando) (int64, error) {
			created = true
			return 123, nil
		},
		updateFn: func(ctx context.Context, id int64, c Catequisando) error {
			updated = true
			return nil
		},
		deleteFn: func(ctx context.Context, id int64) error {
			deleted = true
			return nil
		},
	}

	svc := NewService(repo, nil)

	id, err := svc.Create(context.Background(), Catequisando{Nome: "Novo"})
	if err != nil {
		t.Fatalf("create returned error: %v", err)
	}
	if id != 123 || !created {
		t.Fatalf("expected delegated create, got id=%d created=%v", id, created)
	}

	if err := svc.Update(context.Background(), 123, Catequisando{Nome: "Atualizado"}); err != nil {
		t.Fatalf("update returned error: %v", err)
	}
	if !updated {
		t.Fatalf("expected delegated update")
	}

	if err := svc.Delete(context.Background(), 123); err != nil {
		t.Fatalf("delete returned error: %v", err)
	}
	if !deleted {
		t.Fatalf("expected delegated delete")
	}
}
