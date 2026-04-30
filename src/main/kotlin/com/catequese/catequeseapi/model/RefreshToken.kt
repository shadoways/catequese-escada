package com.catequese.catequeseapi.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "tb_refresh_token")
data class RefreshToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_refresh_token")
    val idRefreshToken: Long = 0,

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    val usuario: Usuario,

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    val tokenHash: String,

    @Column(name = "data_expiracao", nullable = false)
    val dataExpiracao: LocalDateTime,

    @Column(name = "revogado", nullable = false)
    val revogado: Boolean = false,

    @Column(name = "data_criacao", nullable = false)
    val dataCriacao: LocalDateTime = LocalDateTime.now(),

    @Column(name = "data_revogacao")
    val dataRevogacao: LocalDateTime? = null
)

