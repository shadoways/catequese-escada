package catequisando

import (
	"context"
	"errors"
	"log"
	"strings"

	"catequese-escada/go-api/internal/documento"
)

var ErrDocumentoCivilDuplicado = errors.New("documento civil already linked to another catequisando")

type baseRepository interface {
	FindAll(ctx context.Context) ([]Catequisando, error)
	FindByID(ctx context.Context, id int64) (Catequisando, error)
	Create(ctx context.Context, c Catequisando) (int64, error)
	Update(ctx context.Context, id int64, c Catequisando) error
	Delete(ctx context.Context, id int64) error
	ExistsDocumentoCivilEmOutroCatequisando(ctx context.Context, numeroDocumento, tipoDocumento string, excludeID int64) (bool, error)
}

type documentoProvider interface {
	FindByCatequisandoIDs(ctx context.Context, ids []int64) (map[int64][]documento.Documento, error)
}

type Service struct {
	repo       baseRepository
	documentos documentoProvider
}

func NewService(repo baseRepository, documentos documentoProvider) *Service {
	return &Service{repo: repo, documentos: documentos}
}

func (s *Service) FindAll(ctx context.Context) ([]Catequisando, error) {
	items, err := s.repo.FindAll(ctx)
	if err != nil {
		return nil, err
	}
	if err := s.attachArquivos(ctx, items); err != nil {
		log.Printf("warn: could not enrich catequisandos with arquivos: %v", err)
		for i := range items {
			items[i].Arquivos = EmptyArquivosResumo()
			items[i].Arquivos.Parcial = true
		}
	}
	return items, nil
}

func (s *Service) FindByID(ctx context.Context, id int64) (Catequisando, error) {
	item, err := s.repo.FindByID(ctx, id)
	if err != nil {
		return Catequisando{}, err
	}
	tmp := []Catequisando{item}
	if err := s.attachArquivos(ctx, tmp); err != nil {
		log.Printf("warn: could not enrich catequisando id=%d with arquivos: %v", id, err)
		tmp[0].Arquivos = EmptyArquivosResumo()
		tmp[0].Arquivos.Parcial = true
	}
	return tmp[0], nil
}

func (s *Service) Create(ctx context.Context, c Catequisando) (int64, error) {
	if err := s.validateDocumentoCivilUnico(ctx, c, 0); err != nil {
		return 0, err
	}
	return s.repo.Create(ctx, c)
}

func (s *Service) Update(ctx context.Context, id int64, c Catequisando) error {
	if err := s.validateDocumentoCivilUnico(ctx, c, id); err != nil {
		return err
	}
	return s.repo.Update(ctx, id, c)
}

func (s *Service) Delete(ctx context.Context, id int64) error {
	return s.repo.Delete(ctx, id)
}

func (s *Service) attachArquivos(ctx context.Context, items []Catequisando) error {
	for i := range items {
		items[i].Arquivos = EmptyArquivosResumo()
	}
	if len(items) == 0 || s.documentos == nil {
		return nil
	}

	ids := make([]int64, 0, len(items))
	for _, item := range items {
		ids = append(ids, item.IDCatequisando)
	}

	docsByCateq, err := s.documentos.FindByCatequisandoIDs(ctx, ids)
	if err != nil {
		return err
	}

	for i := range items {
		docs := docsByCateq[items[i].IDCatequisando]
		converted := make([]DocumentoArquivo, 0, len(docs))
		for _, d := range docs {
			converted = append(converted, DocumentoArquivo{
				IDDocumento:    d.IDDocumento,
				TipoDocumento:  d.TipoDocumento,
				CaminhoArquivo: d.CaminhoArquivo,
				DataEnvio:      d.DataEnvio,
				TipoStatus:     d.TipoStatus,
			})
		}
		items[i].Arquivos = ArquivosResumo{Itens: converted, Total: len(converted)}
	}

	return nil
}

func (s *Service) validateDocumentoCivilUnico(ctx context.Context, c Catequisando, excludeID int64) error {
	numero := strings.TrimSpace(c.NumeroDocumento)
	tipo := strings.TrimSpace(c.TipoDocumento)
	if numero == "" || tipo == "" {
		return nil
	}

	exists, err := s.repo.ExistsDocumentoCivilEmOutroCatequisando(ctx, numero, tipo, excludeID)
	if err != nil {
		return err
	}
	if exists {
		return ErrDocumentoCivilDuplicado
	}

	return nil
}
