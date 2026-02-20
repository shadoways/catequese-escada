package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "tb_comunidade")
data class Comunidade(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idComunidade: Long = 0,

    val nome: String,
    val descricao: String?,
    val ativo: Boolean = true,

    @OneToMany(mappedBy = "comunidade")
    @JsonIgnore
    val catequisandos: List<Catequisando> = emptyList()
)

