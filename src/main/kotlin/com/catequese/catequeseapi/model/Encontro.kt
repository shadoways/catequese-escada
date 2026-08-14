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
 * Um encontro da turma: a "aula" onde a chamada e feita.
 *
 * O catequista abre o encontro do dia, marca a presenca e envia. Depois de
 * fechado a lista nao muda mais -- so o administrador reabre, para corrigir
 * algum engano.
 *
 * Encontro CANCELADO nao entra em conta nenhuma: feriado, chuva ou catequista
 * doente nao podem virar falta de ninguem. Por isso o cancelamento exige
 * motivo registrado.
 */
@Entity
@Table(name = "tb_encontro")
data class Encontro(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_encontro")
    val idEncontro: Long = 0,

    @ManyToOne
    @JoinColumn(name = "id_turma")
    val turma: Turma? = null,

    val data: LocalDate? = null,

    /** Assunto do dia. Vira o diario da turma para quem assumir depois. */
    val tema: String? = null,

    @Enumerated(EnumType.STRING)
    val situacao: SituacaoEncontro = SituacaoEncontro.ABERTO,

    /**
     * Obrigatorio quando o encontro e cancelado -- inclusive no caso de ser
     * fechado sem nenhuma presenca marcada, que na pratica e um cancelamento.
     */
    @Column(name = "motivo_cancelamento")
    val motivoCancelamento: String? = null,

    /** Evento ligado (retiro, missa), quando a presenca nao e do encontro comum. */
    @Column(name = "id_evento")
    val idEvento: Long? = null,

    @Column(name = "aberto_por")
    val abertoPor: String? = null,

    @Column(name = "aberto_em")
    val abertoEm: LocalDateTime? = null,

    @Column(name = "fechado_por")
    val fechadoPor: String? = null,

    @Column(name = "fechado_em")
    val fechadoEm: LocalDateTime? = null,

    /** True quando quem fechou foi o sistema, e nao uma pessoa. */
    @Column(name = "fechamento_automatico")
    val fechamentoAutomatico: Boolean = false
) {
    fun estaAberto(): Boolean = situacao == SituacaoEncontro.ABERTO

    /** So encontro fechado entra no calculo de frequencia. */
    fun contaParaFrequencia(): Boolean = situacao == SituacaoEncontro.FECHADO
}
