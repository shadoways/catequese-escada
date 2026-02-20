package com.catequese.catequeseapi.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "tb_ficha_inscricao")
data class FichaInscricao(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idFicha: Long = 0,

    val dataInscricao: LocalDate?,
    val observacoes: String?,

    @ManyToOne
    @JoinColumn(name = "id_catequisando")
    val catequisando: Catequisando? = null
)