package ficha

import (
	"context"
	"database/sql"
	"errors"
	"strings"
)

type Service struct {
	repo *Repository
}

func NewService(repo *Repository) *Service {
	return &Service{repo: repo}
}

func (s *Service) FindAll(ctx context.Context) ([]FichaInscricao, error) {
	items, err := s.repo.FindAll(ctx)
	if err != nil {
		return nil, err
	}
	result := make([]FichaInscricao, 0, len(items))
	for _, item := range items {
		result = append(result, toDTO(item))
	}
	return result, nil
}

func (s *Service) FindByID(ctx context.Context, id int64) (FichaInscricao, error) {
	item, err := s.repo.FindByID(ctx, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return FichaInscricao{}, ErrNotFound
		}
		return FichaInscricao{}, err
	}
	return toDTO(item), nil
}

func (s *Service) Create(ctx context.Context, req FichaInscricaoRequest) (FichaInscricao, error) {
	if req.CatequisandoID != nil {
		exists, err := s.repo.ExistsCatequisandoID(ctx, *req.CatequisandoID)
		if err != nil {
			return FichaInscricao{}, err
		}
		if !exists {
			return FichaInscricao{}, ErrCatequisandoNotFound
		}
	}
	id, err := s.repo.Create(ctx, fichaDB{
		DataInscricao:  strings.TrimSpace(req.DataInscricao),
		Observacoes:    strings.TrimSpace(req.Observacoes),
		CatequisandoID: req.CatequisandoID,
	})
	if err != nil {
		return FichaInscricao{}, err
	}
	return s.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req FichaInscricaoRequest) (FichaInscricao, error) {
	existing, err := s.repo.FindByID(ctx, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return FichaInscricao{}, ErrNotFound
		}
		return FichaInscricao{}, err
	}

	cateqID := req.CatequisandoID
	if cateqID == nil {
		cateqID = existing.CatequisandoID
	}
	if cateqID != nil {
		exists, err := s.repo.ExistsCatequisandoID(ctx, *cateqID)
		if err != nil {
			return FichaInscricao{}, err
		}
		if !exists {
			return FichaInscricao{}, ErrCatequisandoNotFound
		}
	}

	if err := s.repo.Update(ctx, id, fichaDB{
		DataInscricao:  strings.TrimSpace(req.DataInscricao),
		Observacoes:    strings.TrimSpace(req.Observacoes),
		CatequisandoID: cateqID,
	}); err != nil {
		return FichaInscricao{}, err
	}
	return s.FindByID(ctx, id)
}

func (s *Service) DeleteByID(ctx context.Context, id int64) error {
	_, err := s.FindByID(ctx, id)
	if err != nil {
		return err
	}
	return s.repo.DeleteByID(ctx, id)
}

func (s *Service) DeleteByCatequisandoID(ctx context.Context, catequisandoID int64) error {
	exists, err := s.repo.ExistsCatequisandoID(ctx, catequisandoID)
	if err != nil {
		return err
	}
	if !exists {
		return ErrCatequisandoNotFound
	}
	return s.repo.DeleteByCatequisandoID(ctx, catequisandoID)
}

func toDTO(item fichaDB) FichaInscricao {
	return FichaInscricao{
		IDFicha:        item.IDFicha,
		DataInscricao:  item.DataInscricao,
		Observacoes:    item.Observacoes,
		CatequisandoID: item.CatequisandoID,
	}
}
