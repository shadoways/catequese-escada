package evento

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

func (s *Service) FindAll(ctx context.Context) ([]Evento, error) { return s.repo.FindAll(ctx) }

func (s *Service) FindByID(ctx context.Context, id int64) (Evento, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Create(ctx context.Context, req Evento) (Evento, error) {
	id, err := s.repo.Create(ctx, req)
	if err != nil {
		return Evento{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req Evento) (Evento, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Evento{}, err
	}
	if !exists {
		return Evento{}, sql.ErrNoRows
	}
	if err := s.repo.Update(ctx, id, req); err != nil {
		return Evento{}, err
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
