package com.catequese.catequeseapi.dtos

data class DocumentoStatusUpdateDTO(
    val idDocumento: Long,
    val novoStatus: String, // PENDENTE, ENTREGUE, REJEITADO, CANCELADO
    val observacao: String? = null
)

data class DocumentoResponseDTO(
    val idDocumento: Long,
    val tipoDocumento: String,
    val caminhoArquivo: String,
    val dataEnvio: String,
    val tipoStatus: String,
    val observacaoEntrega: String?,
    val catequisandoId: Long
)

data class CatequisandoDocumentosStatusDTO(
    val catequisandoId: Long,
    val nomeCatequisando: String,
    val documentos: List<DocumentoStatusDTO>
)

data class DocumentoStatusDTO(
    val tipoDocumento: String,     // DOCUMENTO, CERTIDAO, FOTO, ASSINATURA
    val status: String,             // PENDENTE, ENTREGUE, REJEITADO, CANCELADO
    val observacao: String?
)
