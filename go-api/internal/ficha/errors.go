package ficha

import "errors"

var (
	ErrNotFound             = errors.New("Ficha não encontrada")
	ErrCatequisandoNotFound = errors.New("Catequisando não encontrado")
)
