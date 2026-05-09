package auth

import (
	"context"
	"database/sql"
	"fmt"
	"strconv"
	"time"
)

type dbtx interface {
	ExecContext(context.Context, string, ...any) (sql.Result, error)
	QueryContext(context.Context, string, ...any) (*sql.Rows, error)
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

type Repository struct{}

func NewRepository() *Repository {
	return &Repository{}
}

func (r *Repository) FindUserByEmail(ctx context.Context, q dbtx, email string) (User, error) {
	const query = `
SELECT id_usuario, nome, email, password_hash, ativo
FROM tb_usuario
WHERE email = ?
LIMIT 1`

	var u User
	err := q.QueryRowContext(ctx, query, email).Scan(&u.ID, &u.Nome, &u.Email, &u.PasswordHash, &u.Ativo)
	if err != nil {
		return User{}, err
	}

	roles, err := r.ListRolesByUserID(ctx, q, u.ID)
	if err != nil {
		return User{}, err
	}
	u.Roles = roles
	return u, nil
}

func (r *Repository) FindUserByID(ctx context.Context, q dbtx, id int64) (User, error) {
	const query = `
SELECT id_usuario, nome, email, password_hash, ativo
FROM tb_usuario
WHERE id_usuario = ?
LIMIT 1`

	var u User
	err := q.QueryRowContext(ctx, query, id).Scan(&u.ID, &u.Nome, &u.Email, &u.PasswordHash, &u.Ativo)
	if err != nil {
		return User{}, err
	}

	roles, err := r.ListRolesByUserID(ctx, q, u.ID)
	if err != nil {
		return User{}, err
	}
	u.Roles = roles
	return u, nil
}

func (r *Repository) ListRolesByUserID(ctx context.Context, q dbtx, userID int64) ([]string, error) {
	const query = `
SELECT role
FROM tb_usuario_role
WHERE id_usuario = ?
ORDER BY id_usuario_role ASC`

	rows, err := q.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	roles := make([]string, 0, 2)
	for rows.Next() {
		var role string
		if err := rows.Scan(&role); err != nil {
			return nil, err
		}
		roles = append(roles, role)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	return roles, nil
}

func (r *Repository) UpdateUltimoLogin(ctx context.Context, q dbtx, userID int64, now time.Time) error {
	const query = `UPDATE tb_usuario SET ultimo_login = ? WHERE id_usuario = ?`
	_, err := q.ExecContext(ctx, query, now, userID)
	return err
}

func (r *Repository) InsertRefreshToken(ctx context.Context, q dbtx, userID int64, tokenHash string, expiresAt time.Time) error {
	const query = `
INSERT INTO tb_refresh_token (id_usuario, token_hash, data_expiracao, revogado)
VALUES (?, ?, ?, FALSE)`
	_, err := q.ExecContext(ctx, query, userID, tokenHash, expiresAt)
	return err
}

func (r *Repository) FindRefreshTokenByHash(ctx context.Context, q dbtx, tokenHash string) (StoredRefreshToken, error) {
	const query = `
SELECT id_refresh_token, id_usuario, token_hash, revogado, data_expiracao
FROM tb_refresh_token
WHERE token_hash = ?
LIMIT 1`

	var rt StoredRefreshToken
	var rawExpiry any
	err := q.QueryRowContext(ctx, query, tokenHash).Scan(&rt.ID, &rt.UserID, &rt.TokenHash, &rt.Revogado, &rawExpiry)
	if err != nil {
		return StoredRefreshToken{}, err
	}
	expiryUnix, err := toUnixSeconds(rawExpiry)
	if err != nil {
		return StoredRefreshToken{}, err
	}
	rt.DataExpiracao = expiryUnix
	return rt, nil
}

func (r *Repository) RevokeRefreshToken(ctx context.Context, q dbtx, tokenID int64, now time.Time) error {
	const query = `
UPDATE tb_refresh_token
SET revogado = TRUE, data_revogacao = ?
WHERE id_refresh_token = ?`
	res, err := q.ExecContext(ctx, query, now, tokenID)
	if err != nil {
		return err
	}
	rows, err := res.RowsAffected()
	if err != nil {
		return err
	}
	if rows == 0 {
		return fmt.Errorf("refresh token not found")
	}
	return nil
}

func (r *Repository) MarkActivePasswordResetTokensUsedByUserID(ctx context.Context, q dbtx, userID int64) error {
	const query = `
UPDATE tb_password_reset_token
SET usado = TRUE
WHERE id_usuario = ? AND usado = FALSE`
	_, err := q.ExecContext(ctx, query, userID)
	return err
}

func (r *Repository) InsertPasswordResetToken(ctx context.Context, q dbtx, userID int64, token string, expiresAt time.Time) error {
	const query = `
INSERT INTO tb_password_reset_token (token, id_usuario, data_expiracao, usado)
VALUES (?, ?, ?, FALSE)`
	_, err := q.ExecContext(ctx, query, token, userID, expiresAt)
	return err
}

func (r *Repository) FindPasswordResetToken(ctx context.Context, q dbtx, token string) (StoredPasswordResetToken, error) {
	const query = `
SELECT id_token, token, id_usuario, usado, data_expiracao
FROM tb_password_reset_token
WHERE token = ?
LIMIT 1`

	var item StoredPasswordResetToken
	var rawExpiry any
	err := q.QueryRowContext(ctx, query, token).Scan(&item.ID, &item.Token, &item.UserID, &item.Usado, &rawExpiry)
	if err != nil {
		return StoredPasswordResetToken{}, err
	}
	expiryUnix, err := toUnixSeconds(rawExpiry)
	if err != nil {
		return StoredPasswordResetToken{}, err
	}
	item.DataExpiracao = expiryUnix
	return item, nil
}

func (r *Repository) MarkPasswordResetTokenUsedByID(ctx context.Context, q dbtx, tokenID int64) error {
	const query = `UPDATE tb_password_reset_token SET usado = TRUE WHERE id_token = ?`
	_, err := q.ExecContext(ctx, query, tokenID)
	return err
}

func (r *Repository) MarkOtherPasswordResetTokensUsedByUserID(ctx context.Context, q dbtx, userID, tokenID int64) error {
	const query = `
UPDATE tb_password_reset_token
SET usado = TRUE
WHERE id_usuario = ? AND usado = FALSE AND id_token <> ?`
	_, err := q.ExecContext(ctx, query, userID, tokenID)
	return err
}

func (r *Repository) UpdateUserPasswordHashByID(ctx context.Context, q dbtx, userID int64, passwordHash string) error {
	const query = `UPDATE tb_usuario SET password_hash = ? WHERE id_usuario = ?`
	_, err := q.ExecContext(ctx, query, passwordHash, userID)
	return err
}

func toUnixSeconds(raw any) (int64, error) {
	switch v := raw.(type) {
	case time.Time:
		return v.UTC().Unix(), nil
	case int64:
		return v, nil
	case int32:
		return int64(v), nil
	case int:
		return int64(v), nil
	case float64:
		return int64(v), nil
	case []byte:
		return parseTimeOrUnix(string(v))
	case string:
		return parseTimeOrUnix(v)
	default:
		return 0, fmt.Errorf("unsupported expiry type %T", raw)
	}
}

func parseTimeOrUnix(value string) (int64, error) {
	v := value
	if unix, err := strconv.ParseInt(v, 10, 64); err == nil {
		return unix, nil
	}

	layouts := []string{
		time.RFC3339Nano,
		time.RFC3339,
		"2006-01-02 15:04:05",
		"2006-01-02 15:04:05.999999999",
	}
	for _, layout := range layouts {
		if parsed, err := time.Parse(layout, v); err == nil {
			return parsed.UTC().Unix(), nil
		}
	}
	return 0, fmt.Errorf("invalid expiry format: %s", value)
}
