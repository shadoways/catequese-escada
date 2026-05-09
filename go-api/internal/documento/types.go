package documento

type CatequisandoRef struct {
	IDCatequisando int64 `json:"idCatequisando"`
}

type Documento struct {
	IDDocumento    int64            `json:"idDocumento"`
	TipoDocumento  string           `json:"tipoDocumento,omitempty"`
	CaminhoArquivo string           `json:"caminhoArquivo,omitempty"`
	DataEnvio      string           `json:"dataEnvio,omitempty"`
	Catequisando   *CatequisandoRef `json:"catequisando,omitempty"`
	TipoStatus     string           `json:"tipoStatus,omitempty"`
}

type StatusUpdateRequest struct {
	NovoStatus string `json:"novoStatus"`
}
