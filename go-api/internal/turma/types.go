package turma

type Turma struct {
	IDTurma      int64  `json:"idTurma"`
	Nome         string `json:"nome"`
	Descricao    string `json:"descricao,omitempty"`
	Ano          *int64 `json:"ano,omitempty"`
	Nivel        string `json:"nivel,omitempty"`
	IDCatequista *int64 `json:"idCatequista,omitempty"`
}
