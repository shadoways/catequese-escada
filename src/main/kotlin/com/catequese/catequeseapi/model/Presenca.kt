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
@Table(name = "tb_presenca")
data class Presenca(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idPresenca: Long = 0,

    val data: LocalDate?,
    val presente: Boolean? = false,

    @ManyToOne
    @JoinColumn(name = "id_catequisando")
    val catequisando: Catequisando? = null
)