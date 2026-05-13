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

func (s *Service) FindByCatequisandoID(ctx context.Context, catequisandoID int64) ([]FichaInscricao, error) {
	exists, err := s.repo.ExistsCatequisandoID(ctx, catequisandoID)
	if err != nil {
		return nil, err
	}
	if !exists {
		return nil, ErrCatequisandoNotFound
	}

	items, err := s.repo.FindByCatequisandoID(ctx, catequisandoID)
	if err != nil {
		return nil, err
	}
	result := make([]FichaInscricao, 0, len(items))
	for _, item := range items {
		result = append(result, toDTO(item))
	}
	return result, nil
}

func (s *Service) FindByIDAndCatequisandoID(ctx context.Context, id int64, catequisandoID int64) (FichaInscricao, error) {
	item, err := s.repo.FindByIDAndCatequisandoID(ctx, id, catequisandoID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return FichaInscricao{}, ErrNotFound
		}
		return FichaInscricao{}, err
	}
	return toDTO(item), nil
}

func (s *Service) CreateForCatequisando(ctx context.Context, catequisandoID int64, req FichaInscricaoRequest) (FichaInscricao, error) {
	exists, err := s.repo.ExistsCatequisandoID(ctx, catequisandoID)
	if err != nil {
		return FichaInscricao{}, err
	}
	if !exists {
		return FichaInscricao{}, ErrCatequisandoNotFound
	}

	req.CatequisandoID = &catequisandoID
	id, err := s.repo.Create(ctx, fichaDB{
		DataInscricao:  strings.TrimSpace(req.DataInscricao),
		Observacoes:    strings.TrimSpace(req.Observacoes),
		CatequisandoID: req.CatequisandoID,
	})
	if err != nil {
		return FichaInscricao{}, err
	}
	return s.FindByIDAndCatequisandoID(ctx, id, catequisandoID)
}

func (s *Service) UpdateForCatequisando(ctx context.Context, id int64, catequisandoID int64, req FichaInscricaoRequest) (FichaInscricao, error) {
	_, err := s.repo.FindByIDAndCatequisandoID(ctx, id, catequisandoID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return FichaInscricao{}, ErrNotFound
		}
		return FichaInscricao{}, err
	}

	exists, err := s.repo.ExistsCatequisandoID(ctx, catequisandoID)
	if err != nil {
		return FichaInscricao{}, err
	}
	if !exists {
		return FichaInscricao{}, ErrCatequisandoNotFound
	}

	cateqID := catequisandoID

	if err := s.repo.UpdateByIDAndCatequisandoID(ctx, id, catequisandoID, fichaDB{
		DataInscricao:  strings.TrimSpace(req.DataInscricao),
		Observacoes:    strings.TrimSpace(req.Observacoes),
		CatequisandoID: &cateqID,
	}); err != nil {
		return FichaInscricao{}, err
	}
	return s.FindByIDAndCatequisandoID(ctx, id, catequisandoID)
}

func (s *Service) DeleteByIDAndCatequisandoID(ctx context.Context, id int64, catequisandoID int64) error {
	_, err := s.FindByIDAndCatequisandoID(ctx, id, catequisandoID)
	if err != nil {
		return err
	}
	return s.repo.DeleteByIDAndCatequisandoID(ctx, id, catequisandoID)
}

func toDTO(item fichaDB) FichaInscricao {
	return FichaInscricao{
		IDFicha:        item.IDFicha,
		DataInscricao:  item.DataInscricao,
		Observacoes:    item.Observacoes,
		CatequisandoID: item.CatequisandoID,
	}
}
