package com.catequese.catequeseapi.model

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
 * Uma trilha de formacao de catequistas -- "Escola Diocesana 2026", "Formacao
 * paroquial 2026".
 *
 * A formacao e um conteiner com varios encontros, e nao um evento solto: e o
 * que permite dizer "faltou 2 de 6 na Diocesana" em vez de um percentual unico
 * e sem significado no fim do ano. Cada encontro e um Evento com
 * `tipo = FORMACAO` apontando para c'a por `idFormacao`.
 *
 * `percentualMinimo` e campo, e nao constante: entre as escolas diocesanas
 * consultadas o minimo varia (Divinopolis exige 80%, Santo Andre 75%), entao
 * fixar 80 no codigo quebraria em qualquer diocese que use outro numero.
 */
@Entity
@Table(name = "tb_formacao")
data class Formacao(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_formacao")
    val idFormacao: Long = 0,

    val nome: String,

    /**
     * Formacao existe do nivel paroquial para cima. Nao ha formacao "de turma":
     * quem se forma e o catequista, nao a turma.
     */
    @Enumerated(EnumType.STRING)
    val nivel: NivelEvento = NivelEvento.PAROQUIAL,

    val ano: Int? = null,

    val descricao: String? = null,

    /** Percentual de presenca exigido para concluir. Padrao 80. */
    @Column(name = "percentual_minimo")
    val percentualMinimo: Int = 80,

    @Enumerated(EnumType.STRING)
    val situacao: SituacaoFormacao = SituacaoFormacao.ABERTA,

    @Column(name = "criado_por")
    val criadoPor: String? = null,

    @Column(name = "criado_em")
    val criadoEm: LocalDateTime? = null
)

/**
 * Inscricao nominal de um catequista numa formacao.
 *
 * Existe para que o percentual signifique alguma coisa. Sem inscricao, quem
 * comecou a atuar em setembro apareceria com 30% de frequencia numa formacao
 * que correu o ano todo -- o numero acusaria uma falta que nunca houve.
 */
@Entity
@Table(name = "tb_formacao_inscrito")
data class FormacaoInscrito(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_formacao_inscrito")
    val idFormacaoInscrito: Long = 0,

    @Column(name = "id_formacao")
    val idFormacao: Long = 0,

    @Column(name = "id_catequista")
    val idCatequista: Long = 0,

    @Column(name = "inscrito_em")
    val inscritoEm: LocalDateTime? = null
)

/**
 * Presenca de um CATEQUISTA num encontro de formacao.
 *
 * Tabela propria em vez de reaproveitar tb_presenca: aquela tabela liga
 * catequisando a matricula, e nao teria como registrar catequista sem
 * distorcer o modelo. Alem disso as duas contam de formas diferentes -- esta
 * apura os 80% da formacao, e a de la apura a frequencia da turma.
 */
@Entity
@Table(name = "tb_presenca_formacao")
data class PresencaFormacao(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_presenca_formacao")
    val idPresencaFormacao: Long = 0,

    @Column(name = "id_evento")
    val idEvento: Long = 0,

    @Column(name = "id_catequista")
    val idCatequista: Long = 0,

    @Enumerated(EnumType.STRING)
    val situacao: SituacaoPresenca = SituacaoPresenca.PRESENTE,

    /** Obrigatoria quando a situacao e JUSTIFICADA -- mesma regra de Presenca. */
    val justificativa: String? = null,

    @Column(name = "marcado_por")
    val marcadoPor: String? = null,

    @Column(name = "marcado_em")
    val marcadoEm: LocalDateTime? = null
)
