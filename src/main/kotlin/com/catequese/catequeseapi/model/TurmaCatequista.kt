package com.catequese.catequeseapi.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Equipe de catequistas da turma.
 *
 * A turma ja tinha um catequista, mas so um: substituto ou dupla de catequistas
 * e comum, e sem isso o substituto nao conseguiria fazer a chamada. O campo
 * antigo em tb_turma continua valendo como responsavel principal, para nao
 * quebrar o que ja existe; esta tabela acrescenta os demais.
 *
 * Ids soltos em vez de relacao JPA: a tabela e so um vinculo, e assim ela nao
 * interfere na serializacao de Turma nem de Catequista, que ja saem em JSON
 * em varias telas.
 */
@Entity
@Table(name = "tb_turma_catequista")
data class TurmaCatequista(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turma_catequista")
    val idTurmaCatequista: Long = 0,

    @Column(name = "id_turma")
    val idTurma: Long = 0,

    @Column(name = "id_catequista")
    val idCatequista: Long = 0,

    /** Marca o responsavel principal, quando houver mais de um. */
    val principal: Boolean = false,

    @Column(name = "criado_em")
    val criadoEm: LocalDateTime? = null
)
