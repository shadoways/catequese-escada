package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.model.SituacaoFormacao
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.model.TipoEvento
import java.time.LocalDate

/**
 * Um evento como a agenda o mostra.
 *
 * Alem dos campos do evento vem `podeEditar`, ja resolvido no servidor. A tela
 * nao tem como recalcular isso sozinha sem reimplementar a regra em JavaScript
 * -- e regra de permissao duplicada e regra que vai divergir.
 */
data class EventoAgendaDTO(
    val idEvento: Long,
    val titulo: String,
    val tipo: TipoEvento,
    val tipoRotulo: String,
    val nivel: NivelEvento?,
    val nivelRotulo: String?,
    val idComunidade: Long?,
    val comunidadeNome: String?,
    val idTurma: Long?,
    val turmaNome: String?,
    val idFormacao: Long?,
    val formacaoNome: String?,
    val descricao: String?,
    val dataInicio: LocalDate?,
    val dataFim: LocalDate?,
    val horaInicio: String?,
    val local: String?,
    val situacao: SituacaoEvento,
    val motivoCancelamento: String?,
    val podeEditar: Boolean,

    /** Só preenchido em evento de formação: como está a frequência de quem pediu. */
    val minhaFrequencia: FrequenciaFormacaoDTO? = null
)

/** O que a tela precisa para montar o formulário sem chutar o que é permitido. */
data class OpcoesAgendaDTO(
    val niveisQuePodeCriar: List<OpcaoDTO>,
    val tipos: List<OpcaoDTO>,
    val comunidades: List<OpcaoDTO>,
    val turmas: List<OpcaoDTO>,
    val formacoes: List<OpcaoDTO>,
    val podeCriar: Boolean
)

data class OpcaoDTO(
    val valor: String,
    val rotulo: String
)

data class EventoFormDTO(
    val titulo: String?,
    val tipo: String?,
    val nivel: String?,
    val idComunidade: Long? = null,
    val idTurma: Long? = null,
    val idFormacao: Long? = null,
    val descricao: String? = null,
    val dataInicio: LocalDate? = null,
    val dataFim: LocalDate? = null,
    val horaInicio: String? = null,
    val local: String? = null,
    val situacao: String? = null,
    val motivoCancelamento: String? = null,

    /**
     * O usuário viu os conflitos e quer marcar assim mesmo.
     *
     * Existe porque bloquear de vez seria errado: há casos legítimos de dois
     * eventos no mesmo dia para o mesmo público (a missa de manhã e o retiro
     * à tarde). O sistema avisa e mostra o que bate; quem decide é quem
     * conhece a paróquia.
     */
    val confirmarConflito: Boolean = false
)

/** Um evento que já ocupa o mesmo público na mesma data. */
data class ConflitoDTO(
    val idEvento: Long,
    val titulo: String,
    val tipoRotulo: String,
    val nivelRotulo: String?,

    /** "toda a paróquia", "comunidade São José", "turma Crisma II". */
    val alcance: String,
    val dataInicio: LocalDate?,
    val horaInicio: String?,
    val local: String?
)

/** Resposta da checagem prévia, enquanto a pessoa ainda preenche o formulário. */
data class ChecagemConflitoDTO(
    val temConflito: Boolean,
    val conflitos: List<ConflitoDTO>
)

/** Frequência de um catequista numa formação. */
data class FrequenciaFormacaoDTO(
    val idFormacao: Long,
    val formacaoNome: String,
    val percentualMinimo: Int,

    /** Encontros já realizados da formação. Previstos não entram. */
    val encontrosRealizados: Int,

    val presencas: Int,
    val faltas: Int,
    val justificadas: Int,

    /** Null quando ainda não houve encontro realizado — não é 0%, é "sem apuração". */
    val percentual: Int?,

    val atingiuMinimo: Boolean
)

data class FormacaoResumoDTO(
    val idFormacao: Long,
    val nome: String,
    val nivel: NivelEvento,
    val nivelRotulo: String,
    val ano: Int?,
    val descricao: String?,
    val percentualMinimo: Int,
    val situacao: SituacaoFormacao,
    val totalEncontros: Int,
    val encontrosRealizados: Int,
    val totalInscritos: Int,
    val inscritosEmDia: Int,
    val inscritosAbaixo: Int,
    val podeEditar: Boolean
)

data class FormacaoDetalheDTO(
    val formacao: FormacaoResumoDTO,
    val encontros: List<EventoAgendaDTO>,
    val inscritos: List<InscritoFormacaoDTO>
)

data class InscritoFormacaoDTO(
    val idCatequista: Long,
    val nome: String,
    val frequencia: FrequenciaFormacaoDTO
)

data class FormacaoFormDTO(
    val nome: String?,
    val nivel: String?,
    val ano: Int? = null,
    val descricao: String? = null,
    val percentualMinimo: Int? = null,
    val situacao: String? = null
)

data class MarcacaoFormacaoDTO(
    val idCatequista: Long,
    val situacao: String,
    val justificativa: String? = null
)

data class ChamadaFormacaoDTO(
    val marcacoes: List<MarcacaoFormacaoDTO> = emptyList()
)

/**
 * A faixa do topo da agenda: o que responde "tem algo comigo?" antes de o
 * usuário ler a lista.
 */
data class ResumoAgendaDTO(
    val proximoEvento: EventoAgendaDTO?,
    val formacoesEmRisco: List<FrequenciaFormacaoDTO>,
    val eventosDasMinhasTurmas: Int,
    val totalNoAno: Int
)

data class AgendaDTO(
    val ano: Int,
    val resumo: ResumoAgendaDTO,
    val eventos: List<EventoAgendaDTO>
)
