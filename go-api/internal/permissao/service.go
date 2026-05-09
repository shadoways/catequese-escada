package permissao

import (
	"context"
	"database/sql"
)

type Service struct{ repo *Repository }

func NewService(repo *Repository) *Service                          { return &Service{repo: repo} }
func (s *Service) FindAll(ctx context.Context) ([]Permissao, error) { return s.repo.FindAll(ctx) }
func (s *Service) FindByID(ctx context.Context, id int64) (Permissao, error) {
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Create(ctx context.Context, req Permissao) (Permissao, error) {
	if req.Login == nil {
		return Permissao{}, sql.ErrNoRows
	}
	exists, err := s.repo.ExistsLoginID(ctx, req.Login.IDLogin)
	if err != nil {
		return Permissao{}, err
	}
	if !exists {
		return Permissao{}, sql.ErrNoRows
	}
	id, err := s.repo.Create(ctx, req)
	if err != nil {
		return Permissao{}, err
	}
	return s.repo.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req Permissao) (Permissao, error) {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return Permissao{}, err
	}
	if !exists {
		return Permissao{}, sql.ErrNoRows
	}
	if req.Login == nil {
		return Permissao{}, sql.ErrNoRows
	}
	loginExists, err := s.repo.ExistsLoginID(ctx, req.Login.IDLogin)
	if err != nil {
		return Permissao{}, err
	}
	if !loginExists {
		return Permissao{}, sql.ErrNoRows
	}
	if err := s.repo.Update(ctx, id, req); err != nil {
		return Permissao{}, err
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
