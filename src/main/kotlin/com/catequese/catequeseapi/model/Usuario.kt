package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "tb_usuario")
data class Usuario(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    val idUsuario: Long = 0,

    @Column(nullable = false)
    val nome: String,

    @Column(nullable = false, unique = true)
    val email: String,

    @Column(name = "password_hash", nullable = false)
    @JsonIgnore
    val passwordHash: String,

    @Column(nullable = false)
    val ativo: Boolean = true,

    @Column(name = "ultimo_login")
    val ultimoLogin: LocalDateTime? = null,

    @ManyToOne
    @JoinColumn(name = "id_comunidade")
    val comunidade: Comunidade? = null,

    @ManyToOne
    @JoinColumn(name = "id_catequista")
    val catequista: Catequista? = null,

    @OneToMany(mappedBy = "usuario", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    val roles: List<UsuarioRole> = emptyList()
)

