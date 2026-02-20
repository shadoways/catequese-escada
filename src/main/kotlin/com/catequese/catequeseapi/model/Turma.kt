package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "tb_turma")
data class Turma(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idTurma: Long = 0,

    val nome: String,
    val descricao: String?,
    val ano: Int?,
    val nivel: String?,

    @ManyToOne
    @JoinColumn(name = "id_catequista")
    val catequista: Catequista? = null,

    @OneToMany(mappedBy = "turma")
    @JsonIgnore
    val catequisandos: List<Catequisando> = emptyList()
)