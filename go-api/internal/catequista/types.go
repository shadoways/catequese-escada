package catequista

type Catequista struct {
	IDCatequista   int64  `json:"idCatequista"`
	Nome           string `json:"nome"`
	Telefone       string `json:"telefone,omitempty"`
	Email          string `json:"email,omitempty"`
	Endereco       string `json:"endereco,omitempty"`
	DataNascimento string `json:"dataNascimento,omitempty"`
	DataInicio     string `json:"dataInicio,omitempty"`
	Ativo          bool   `json:"ativo"`
}
