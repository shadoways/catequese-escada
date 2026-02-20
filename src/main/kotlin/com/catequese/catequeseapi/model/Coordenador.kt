package com.catequese.catequeseapi.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "tb_coordenador")
data class Coordenador(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idCoordenador: Long = 0,

    val nome: String,
    val telefone: String?,
    val email: String?,
    val nivelOrganizacional: String?,
    val dataNascimento: LocalDate?,
    val dataInicio: LocalDate?,
    val ativo: Boolean = true
)