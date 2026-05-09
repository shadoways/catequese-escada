package comunidade

type Comunidade struct {
	IDComunidade int64  `json:"idComunidade"`
	Nome         string `json:"nome"`
	Descricao    string `json:"descricao,omitempty"`
	Ativo        bool   `json:"ativo"`
}
