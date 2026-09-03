package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoFrequencia
import java.time.LocalDate

/**
 * As telas de detalhe dos indicadores.
 *
 * O resumo geral responde "como esta indo"; cada tela daqui responde uma
 * pergunta so, com os filtros que aquela pergunta pede. Foi o que faltava na
 * primeira versao: tudo numa pagina, com dois filtros para nove blocos, obriga
 * a pessoa a ler o que nao perguntou.
 *
 * A regra da comparacao continua valendo em todas: numero sem base nao e
 * indicador. Onde a comparacao nao faz sentido (uma lista, um ranking), o
 * numero vem cru e a tela nao finge que ha variacao.
 *
 * NOME COM PREFIXO `Indicadores`, e nao sufixo `Detalhe`. A primeira versao
 * chamava este de `FormacaoDetalheDTO` -- nome que JA EXISTIA em AgendaDTO.kt
 * como "o detalhe de UMA formacao". Mesmo pacote, mesmo nome, e o compilador
 * escolhe uma das duas: o erro seguinte nem fala de nome, fala de "parametro
 * inexistente" em quem usava a outra, e manda procurar erro de digitacao num
 * arquivo que estava certo. O prefixo amarra o tipo a tela dona dele.
 */

// ---------------------------------------------------------------- matriculas

data class LinhaAnoSituacaoDTO(
    val ano: Int,
    val cursando: Int,
    val concluiram: Int,
    val naoConcluiram: Int,
    val transferidos: Int,
    val desistentes: Int,
    val total: Int
)

data class LinhaTurmaMatriculaDTO(
    val idTurma: Long?,
    val turma: String,
    val comunidade: String,
    val categoria: String?,
    val cursando: Int,
    val concluiram: Int,
    val desistentes: Int,
    val total: Int
)

data class IndicadoresMatriculasDTO(
    val cabecalho: String,
    val total: IndicadorDTO,
    val cursando: IndicadorDTO,
    val desistentes: IndicadorDTO,
    val concluiram: IndicadorDTO,
    /** Historico: a mesma quebra, ano a ano, para ver se a evasao e tendencia. */
    val porAno: List<LinhaAnoSituacaoDTO>,
    val porTurma: List<LinhaTurmaMatriculaDTO>,
    val avisos: List<String>
)

// ---------------------------------------------------------------- frequencia

data class LinhaTurmaFrequenciaDTO(
    val idTurma: Long,
    val turma: String,
    val comunidade: String,
    val categoria: String?,
    val exigeFrequencia: Boolean,
    val apurados: Int,
    val media: Double?,
    val regulares: Int,
    val pertoDoLimite: Int,
    val abaixo: Int,
    val encontrosFechados: Int,
    val encontrosCancelados: Int
)

data class LinhaCatequisandoFrequenciaDTO(
    val idCatequisando: Long,
    val nome: String,
    val idTurma: Long?,
    val turma: String?,
    val percentual: Double?,
    val situacao: SituacaoFrequencia,
    val presencas: Int,
    val faltas: Int,
    val justificadas: Int,
    val encontros: Int
)

data class IndicadoresFrequenciaDTO(
    val cabecalho: String,
    /** Media dos percentuais apurados. Nulo quando nada foi apurado ainda. */
    val aproveitamento: IndicadorDTO,
    val regulares: IndicadorDTO,
    /** Entre o alerta e o minimo: quem ainda da tempo de recuperar. */
    val pertoDoLimite: IndicadorDTO,
    val abaixo: IndicadorDTO,
    val minimo: Int,
    val alerta: Int,
    val turmas: List<LinhaTurmaFrequenciaDTO>,
    /** So vem preenchida com uma turma escolhida -- a paroquia inteira seriam centenas de linhas. */
    val catequisandos: List<LinhaCatequisandoFrequenciaDTO>,
    val avisos: List<String>
)

// ------------------------------------------------------------------ formacao

data class LinhaCatequistaFormacaoDTO(
    val idCatequista: Long,
    val nome: String,
    val comunidade: String,
    val formacoes: Int,
    val encontrosPossiveis: Int,
    val presencas: Int,
    val percentual: Double?,
    val atingiuMinimo: Boolean
)

data class LinhaComunidadeFormacaoDTO(
    val idComunidade: Long?,
    val nome: String,
    val catequistas: Int,
    val participaram: Int,
    val percentual: Double?
)

data class LinhaFormacaoItemDTO(
    val idFormacao: Long,
    val nome: String,
    val nivel: NivelEvento,
    val rotuloNivel: String,
    val encontrosRealizados: Int,
    val inscritos: Int,
    val participaram: Int,
    val atingiram: Int,
    val minimo: Int
)

data class IndicadoresFormacaoDTO(
    val cabecalho: String,
    val inscritos: IndicadorDTO,
    val participaram: IndicadorDTO,
    val atingiramMinimo: IndicadorDTO,
    val porNivel: List<LinhaFormacaoDTO>,
    /** Ordenado do mais presente para o menos: a pergunta e quem foi e quem nao foi. */
    val catequistas: List<LinhaCatequistaFormacaoDTO>,
    val comunidades: List<LinhaComunidadeFormacaoDTO>,
    val formacoes: List<LinhaFormacaoItemDTO>,
    val avisos: List<String>
)

// ------------------------------------------------------------------- eventos

data class LinhaEventoDTO(
    val idEvento: Long,
    val titulo: String,
    val tipo: String,
    val rotuloTipo: String,
    val nivel: String?,
    val rotuloNivel: String?,
    val data: LocalDate?,
    val situacao: String,
    val local: String?,
    val comunidade: String?,
    val turma: String?,
    val formacao: String?,
    /** Quem o evento atinge, em uma frase -- e a resposta de "para quem foi isso?". */
    val publico: String,
    /** Nulo quando aquele tipo de evento nao registra aquela presenca. */
    val catequistasPresentes: Int?,
    val catequisandosPresentes: Int?
)

data class IndicadoresEventosDTO(
    val cabecalho: String,
    val total: IndicadorDTO,
    val realizados: IndicadorDTO,
    val cancelados: IndicadorDTO,
    val porTipo: List<FatiaDTO>,
    val porNivel: List<FatiaDTO>,
    val eventos: List<LinhaEventoDTO>,
    val avisos: List<String>
)

// ------------------------------------------------------------------- opcoes

data class OpcaoTurmaDTO(
    val id: Long,
    val nome: String,
    val idComunidade: Long?
)

data class OpcaoCatequistaDTO(
    val id: Long,
    val nome: String,
    val idComunidade: Long?
)

/**
 * Tudo que as barras de filtro precisam, numa requisicao so.
 *
 * Vem com o vinculo de comunidade em turma e catequista para a tela poder
 * ENCOLHER a lista quando uma comunidade for escolhida -- select com trezentas
 * turmas de toda a paroquia nao e filtro, e um obstaculo.
 */
data class OpcoesIndicadoresCompletasDTO(
    val anos: List<Int>,
    val comunidades: List<ItemSimplesDTO>,
    val turmas: List<OpcaoTurmaDTO>,
    val catequistas: List<OpcaoCatequistaDTO>,
    val situacoesMatricula: List<OpcaoDTO>,
    val tiposEvento: List<OpcaoDTO>,
    val niveisEvento: List<OpcaoDTO>
)
