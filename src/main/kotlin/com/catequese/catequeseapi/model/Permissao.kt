package com.catequese.catequeseapi.model

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "tb_permissoes")
data class Permissao(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idPermissao: Long = 0,

    val permissao: String?,

    @ManyToOne
    @JoinColumn(name = "id_login")
    val login: Login? = null
)