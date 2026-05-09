package usuario

type CreateUsuarioRequest struct {
	Nome         string   `json:"nome"`
	Email        string   `json:"email"`
	Password     string   `json:"password"`
	Roles        []string `json:"roles"`
	IDComunidade *int64   `json:"idComunidade"`
	IDCatequista *int64   `json:"idCatequista"`
}

type UpdateUsuarioRequest struct {
	Nome         string   `json:"nome"`
	Email        string   `json:"email"`
	Ativo        bool     `json:"ativo"`
	Roles        []string `json:"roles"`
	IDComunidade *int64   `json:"idComunidade"`
	IDCatequista *int64   `json:"idCatequista"`
}

type UsuarioDTO struct {
	IDUsuario    int64    `json:"idUsuario"`
	Nome         string   `json:"nome"`
	Email        string   `json:"email"`
	Ativo        bool     `json:"ativo"`
	Roles        []string `json:"roles"`
	IDComunidade *int64   `json:"idComunidade"`
	IDCatequista *int64   `json:"idCatequista"`
}

type usuarioDB struct {
	IDUsuario    int64
	Nome         string
	Email        string
	PasswordHash string
	Ativo        bool
	IDComunidade *int64
	IDCatequista *int64
}
