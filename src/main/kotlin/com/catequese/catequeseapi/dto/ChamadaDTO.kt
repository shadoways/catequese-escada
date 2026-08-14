package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.Encontro
import com.catequese.catequeseapi.model.SituacaoEncontro
import com.catequese.catequeseapi.model.SituacaoPresenca
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Uma turma na tela "Minhas turmas".
 *
 * Traz junto o encontro em aberto porque e a primeira coisa que o catequista
 * precisa saber ao abrir o sistema: ou ele continua a chamada de hoje, ou
 * comeca uma nova. Sem isso a tela precisaria de uma segunda chamada a API
 * por turma so para descobrir em que pe cada uma esta.
 */
data class TurmaChamadaDTO(
    val idTurma: Long,
    val nome: String,
    val categoria: CategoriaTurma?,
    val etapa: Int?,
    val ano: Int,
    val matriculados: Int,
    val exigeFrequencia: Boolean,
    /** Nulo quando nao ha chamada em andamento. */
    val encontroAberto: EncontroDTO? = null,
    val ultimoEncontro: LocalDate? = null
)

/** Abertura da chamada do dia. Sem data, assume hoje. */
data class AbrirEncontroDTO(
    val idTurma: Long = 0,
    val data: LocalDate? = null,
    val tema: String? = null
)

/** Uma marcacao. `justificativa` e obrigatoria quando a situacao e JUSTIFICADA. */
data class MarcarPresencaDTO(
    val idCatequisando: Long = 0,
    val situacao: SituacaoPresenca = SituacaoPresenca.FALTA,
    val justificativa: String? = null
)

/** Envio da chamada inteira de uma vez, ao final do encontro. */
data class MarcarLoteDTO(
    val marcacoes: List<MarcarPresencaDTO> = emptyList()
)

/**
 * Fechamento ou cancelamento.
 * O motivo passa a ser obrigatorio quando ninguem foi marcado como presente:
 * na pratica, um encontro sem nenhum presente e um encontro que nao aconteceu.
 */
data class FinalizarEncontroDTO(
    val motivo: String? = null,
    val tema: String? = null
)

data class EncontroDTO(
    val idEncontro: Long,
    val idTurma: Long?,
    val nomeTurma: String?,
    val data: LocalDate?,
    val tema: String?,
    val situacao: SituacaoEncontro,
    val motivoCancelamento: String?,
    val abertoPor: String?,
    val abertoEm: LocalDateTime?,
    val fechadoPor: String?,
    val fechadoEm: LocalDateTime?,
    val fechamentoAutomatico: Boolean,
    val presentes: Int,
    val faltas: Int,
    val justificadas: Int,
    val totalMatriculados: Int,
    /** A tela usa isto para liberar ou nao os controles de marcacao. */
    val editavel: Boolean
) {
    companion object {
        fun de(
            encontro: Encontro,
            presentes: Int = 0,
            faltas: Int = 0,
            justificadas: Int = 0,
            totalMatriculados: Int = 0,
            editavel: Boolean = false
        ) = EncontroDTO(
            idEncontro = encontro.idEncontro,
            idTurma = encontro.turma?.idTurma,
            nomeTurma = encontro.turma?.nome,
            data = encontro.data,
            tema = encontro.tema,
            situacao = encontro.situacao,
            motivoCancelamento = encontro.motivoCancelamento,
            abertoPor = encontro.abertoPor,
            abertoEm = encontro.abertoEm,
            fechadoPor = encontro.fechadoPor,
            fechadoEm = encontro.fechadoEm,
            fechamentoAutomatico = encontro.fechamentoAutomatico,
            presentes = presentes,
            faltas = faltas,
            justificadas = justificadas,
            totalMatriculados = totalMatriculados,
            editavel = editavel
        )
    }
}

/**
 * Um evento (retiro, missa, encontrao) na tela do catequista.
 *
 * A presenca no evento e gravada como um Encontro comum, ligado ao evento
 * pelo id -- assim toda a maquinaria de chamada (marcar, encerrar, auditoria)
 * vale sem duplicacao. O que muda e que ele NAO entra na conta dos 80%.
 */
data class EventoChamadaDTO(
    val idEvento: Long,
    val titulo: String,
    val local: String?,
    val publicoAlvo: String?,
    val dataInicio: LocalDate?,
    val dataFim: LocalDate?,
    val turmas: List<TurmaEventoDTO> = emptyList()
)

/** Situacao de uma turma naquele evento. */
data class TurmaEventoDTO(
    val idTurma: Long,
    val nomeTurma: String,
    val matriculados: Int,
    /** Nulo quando a chamada do evento ainda nao foi aberta para esta turma. */
    val idEncontro: Long? = null,
    val situacao: SituacaoEncontro? = null,
    val presentes: Int = 0,
    val editavel: Boolean = false
)

/** Abertura da chamada de um evento para uma turma. */
data class AbrirEventoDTO(
    val idEvento: Long = 0,
    val idTurma: Long = 0,
    val data: LocalDate? = null
)

/** Uma linha da lista de chamada. */
data class ItemChamadaDTO(
    val idCatequisando: Long,
    val nome: String,
    val situacao: SituacaoPresenca?,
    val justificativa: String?,
    val marcadoPor: String?,
    val marcadoEm: LocalDateTime?
)

data class ChamadaDTO(
    val encontro: EncontroDTO,
    val itens: List<ItemChamadaDTO>
)
