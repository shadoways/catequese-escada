package usuario

import (
	"context"
	"database/sql"
	"errors"
	"net/mail"
	"strings"

	"catequese-escada/go-api/internal/auth"
)

var validRoles = map[string]struct{}{
	"COORDENADOR_PAROQUIAL":  {},
	"COORDENADOR_COMUNIDADE": {},
	"CATEQUISTA":             {},
}

type Service struct {
	db   *sql.DB
	repo *Repository
}

func NewService(db *sql.DB, repo *Repository) *Service {
	return &Service{db: db, repo: repo}
}

func (s *Service) FindAll(ctx context.Context) ([]UsuarioDTO, error) {
	items, err := s.repo.FindAll(ctx)
	if err != nil {
		return nil, err
	}
	result := make([]UsuarioDTO, 0, len(items))
	for _, item := range items {
		roles, err := s.repo.ListRolesByUserID(ctx, item.IDUsuario)
		if err != nil {
			return nil, err
		}
		result = append(result, toDTO(item, roles))
	}
	return result, nil
}

func (s *Service) FindByID(ctx context.Context, id int64) (UsuarioDTO, error) {
	item, err := s.repo.FindByID(ctx, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return UsuarioDTO{}, ErrNotFound
		}
		return UsuarioDTO{}, err
	}
	roles, err := s.repo.ListRolesByUserID(ctx, item.IDUsuario)
	if err != nil {
		return UsuarioDTO{}, err
	}
	return toDTO(item, roles), nil
}

func (s *Service) FindByEmail(ctx context.Context, email string) (UsuarioDTO, error) {
	normalized := normalizeEmail(email)
	item, err := s.repo.FindByEmail(ctx, normalized)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return UsuarioDTO{}, ErrNotFound
		}
		return UsuarioDTO{}, err
	}
	roles, err := s.repo.ListRolesByUserID(ctx, item.IDUsuario)
	if err != nil {
		return UsuarioDTO{}, err
	}
	return toDTO(item, roles), nil
}

func (s *Service) Create(ctx context.Context, req CreateUsuarioRequest) (UsuarioDTO, error) {
	normalizedEmail := normalizeEmail(req.Email)
	if len(strings.TrimSpace(req.Password)) < 6 {
		return UsuarioDTO{}, ErrInvalidPassword
	}
	if _, err := mail.ParseAddress(normalizedEmail); err != nil {
		return UsuarioDTO{}, ErrEmailExists
	}

	exists, err := s.repo.ExistsByEmail(ctx, normalizedEmail)
	if err != nil {
		return UsuarioDTO{}, err
	}
	if exists {
		return UsuarioDTO{}, ErrEmailExists
	}

	roles, err := normalizeRoles(req.Roles)
	if err != nil {
		return UsuarioDTO{}, err
	}

	if err := s.validateRefs(ctx, req.IDComunidade, req.IDCatequista); err != nil {
		return UsuarioDTO{}, err
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		return UsuarioDTO{}, err
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return UsuarioDTO{}, err
	}
	defer tx.Rollback()

	id, err := s.repo.Insert(ctx, tx, usuarioDB{
		Nome:         strings.TrimSpace(req.Nome),
		Email:        normalizedEmail,
		PasswordHash: hash,
		Ativo:        true,
		IDComunidade: req.IDComunidade,
		IDCatequista: req.IDCatequista,
	})
	if err != nil {
		return UsuarioDTO{}, err
	}

	if err := s.repo.ReplaceRoles(ctx, tx, id, roles); err != nil {
		return UsuarioDTO{}, err
	}

	if err := tx.Commit(); err != nil {
		return UsuarioDTO{}, err
	}

	return s.FindByID(ctx, id)
}

func (s *Service) Update(ctx context.Context, id int64, req UpdateUsuarioRequest) (UsuarioDTO, error) {
	existing, err := s.repo.FindByID(ctx, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return UsuarioDTO{}, ErrNotFound
		}
		return UsuarioDTO{}, err
	}

	normalizedEmail := normalizeEmail(req.Email)
	if normalizedEmail != existing.Email {
		exists, err := s.repo.ExistsByEmail(ctx, normalizedEmail)
		if err != nil {
			return UsuarioDTO{}, err
		}
		if exists {
			return UsuarioDTO{}, ErrEmailExists
		}
	}

	if err := s.validateRefs(ctx, req.IDComunidade, req.IDCatequista); err != nil {
		return UsuarioDTO{}, err
	}

	roles, err := normalizeRolesAllowEmpty(req.Roles)
	if err != nil {
		return UsuarioDTO{}, err
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return UsuarioDTO{}, err
	}
	defer tx.Rollback()

	if err := s.repo.Update(ctx, tx, id, usuarioDB{
		Nome:         strings.TrimSpace(req.Nome),
		Email:        normalizedEmail,
		Ativo:        req.Ativo,
		IDComunidade: req.IDComunidade,
		IDCatequista: req.IDCatequista,
	}); err != nil {
		return UsuarioDTO{}, err
	}

	if len(roles) > 0 {
		if err := s.repo.ReplaceRoles(ctx, tx, id, roles); err != nil {
			return UsuarioDTO{}, err
		}
	}

	if err := tx.Commit(); err != nil {
		return UsuarioDTO{}, err
	}

	return s.FindByID(ctx, id)
}

func (s *Service) ToggleAtivo(ctx context.Context, id int64) (UsuarioDTO, error) {
	u, err := s.repo.FindByID(ctx, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return UsuarioDTO{}, ErrNotFound
		}
		return UsuarioDTO{}, err
	}
	if err := s.repo.ToggleAtivo(ctx, id, !u.Ativo); err != nil {
		return UsuarioDTO{}, err
	}
	return s.FindByID(ctx, id)
}

func (s *Service) Delete(ctx context.Context, id int64) error {
	exists, err := s.repo.ExistsByID(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return ErrNotFound
	}
	return s.repo.DeleteByID(ctx, id)
}

func (s *Service) validateRefs(ctx context.Context, idComunidade *int64, idCatequista *int64) error {
	if idComunidade != nil {
		exists, err := s.repo.ExistsComunidade(ctx, *idComunidade)
		if err != nil {
			return err
		}
		if !exists {
			return ErrComunidadeNotFound
		}
	}
	if idCatequista != nil {
		exists, err := s.repo.ExistsCatequista(ctx, *idCatequista)
		if err != nil {
			return err
		}
		if !exists {
			return ErrCatequistaNotFound
		}
	}
	return nil
}

func normalizeRoles(roles []string) ([]string, error) {
	if len(roles) == 0 {
		return nil, ErrInvalidRole
	}
	return normalizeRolesAllowEmpty(roles)
}

func normalizeRolesAllowEmpty(roles []string) ([]string, error) {
	if len(roles) == 0 {
		return nil, nil
	}
	out := make([]string, 0, len(roles))
	for _, role := range roles {
		r := strings.TrimSpace(role)
		if _, ok := validRoles[r]; !ok {
			return nil, ErrInvalidRole
		}
		out = append(out, r)
	}
	return out, nil
}

func normalizeEmail(email string) string {
	return strings.ToLower(strings.TrimSpace(email))
}

func toDTO(u usuarioDB, roles []string) UsuarioDTO {
	return UsuarioDTO{
		IDUsuario:    u.IDUsuario,
		Nome:         u.Nome,
		Email:        u.Email,
		Ativo:        u.Ativo,
		Roles:        roles,
		IDComunidade: u.IDComunidade,
		IDCatequista: u.IDCatequista,
	}
}
