package com.catequese.catequeseapi.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Um item da agenda da catequese.
 *
 * Era uma tabela solta com `nivel` e `publico_alvo` em texto livre e nenhum
 * vinculo: na pratica todo evento era global, aparecia para todo mundo e nao
 * havia como dizer de quem ele era. Agora responde a duas perguntas
 * independentes -- `nivel` (de quem e, decide permissao) e `tipo` (o que e,
 * decide como aparece).
 *
 * Ids soltos para comunidade, turma e formacao em vez de relacao JPA: os tres
 * sao opcionais e dependem do nivel, e uma relacao ManyToOne nula em tres
 * campos so complicaria a serializacao sem ganhar nada -- o mesmo criterio ja
 * usado em TurmaCatequista.
 */
@Entity
@Table(name = "tb_evento")
data class Evento(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_evento")
    val idEvento: Long = 0,

    val titulo: String,

    @Enumerated(EnumType.STRING)
    val tipo: TipoEvento = TipoEvento.ENCONTRO,

    /**
     * Era String livre. Fica anulavel porque os registros antigos nao tinham
     * enum nenhum -- a migracao converte o que da e deixa o resto nulo, que a
     * tela mostra como "sem nivel definido" em vez de sumir.
     */
    @Enumerated(EnumType.STRING)
    val nivel: NivelEvento? = null,

    /** Preenchido quando o nivel e COMUNIDADE. */
    @Column(name = "id_comunidade")
    val idComunidade: Long? = null,

    /** Preenchido quando o nivel e TURMA. */
    @Column(name = "id_turma")
    val idTurma: Long? = null,

    /** Preenchido quando o evento e um encontro de uma trilha de formacao. */
    @Column(name = "id_formacao")
    val idFormacao: Long? = null,

    /** Legado: texto livre da versao anterior. Mantido para nao perder dado. */
    @Column(name = "publico_alvo")
    val publicoAlvo: String? = null,

    val descricao: String? = null,

    @Column(name = "data_inicio")
    val dataInicio: LocalDate? = null,

    @Column(name = "data_fim")
    val dataFim: LocalDate? = null,

    /** Hora de inicio, quando a paroquia quiser registrar. Opcional de proposito. */
    @Column(name = "hora_inicio")
    val horaInicio: String? = null,

    val local: String? = null,

    @Enumerated(EnumType.STRING)
    val situacao: SituacaoEvento = SituacaoEvento.PREVISTO,

    /** Obrigatorio quando o evento e cancelado -- mesma regra do Encontro. */
    @Column(name = "motivo_cancelamento")
    val motivoCancelamento: String? = null,

    @Column(name = "criado_por")
    val criadoPor: String? = null,

    @Column(name = "criado_em")
    val criadoEm: LocalDateTime? = null,

    @Column(name = "alterado_por")
    val alteradoPor: String? = null,

    @Column(name = "alterado_em")
    val alteradoEm: LocalDateTime? = null
) {
    /** Data que vale para ordenar e agrupar por mes. */
    fun dataDeReferencia(): LocalDate? = dataInicio ?: dataFim

    /** So evento realizado de formacao entra no calculo dos 80%. */
    fun contaParaFrequenciaDeFormacao(): Boolean =
        tipo == TipoEvento.FORMACAO &&
            idFormacao != null &&
            situacao == SituacaoEvento.REALIZADO
}
