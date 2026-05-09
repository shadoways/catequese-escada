package coordenador

import (
	"context"
	"database/sql"
)

type Service struct{ repo *Repository }

func NewService(repo *Repository) *Service                            { return &Service{repo: repo} }
func (s *Service) FindAll(ctx context.Context) ([]Coordenador, error) { return s.repo.FindAll(ctx) }
func (s *Service) FindByID(ctx context.Context, id int64) (Coordenador, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Create(ctx context.Context, req Coordenador) (Coordenador, error) {
	id, err := s.repo.Create(ctx, req)
	if err != nil {
		return Coordenador{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req Coordenador) (Coordenador, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Coordenador{}, err
	}
	if !exists {
		return Coordenador{}, sql.ErrNoRows
	}
	if err := s.repo.Update(ctx, id, req); err != nil {
		return Coordenador{}, err
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
