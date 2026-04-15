package com.catequese.catequeseapi.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "tb_password_reset_token")
data class PasswordResetToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    val idToken: Long = 0,

    @Column(nullable = false, unique = true)
    val token: String,

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    val usuario: Usuario,

    @Column(name = "data_expiracao", nullable = false)
    val dataExpiracao: LocalDateTime,

    @Column(name = "usado")
    val usado: Boolean = false,

    @Column(name = "data_criacao", nullable = false)
    val dataCriacao: LocalDateTime = LocalDateTime.now()
)

