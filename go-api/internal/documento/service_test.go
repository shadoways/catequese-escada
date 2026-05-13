package documento

import (
	"context"
	"database/sql"
	"errors"
	"testing"
)

type fakeRepo struct {
	findAllFn                       func(ctx context.Context) ([]Documento, error)
	findByIDFn                      func(ctx context.Context, id int64) (Documento, error)
	findByCatequisandoIDsFn         func(ctx context.Context, ids []int64) (map[int64][]Documento, error)
	existsByIDFn                    func(ctx context.Context, id int64) (bool, error)
	existsCatequisandoIDFn          func(ctx context.Context, id int64) (bool, error)
	findCatequisandoByDocumentoIDFn func(ctx context.Context, id int64) (int64, error)
	createFn                        func(ctx context.Context, d Documento) (int64, error)
	updateFn                        func(ctx context.Context, id int64, d Documento) error
	updateStatusFn                  func(ctx context.Context, id int64, status string) error
	deleteByIDFn                    func(ctx context.Context, id int64) error
}

func (f *fakeRepo) FindAll(ctx context.Context) ([]Documento, error) {
	if f.findAllFn == nil {
		return nil, nil
	}
	return f.findAllFn(ctx)
}

func (f *fakeRepo) FindByID(ctx context.Context, id int64) (Documento, error) {
	if f.findByIDFn == nil {
		return Documento{}, sql.ErrNoRows
	}
	return f.findByIDFn(ctx, id)
}

func (f *fakeRepo) FindByCatequisandoIDs(ctx context.Context, ids []int64) (map[int64][]Documento, error) {
	if f.findByCatequisandoIDsFn == nil {
		return map[int64][]Documento{}, nil
	}
	return f.findByCatequisandoIDsFn(ctx, ids)
}

func (f *fakeRepo) ExistsByID(ctx context.Context, id int64) (bool, error) {
	if f.existsByIDFn == nil {
		return false, nil
	}
	return f.existsByIDFn(ctx, id)
}

func (f *fakeRepo) ExistsCatequisandoID(ctx context.Context, id int64) (bool, error) {
	if f.existsCatequisandoIDFn == nil {
		return false, nil
	}
	return f.existsCatequisandoIDFn(ctx, id)
}

func (f *fakeRepo) FindCatequisandoIDByDocumentoID(ctx context.Context, id int64) (int64, error) {
	if f.findCatequisandoByDocumentoIDFn == nil {
		return 0, sql.ErrNoRows
	}
	return f.findCatequisandoByDocumentoIDFn(ctx, id)
}

func (f *fakeRepo) Create(ctx context.Context, d Documento) (int64, error) {
	if f.createFn == nil {
		return 0, nil
	}
	return f.createFn(ctx, d)
}

func (f *fakeRepo) Update(ctx context.Context, id int64, d Documento) error {
	if f.updateFn == nil {
		return nil
	}
	return f.updateFn(ctx, id, d)
}

func (f *fakeRepo) UpdateStatus(ctx context.Context, id int64, status string) error {
	if f.updateStatusFn == nil {
		return nil
	}
	return f.updateStatusFn(ctx, id, status)
}

func (f *fakeRepo) DeleteByID(ctx context.Context, id int64) error {
	if f.deleteByIDFn == nil {
		return nil
	}
	return f.deleteByIDFn(ctx, id)
}

func TestServiceUpdateRejectsOwnerChange(t *testing.T) {
	updated := false
	repo := &fakeRepo{
		existsByIDFn:                    func(ctx context.Context, id int64) (bool, error) { return true, nil },
		existsCatequisandoIDFn:          func(ctx context.Context, id int64) (bool, error) { return true, nil },
		findCatequisandoByDocumentoIDFn: func(ctx context.Context, id int64) (int64, error) { return 10, nil },
		updateFn: func(ctx context.Context, id int64, d Documento) error {
			updated = true
			return nil
		},
	}

	svc := NewService(repo)
	_, err := svc.Update(context.Background(), 99, Documento{Catequisando: &CatequisandoRef{IDCatequisando: 11}})
	if !errors.Is(err, ErrOwnerChangeNotAllowed) {
		t.Fatalf("expected ErrOwnerChangeNotAllowed, got %v", err)
	}
	if updated {
		t.Fatalf("expected update not to be called when owner changes")
	}
}

func TestServiceUpdateAllowsSameOwner(t *testing.T) {
	updated := false
	repo := &fakeRepo{
		existsByIDFn:                    func(ctx context.Context, id int64) (bool, error) { return true, nil },
		existsCatequisandoIDFn:          func(ctx context.Context, id int64) (bool, error) { return true, nil },
		findCatequisandoByDocumentoIDFn: func(ctx context.Context, id int64) (int64, error) { return 10, nil },
		updateFn: func(ctx context.Context, id int64, d Documento) error {
			updated = true
			return nil
		},
		findByIDFn: func(ctx context.Context, id int64) (Documento, error) {
			return Documento{IDDocumento: id, Catequisando: &CatequisandoRef{IDCatequisando: 10}}, nil
		},
	}

	svc := NewService(repo)
	out, err := svc.Update(context.Background(), 99, Documento{Catequisando: &CatequisandoRef{IDCatequisando: 10}})
	if err != nil {
		t.Fatalf("expected nil error, got %v", err)
	}
	if !updated {
		t.Fatalf("expected update to be called")
	}
	if out.Catequisando == nil || out.Catequisando.IDCatequisando != 10 {
		t.Fatalf("unexpected output: %+v", out)
	}
}
