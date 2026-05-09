package comunidade

import (
	"context"
	"database/sql"
	"errors"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) FindAll(ctx context.Context) ([]Comunidade, error) { return s.repo.FindAll(ctx) }

func (s *Service) FindByID(ctx context.Context, id int64) (Comunidade, error) {
	item, err := s.repo.FindByID(ctx, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return Comunidade{}, sql.ErrNoRows
		}
		return Comunidade{}, err
	}
	return item, nil
}

func (s *Service) Create(ctx context.Context, req Comunidade) (Comunidade, error) {
	id, err := s.repo.Create(ctx, req)
	if err != nil {
		return Comunidade{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req Comunidade) (Comunidade, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Comunidade{}, err
	}
	if !exists {
		return Comunidade{}, sql.ErrNoRows
	}
	if err := s.repo.Update(ctx, id, req); err != nil {
		return Comunidade{}, err
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
