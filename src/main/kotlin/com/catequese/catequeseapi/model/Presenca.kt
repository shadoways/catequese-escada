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
 * Marcacao de um catequisando num encontro.
 *
 * `data` e `presente` sao os campos originais e continuam aqui por causa dos
 * registros antigos, gravados antes de existir o conceito de encontro. O que
 * vale daqui em diante e `encontro` + `situacao`.
 *
 * Toda marcacao guarda quem marcou e quando: era um pedido explicito, para
 * haver controle de quem lancou a presenca.
 */
@Entity
@Table(name = "tb_presenca")
data class Presenca(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idPresenca: Long = 0,

    /** Legado: mantido para os registros anteriores ao conceito de encontro. */
    val data: LocalDate?,

    /** Legado: substituido por `situacao`, que distingue falta justificada. */
    val presente: Boolean? = false,

    @ManyToOne
    @JoinColumn(name = "id_catequisando")
    val catequisando: Catequisando? = null,

    @ManyToOne
    @JoinColumn(name = "id_encontro")
    val encontro: Encontro? = null,

    @Enumerated(EnumType.STRING)
    val situacao: SituacaoPresenca? = null,

    /** Obrigatoria quando a situacao e JUSTIFICADA. */
    val justificativa: String? = null,

    @Column(name = "marcado_por")
    val marcadoPor: String? = null,

    @Column(name = "marcado_em")
    val marcadoEm: LocalDateTime? = null
) {
    /**
     * Falta justificada sai da conta em vez de contar contra: o encontro
     * inteiro deixa de ser considerado para aquela pessoa.
     */
    fun entraNoTotal(): Boolean = situacao != SituacaoPresenca.JUSTIFICADA

    fun compareceu(): Boolean = situacao == SituacaoPresenca.PRESENTE
}
