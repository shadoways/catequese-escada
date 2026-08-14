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
 * Por qual etapa do catecumenato a pessoa passou, e quando.
 *
 * E um historico, e nao um campo unico, porque cada etapa tem apuracao de
 * frequencia propria: para dizer "de marco a agosto ele esteve no Catecumenato
 * e teve 82% naquele periodo" e preciso saber quando a etapa comecou e quando
 * terminou. A duracao varia de pessoa para pessoa.
 *
 * A etapa em aberto e a que tem dataFim nula.
 */
@Entity
@Table(name = "tb_etapa_catecumeno")
data class EtapaCatecumeno(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_etapa")
    val idEtapa: Long = 0,

    @ManyToOne
    @JoinColumn(name = "id_catequisando")
    val catequisando: Catequisando? = null,

    @Enumerated(EnumType.STRING)
    val etapa: EtapaCatecumenato = EtapaCatecumenato.PRE_CATECUMENATO,

    @Column(name = "data_inicio")
    val dataInicio: LocalDate? = null,

    /** Nula enquanto a pessoa estiver nesta etapa. */
    @Column(name = "data_fim")
    val dataFim: LocalDate? = null,

    val observacao: String? = null,

    @Column(name = "registrado_por")
    val registradoPor: String? = null,

    @Column(name = "registrado_em")
    val registradoEm: LocalDateTime? = null
) {
    fun emAndamento(): Boolean = dataFim == null
}
