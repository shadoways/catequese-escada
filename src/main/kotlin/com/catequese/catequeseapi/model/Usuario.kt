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

    /** Usado para recuperacao de senha. */
    val email: String? = null,

    /** Reservado para um futuro envio por SMS. Nao usado hoje. */
    val telefone: String? = null,

    /**
     * Senha gerada pelo sistema (criacao do usuario ou reset pelo admin).
     * Enquanto for true o usuario so consegue trocar a propria senha --
     * qualquer outra chamada e barrada no JwtAuthFilter.
     */
    @Column(name = "senha_provisoria")
    val senhaProvisoria: Boolean = false,

    /**
     * Momento da ultima troca de senha. Vai dentro do JWT: se nao bater com o
     * banco, o token e de antes da troca e nao vale mais.
     */
    @Column(name = "data_troca_senha")
    val dataTrocaSenha: LocalDateTime? = null,

    @Column(name = "ultimo_login")
    val ultimoLogin: LocalDateTime? = null,

    /** Zera a cada login correto. Alimenta o bloqueio anti forca-bruta. */
    @Column(name = "tentativas_falhas")
    val tentativasFalhas: Int = 0,

    @Column(name = "bloqueado_ate")
    val bloqueadoAte: LocalDateTime? = null,

    @Column(name = "id_catequista")
    val idCatequista: Long? = null,

    @Column(name = "id_coordenador")
    val idCoordenador: Long? = null,

    /**
     * Comunidade do usuario. O coordenador so enxerga os dados da propria
     * comunidade; o coordenador paroquial ve todas, e por isso costuma ficar
     * sem vinculo aqui.
     */
    @Column(name = "id_comunidade")
    val idComunidade: Long? = null,

    val ativo: Boolean = true,

    @Column(name = "data_criacao")
    val dataCriacao: LocalDateTime? = null
) {
    /** True enquanto o bloqueio temporario por tentativas erradas estiver valendo. */
    fun estaBloqueado(agora: LocalDateTime = LocalDateTime.now()): Boolean =
        bloqueadoAte?.isAfter(agora) == true
}
