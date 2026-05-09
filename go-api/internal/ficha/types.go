package ficha

type FichaInscricao struct {
	IDFicha        int64  `json:"idFicha"`
	DataInscricao  string `json:"dataInscricao,omitempty"`
	Observacoes    string `json:"observacoes,omitempty"`
	CatequisandoID *int64 `json:"catequisandoId,omitempty"`
}

type FichaInscricaoRequest struct {
	DataInscricao  string `json:"dataInscricao"`
	Observacoes    string `json:"observacoes"`
	CatequisandoID *int64 `json:"catequisandoId"`
}

type fichaDB struct {
	IDFicha        int64
	DataInscricao  string
	Observacoes    string
	CatequisandoID *int64
}
