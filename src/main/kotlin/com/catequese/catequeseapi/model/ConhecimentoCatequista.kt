package com.catequese.catequeseapi.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "tb_conhecimento_catequista")
data class ConhecimentoCatequista(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idConhecimento: Long = 0,

    val areaConhecimento: String?,
    val nivel: String?,
    val descricao: String?,

    @ManyToOne
    @JoinColumn(name = "id_catequista")
    val catequista: Catequista? = null
)