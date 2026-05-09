package auth

import "errors"

var (
	ErrInvalidCredentials = errors.New("Credenciais inválidas")
	ErrInvalidRefresh     = errors.New("Refresh token inválido ou expirado")
	ErrInvalidResetToken  = errors.New("Token inválido ou expirado")
	ErrResetTokenUsed     = errors.New("Token já foi utilizado")
	ErrResetTokenExpired  = errors.New("Token expirado")
	ErrWeakPassword       = errors.New("Senha deve ter no mínimo 6 caracteres")
)
