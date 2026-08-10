package com.catequese.catequeseapi.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Token de recuperacao de senha ("esqueci minha senha").
 *
 * IMPORTANTE: o banco guarda apenas o HASH do token, nunca o valor enviado por
 * e-mail. Assim, quem conseguir ler a tabela nao consegue redefinir a senha de
 * ninguem -- mesma logica de guardar senha com hash.
 *
 * O token e de uso unico (usado_em) e expira em poucos minutos.
 */
@Entity
@Table(name = "tb_token_recuperacao")
data class TokenRecuperacao(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_token")
    val idToken: Long = 0,

    @Column(name = "id_usuario")
    val idUsuario: Long,

    /** SHA-256 do token, em hexadecimal (64 caracteres). */
    @Column(name = "token_hash")
    val tokenHash: String,

    @Column(name = "expira_em")
    val expiraEm: LocalDateTime,

    @Column(name = "usado_em")
    val usadoEm: LocalDateTime? = null,

    @Column(name = "criado_em")
    val criadoEm: LocalDateTime,

    /** Guardado so para investigar abuso. */
    @Column(name = "ip_solicitante")
    val ipSolicitante: String? = null
) {
    fun estaValido(agora: LocalDateTime = LocalDateTime.now()): Boolean =
        usadoEm == null && expiraEm.isAfter(agora)
}
