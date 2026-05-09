package conhecimento

type CatequistaRef struct {
	IDCatequista int64 `json:"idCatequista"`
}

type Conhecimento struct {
	IDConhecimento   int64          `json:"idConhecimento"`
	AreaConhecimento string         `json:"areaConhecimento,omitempty"`
	Nivel            string         `json:"nivel,omitempty"`
	Descricao        string         `json:"descricao,omitempty"`
	Catequista       *CatequistaRef `json:"catequista,omitempty"`
}
