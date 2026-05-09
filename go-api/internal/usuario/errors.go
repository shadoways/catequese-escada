package usuario

import "errors"

var (
	ErrNotFound           = errors.New("Usuário não encontrado")
	ErrEmailExists        = errors.New("Email já cadastrado")
	ErrInvalidPassword    = errors.New("Senha deve ter no mínimo 6 caracteres")
	ErrInvalidRole        = errors.New("Role inválida")
	ErrComunidadeNotFound = errors.New("Comunidade não encontrada")
	ErrCatequistaNotFound = errors.New("Catequista não encontrado")
)
