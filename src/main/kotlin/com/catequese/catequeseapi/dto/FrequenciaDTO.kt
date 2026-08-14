package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.EtapaCatecumenato
import com.catequese.catequeseapi.model.JanelaApuracao
import com.catequese.catequeseapi.model.SituacaoFrequencia
import com.catequese.catequeseapi.model.SituacaoMatricula
import java.time.LocalDate

/**
 * Um periodo apurado: o ano, um semestre ou uma etapa do catecumenato.
 *
 * A tela mostra os numeros brutos junto com o percentual de proposito. Quando
 * alguem discorda do resultado, a primeira pergunta e sempre "de quantos
 * encontros estamos falando?" -- e a resposta precisa estar na mesma linha.
 */
data class PeriodoFrequenciaDTO(
    val rotulo: String,
    val inicio: LocalDate,
    val fim: LocalDate,
    /** Encontros fechados no periodo, menos as faltas justificadas da pessoa. */
    val encontrosConsiderados: Int,
    val presencas: Int,
    val faltas: Int,
    val justificadas: Int,
    /** Nulo quando ainda nao houve encontro fechado no periodo. */
    val percentual: Double?,
    val situacao: SituacaoFrequencia,
    val minimo: Int,
    /** Este periodo ja terminou? Muda o tom do aviso: risco x resultado. */
    val encerrado: Boolean,
    /** Preenchido so no catecumenato. */
    val etapa: EtapaCatecumenato? = null,
    /** Preenchido so nas categorias apuradas por semestre. */
    val semestre: Int? = null
)

/**
 * A frequencia de um catequisando numa turma, num ano.
 *
 * `situacao` e a PIOR entre os periodos: ir bem no 2o semestre nao apaga ter
 * ficado abaixo do minimo no 1o.
 */
data class FrequenciaCatequisandoDTO(
    val idCatequisando: Long,
    val nome: String,
    val idTurma: Long?,
    val nomeTurma: String?,
    val categoria: CategoriaTurma?,
    val ano: Int,
    val dataMatricula: LocalDate?,
    val situacaoMatricula: SituacaoMatricula,
    /** Etapa em que o catecumeno esta hoje. Nulo fora do catecumenato. */
    val etapaAtual: EtapaCatecumenato? = null,
    val periodos: List<PeriodoFrequenciaDTO> = emptyList(),
    /** Do periodo corrente (ou do ultimo apurado), para caber numa coluna. */
    val percentualAtual: Double? = null,
    val situacao: SituacaoFrequencia = SituacaoFrequencia.SEM_APURACAO,
    /**
     * Regra dos adultos: quem fica abaixo de 80% no 1o semestre nao conclui a
     * catequese naquele ano. Nas demais categorias segue true ate o
     * encerramento do ano.
     */
    val podeConcluir: Boolean = true,
    /** Frases prontas para a tela, ja no tom certo. */
    val alertas: List<String> = emptyList()
)

/** Contagem por situacao, para o cabecalho da tela da turma. */
data class ResumoFrequenciaDTO(
    val total: Int,
    val regulares: Int,
    val emRisco: Int,
    val abaixoDoMinimo: Int,
    val semApuracao: Int,
    val naoSeAplica: Int
)

data class FrequenciaTurmaDTO(
    val idTurma: Long,
    val nomeTurma: String,
    val categoria: CategoriaTurma?,
    val janela: JanelaApuracao,
    val exigeFrequencia: Boolean,
    val ano: Int,
    val minimo: Int,
    val alerta: Int,
    val encontrosFechados: Int,
    val encontrosCancelados: Int,
    val encontrosAbertos: Int,
    val resumo: ResumoFrequenciaDTO,
    val linhas: List<FrequenciaCatequisandoDTO>,
    /** Avisos da turma inteira, como "turma ainda nao classificada". */
    val alertas: List<String> = emptyList()
)
