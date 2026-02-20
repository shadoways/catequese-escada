package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "tb_catequista")
data class Catequista(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idCatequista: Long = 0,

    val nome: String,
    val telefone: String?,
    val email: String?,
    val endereco: String?,
    val dataNascimento: LocalDate?,
    val dataInicio: LocalDate?,
    val ativo: Boolean = true,

    @OneToMany(mappedBy = "catequista")
    @JsonIgnore
    val turmas: List<Turma> = emptyList()
)