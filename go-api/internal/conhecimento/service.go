package conhecimento

import (
	"context"
	"database/sql"
)

type Service struct{ repo *Repository }

func NewService(repo *Repository) *Service                             { return &Service{repo: repo} }
func (s *Service) FindAll(ctx context.Context) ([]Conhecimento, error) { return s.repo.FindAll(ctx) }
func (s *Service) FindByID(ctx context.Context, id int64) (Conhecimento, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Create(ctx context.Context, req Conhecimento) (Conhecimento, error) {
	if req.Catequista == nil {
		return Conhecimento{}, sql.ErrNoRows
	}
	exists, err := s.repo.ExistsCatequistaID(ctx, req.Catequista.IDCatequista)
	if err != nil {
		return Conhecimento{}, err
	}
	if !exists {
		return Conhecimento{}, sql.ErrNoRows
	}
	id, err := s.repo.Create(ctx, req)
	if err != nil {
		return Conhecimento{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req Conhecimento) (Conhecimento, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Conhecimento{}, err
	}
	if !exists {
		return Conhecimento{}, sql.ErrNoRows
	}
	if req.Catequista == nil {
		return Conhecimento{}, sql.ErrNoRows
	}
	cateqExists, err := s.repo.ExistsCatequistaID(ctx, req.Catequista.IDCatequista)
	if err != nil {
		return Conhecimento{}, err
	}
	if !cateqExists {
		return Conhecimento{}, sql.ErrNoRows
	}
	if err := s.repo.Update(ctx, id, req); err != nil {
		return Conhecimento{}, err
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
