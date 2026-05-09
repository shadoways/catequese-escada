package evento

type Evento struct {
	IDEvento    int64  `json:"idEvento"`
	Titulo      string `json:"titulo"`
	Nivel       string `json:"nivel,omitempty"`
	PublicoAlvo string `json:"publicoAlvo,omitempty"`
	Descricao   string `json:"descricao,omitempty"`
	DataInicio  string `json:"dataInicio,omitempty"`
	DataFim     string `json:"dataFim,omitempty"`
	Local       string `json:"local,omitempty"`
}
