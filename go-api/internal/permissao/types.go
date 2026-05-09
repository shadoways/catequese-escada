package permissao

type LoginRef struct {
	IDLogin int64 `json:"idLogin"`
}

type Permissao struct {
	IDPermissao int64     `json:"idPermissao"`
	Permissao   string    `json:"permissao,omitempty"`
	Login       *LoginRef `json:"login,omitempty"`
}
