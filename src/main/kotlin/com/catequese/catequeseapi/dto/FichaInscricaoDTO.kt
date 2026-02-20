package com.catequese.catequeseapi.dto

import java.time.LocalDate

data class FichaInscricaoDTO(
    val dataInscricao: LocalDate?,
    val observacoes: String?,
    val catequisandoId: Long?
)

