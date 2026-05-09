package coordenador

type Coordenador struct {
	IDCoordenador       int64  `json:"idCoordenador"`
	Nome                string `json:"nome"`
	Telefone            string `json:"telefone,omitempty"`
	Email               string `json:"email,omitempty"`
	NivelOrganizacional string `json:"nivelOrganizacional,omitempty"`
	DataNascimento      string `json:"dataNascimento,omitempty"`
	DataInicio          string `json:"dataInicio,omitempty"`
	Ativo               bool   `json:"ativo"`
}
