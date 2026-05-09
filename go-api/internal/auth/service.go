package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"strings"
	"time"
)

type Service struct {
	db                     *sql.DB
	repo                   *Repository
	jwtService             *JWTService
	jwtExpirationMs        int64
	jwtRefreshExpirationMs int64
}

func NewService(db *sql.DB, repo *Repository, jwtService *JWTService, jwtExpiration, refreshExpiration time.Duration) *Service {
	return &Service{
		db:                     db,
		repo:                   repo,
		jwtService:             jwtService,
		jwtExpirationMs:        jwtExpiration.Milliseconds(),
		jwtRefreshExpirationMs: refreshExpiration.Milliseconds(),
	}
}

func (s *Service) Login(ctx context.Context, req LoginRequest) (LoginResponse, error) {
	email := strings.ToLower(strings.TrimSpace(req.Email))

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return LoginResponse{}, err
	}
	defer tx.Rollback()

	user, err := s.repo.FindUserByEmail(ctx, tx, email)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return LoginResponse{}, ErrInvalidCredentials
		}
		return LoginResponse{}, err
	}

	if !user.Ativo {
		return LoginResponse{}, ErrInvalidCredentials
	}

	if !ComparePassword(req.Password, strings.TrimSpace(user.PasswordHash)) {
		return LoginResponse{}, ErrInvalidCredentials
	}

	accessToken, err := s.jwtService.GenerateAccessToken(user.Email, user.Nome, user.Roles)
	if err != nil {
		return LoginResponse{}, err
	}

	rawRefreshToken, err := generateRefreshTokenRaw()
	if err != nil {
		return LoginResponse{}, err
	}

	now := time.Now().UTC()
	expiresAt := now.Add(time.Duration(s.jwtRefreshExpirationMs) * time.Millisecond)
	if err := s.repo.InsertRefreshToken(ctx, tx, user.ID, hashToken(rawRefreshToken), expiresAt); err != nil {
		return LoginResponse{}, err
	}

	if err := s.repo.UpdateUltimoLogin(ctx, tx, user.ID, now); err != nil {
		return LoginResponse{}, err
	}

	if err := tx.Commit(); err != nil {
		return LoginResponse{}, err
	}

	return LoginResponse{
		Token:            accessToken,
		Email:            user.Email,
		Nome:             user.Nome,
		Roles:            user.Roles,
		ExpiresIn:        s.jwtExpirationMs,
		RefreshToken:     rawRefreshToken,
		RefreshExpiresIn: s.jwtRefreshExpirationMs,
	}, nil
}

func (s *Service) Refresh(ctx context.Context, req RefreshTokenRequest) (LoginResponse, error) {
	tokenHash := hashToken(req.RefreshToken)
	now := time.Now().UTC()

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return LoginResponse{}, err
	}
	defer tx.Rollback()

	stored, err := s.repo.FindRefreshTokenByHash(ctx, tx, tokenHash)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return LoginResponse{}, ErrInvalidRefresh
		}
		return LoginResponse{}, err
	}

	expiresAt := time.Unix(stored.DataExpiracao, 0).UTC()
	if stored.Revogado || expiresAt.Before(now) {
		if !stored.Revogado {
			_ = s.repo.RevokeRefreshToken(ctx, tx, stored.ID, now)
			_ = tx.Commit()
		}
		return LoginResponse{}, ErrInvalidRefresh
	}

	user, err := s.repo.FindUserByID(ctx, tx, stored.UserID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return LoginResponse{}, ErrInvalidCredentials
		}
		return LoginResponse{}, err
	}

	if !user.Ativo {
		_ = s.repo.RevokeRefreshToken(ctx, tx, stored.ID, now)
		_ = tx.Commit()
		return LoginResponse{}, ErrInvalidCredentials
	}

	if err := s.repo.RevokeRefreshToken(ctx, tx, stored.ID, now); err != nil {
		return LoginResponse{}, err
	}

	accessToken, err := s.jwtService.GenerateAccessToken(user.Email, user.Nome, user.Roles)
	if err != nil {
		return LoginResponse{}, err
	}

	rawRefreshToken, err := generateRefreshTokenRaw()
	if err != nil {
		return LoginResponse{}, err
	}
	newExpiry := now.Add(time.Duration(s.jwtRefreshExpirationMs) * time.Millisecond)
	if err := s.repo.InsertRefreshToken(ctx, tx, user.ID, hashToken(rawRefreshToken), newExpiry); err != nil {
		return LoginResponse{}, err
	}

	if err := tx.Commit(); err != nil {
		return LoginResponse{}, err
	}

	return LoginResponse{
		Token:            accessToken,
		Email:            user.Email,
		Nome:             user.Nome,
		Roles:            user.Roles,
		ExpiresIn:        s.jwtExpirationMs,
		RefreshToken:     rawRefreshToken,
		RefreshExpiresIn: s.jwtRefreshExpirationMs,
	}, nil
}

func (s *Service) Logout(ctx context.Context, req RefreshTokenRequest) error {
	tokenHash := hashToken(req.RefreshToken)
	now := time.Now().UTC()

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	stored, err := s.repo.FindRefreshTokenByHash(ctx, tx, tokenHash)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return tx.Commit()
		}
		return err
	}

	if !stored.Revogado {
		if err := s.repo.RevokeRefreshToken(ctx, tx, stored.ID, now); err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (s *Service) RequestPasswordReset(ctx context.Context, req PasswordResetRequest) error {
	email := strings.ToLower(strings.TrimSpace(req.Email))
	if email == "" {
		return nil
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	user, err := s.repo.FindUserByEmail(ctx, tx, email)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return tx.Commit()
		}
		return err
	}

	if !user.Ativo {
		return tx.Commit()
	}

	if err := s.repo.MarkActivePasswordResetTokensUsedByUserID(ctx, tx, user.ID); err != nil {
		return err
	}

	resetToken, err := generateResetTokenRaw()
	if err != nil {
		return err
	}
	expiresAt := time.Now().UTC().Add(24 * time.Hour)
	if err := s.repo.InsertPasswordResetToken(ctx, tx, user.ID, resetToken, expiresAt); err != nil {
		return err
	}

	return tx.Commit()
}

func (s *Service) ResetPassword(ctx context.Context, req PasswordResetConfirmRequest) error {
	token := strings.TrimSpace(req.Token)
	if token == "" {
		return ErrInvalidResetToken
	}
	if len(strings.TrimSpace(req.NewPassword)) < 6 {
		return ErrWeakPassword
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()

	stored, err := s.repo.FindPasswordResetToken(ctx, tx, token)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ErrInvalidResetToken
		}
		return err
	}

	if stored.Usado {
		return ErrResetTokenUsed
	}
	if time.Unix(stored.DataExpiracao, 0).UTC().Before(time.Now().UTC()) {
		return ErrResetTokenExpired
	}

	passwordHash, err := HashPassword(req.NewPassword)
	if err != nil {
		return err
	}

	if err := s.repo.UpdateUserPasswordHashByID(ctx, tx, stored.UserID, passwordHash); err != nil {
		return err
	}
	if err := s.repo.MarkPasswordResetTokenUsedByID(ctx, tx, stored.ID); err != nil {
		return err
	}
	if err := s.repo.MarkOtherPasswordResetTokensUsedByUserID(ctx, tx, stored.UserID, stored.ID); err != nil {
		return err
	}

	return tx.Commit()
}

func hashToken(value string) string {
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func generateRefreshTokenRaw() (string, error) {
	buf := make([]byte, 32)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}

func generateResetTokenRaw() (string, error) {
	buf := make([]byte, 24)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return hex.EncodeToString(buf), nil
}
