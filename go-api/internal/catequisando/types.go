package catequisando

type TurmaRef struct {
	IDTurma int64  `json:"idTurma"`
	Nome    string `json:"nome,omitempty"`
}

type ComunidadeRef struct {
	IDComunidade int64  `json:"idComunidade"`
	Nome         string `json:"nome,omitempty"`
}

type Catequisando struct {
	IDCatequisando        int64          `json:"idCatequisando"`
	Nome                  string         `json:"nome"`
	Telefone              string         `json:"telefone,omitempty"`
	Email                 string         `json:"email,omitempty"`
	DataNascimento        string         `json:"dataNascimento,omitempty"`
	NomeResponsavel       string         `json:"nomeResponsavel,omitempty"`
	TelefoneResponsavel   string         `json:"telefoneResponsavel,omitempty"`
	Endereco              string         `json:"endereco,omitempty"`
	NumeroDocumento       string         `json:"numeroDocumento,omitempty"`
	TipoDocumento         string         `json:"tipoDocumento,omitempty"`
	IntoleranteGluten     bool           `json:"intoleranteGluten"`
	FoiBatizado           bool           `json:"foiBatizado"`
	FezPrimeiraEucaristia bool           `json:"fezPrimeiraEucaristia"`
	EstadoConjugal        string         `json:"estadoConjugal,omitempty"`
	Ativo                 bool           `json:"ativo"`
	Turma                 *TurmaRef      `json:"turma,omitempty"`
	Comunidade            *ComunidadeRef `json:"comunidade,omitempty"`
}
