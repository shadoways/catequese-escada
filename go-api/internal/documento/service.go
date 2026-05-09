package documento

import (
	"context"
	"database/sql"
	"strings"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) FindAll(ctx context.Context) ([]Documento, error) { return s.repo.FindAll(ctx) }

func (s *Service) FindByID(ctx context.Context, id int64) (Documento, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Create(ctx context.Context, req Documento) (Documento, error) {
	if req.Catequisando == nil {
		return Documento{}, sql.ErrNoRows
	}
	exists, err := s.repo.ExistsCatequisandoID(ctx, req.Catequisando.IDCatequisando)
	if err != nil {
		return Documento{}, err
	}
	if !exists {
		return Documento{}, sql.ErrNoRows
	}
	if strings.TrimSpace(req.TipoStatus) == "" {
		req.TipoStatus = "PENDENTE"
	}
	id, err := s.repo.Create(ctx, req)
	if err != nil {
		return Documento{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req Documento) (Documento, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Documento{}, err
	}
	if !exists {
		return Documento{}, sql.ErrNoRows
	}
	if req.Catequisando == nil {
		return Documento{}, sql.ErrNoRows
	}
	cateqExists, err := s.repo.ExistsCatequisandoID(ctx, req.Catequisando.IDCatequisando)
	if err != nil {
		return Documento{}, err
	}
	if !cateqExists {
		return Documento{}, sql.ErrNoRows
	}
	if strings.TrimSpace(req.TipoStatus) == "" {
		req.TipoStatus = "PENDENTE"
	}
	if err := s.repo.Update(ctx, id, req); err != nil {
		return Documento{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) UpdateStatus(ctx context.Context, id int64, status string) (Documento, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Documento{}, err
	}
	if !exists {
		return Documento{}, sql.ErrNoRows
	}
	if strings.TrimSpace(status) == "" {
		status = "PENDENTE"
	}
	if err := s.repo.UpdateStatus(ctx, id, status); err != nil {
		return Documento{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Delete(ctx context.Context, id int64) error {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return sql.ErrNoRows
	}
	return s.repo.DeleteByID(ctx, id)
}
