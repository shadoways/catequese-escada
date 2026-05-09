package presenca

import (
	"context"
	"database/sql"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) FindAll(ctx context.Context) ([]Presenca, error) { return s.repo.FindAll(ctx) }

func (s *Service) FindByID(ctx context.Context, id int64) (Presenca, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Create(ctx context.Context, req Presenca) (Presenca, error) {
	if req.Catequisando == nil {
		return Presenca{}, sql.ErrNoRows
	}
	exists, err := s.repo.ExistsCatequisandoID(ctx, req.Catequisando.IDCatequisando)
	if err != nil {
		return Presenca{}, err
	}
	if !exists {
		return Presenca{}, sql.ErrNoRows
	}
	id, err := s.repo.Create(ctx, req)
	if err != nil {
		return Presenca{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req Presenca) (Presenca, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Presenca{}, err
	}
	if !exists {
		return Presenca{}, sql.ErrNoRows
	}
	if req.Catequisando == nil {
		return Presenca{}, sql.ErrNoRows
	}
	cateqExists, err := s.repo.ExistsCatequisandoID(ctx, req.Catequisando.IDCatequisando)
	if err != nil {
		return Presenca{}, err
	}
	if !cateqExists {
		return Presenca{}, sql.ErrNoRows
	}
	if err := s.repo.Update(ctx, id, req); err != nil {
		return Presenca{}, err
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
