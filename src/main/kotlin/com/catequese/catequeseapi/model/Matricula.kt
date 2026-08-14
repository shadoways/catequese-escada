package com.catequese.catequeseapi.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Vinculo do catequisando com uma turma, num ano.
 *
 * E o que da historico ao sistema: o catequisando deixa de ter uma unica turma
 * e passa a ter um percurso. Sem isso, ao passar de Crisma I para Crisma II o
 * ano anterior se perderia, e nao haveria como saber quem ja cumpriu os dois
 * anos nem consultar turmas antigas.
 *
 * A data de matricula tambem e o marco zero da frequencia: quem entrou em abril
 * nao e cobrado pelos encontros de fevereiro e marco.
 */
@Entity
@Table(name = "tb_matricula")
data class Matricula(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_matricula")
    val idMatricula: Long = 0,

    @ManyToOne
    @JoinColumn(name = "id_catequisando")
    val catequisando: Catequisando? = null,

    @ManyToOne
    @JoinColumn(name = "id_turma")
    val turma: Turma? = null,

    val ano: Int = 0,

    @Column(name = "data_matricula")
    val dataMatricula: LocalDate? = null,

    @Enumerated(EnumType.STRING)
    val situacao: SituacaoMatricula = SituacaoMatricula.CURSANDO,

    val observacao: String? = null,

    @Column(name = "criado_em")
    val criadoEm: LocalDateTime? = null,

    @Column(name = "atualizado_em")
    val atualizadoEm: LocalDateTime? = null,

    @Column(name = "atualizado_por")
    val atualizadoPor: String? = null
)
