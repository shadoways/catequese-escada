package com.catequese.catequeseapi.dto

import java.time.LocalDate

data class DocumentoDTO(
    val tipoDocumento: String?,
    val caminhoArquivo: String?,
    val dataEnvio: LocalDate?,
    val catequisandoId: Long?,
    val tipoStatus: String? = "PENDENTE" // Status inicial, pode ser sobrescrito
)

