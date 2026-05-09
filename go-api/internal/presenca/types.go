package presenca

type CatequisandoRef struct {
	IDCatequisando int64 `json:"idCatequisando"`
}

type Presenca struct {
	IDPresenca   int64            `json:"idPresenca"`
	Data         string           `json:"data,omitempty"`
	Presente     *bool            `json:"presente,omitempty"`
	Catequisando *CatequisandoRef `json:"catequisando,omitempty"`
}
