package com.catequese.catequeseapi.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "tb_evento")
data class Evento(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idEvento: Long = 0,

    val titulo: String,
    val nivel: String?,
    val publicoAlvo: String?,
    val descricao: String?,
    val dataInicio: LocalDate?,
    val dataFim: LocalDate?,
    val local: String?
)