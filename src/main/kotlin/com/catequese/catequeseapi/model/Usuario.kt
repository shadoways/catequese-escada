package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Usuario de acesso ao sistema.
 *
 * Tabela nova e independente: nao altera tb_login/tb_permissoes, que ficam como
 * legado (nunca foram usadas por nenhum codigo). O vinculo com catequista ou
 * coordenador e opcional e existe so para exibicao/relatorio, por isso e um id
 * solto em vez de uma relacao JPA -- assim o admin nao precisa ter registro de
 * coordenador para conseguir entrar.
 */
@Entity
@Table(name = "tb_usuario")
data class Usuario(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    val idUsuario: Long = 0,

    val nome: String,

    val username: String,

    /** Hash BCrypt. Nunca sai em JSON. */
    @JsonIgnore
    @Column(name = "password_hash")
    val passwordHash: String,

    @Enumerated(EnumType.STRING)
    val tipo: TipoUsuario,

    @Column(name = "id_catequista")
    val idCatequista: Long? = null,

    @Column(name = "id_coordenador")
    val idCoordenador: Long? = null,

    val ativo: Boolean = true,

    @Column(name = "data_criacao")
    val dataCriacao: LocalDateTime? = null
)
