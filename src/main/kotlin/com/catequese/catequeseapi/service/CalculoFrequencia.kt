package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.SituacaoFrequencia
import java.time.LocalDate

/**
 * A conta da frequencia, sem banco de dados no meio.
 *
 * Fica isolada de proposito: e a regra que o usuario mais precisa confiar, e
 * assim ela pode ser coberta por teste de unidade de verdade, sem subir Spring
 * nem preparar dados. Quem busca encontro e presenca e o FrequenciaService.
 */
object CalculoFrequencia {

    const val MINIMO_PADRAO = 80
    const val ALERTA_PADRAO = 85

    /** Janela de tempo em que a frequencia sera apurada. */
    data class Periodo(
        val inicio: LocalDate,
        val fim: LocalDate,
        val rotulo: String
    )

    data class Resultado(
        val encontrosConsiderados: Int,
        val presencas: Int,
        val faltas: Int,
        val justificadas: Int,
        val percentual: Double?,
        val situacao: SituacaoFrequencia
    )

    fun anoCivil(ano: Int) = Periodo(
        inicio = LocalDate.of(ano, 1, 1),
        fim = LocalDate.of(ano, 12, 31),
        rotulo = "Ano de $ano"
    )

    /** semestre 1 = janeiro a junho; 2 = julho a dezembro. */
    fun semestre(ano: Int, semestre: Int): Periodo = if (semestre == 1) {
        Periodo(LocalDate.of(ano, 1, 1), LocalDate.of(ano, 6, 30), "1o semestre de $ano")
    } else {
        Periodo(LocalDate.of(ano, 7, 1), LocalDate.of(ano, 12, 31), "2o semestre de $ano")
    }

    fun semestreDe(data: LocalDate): Int = if (data.monthValue <= 6) 1 else 2

    /**
     * Quem se matriculou depois do inicio do periodo so responde a partir dali.
     * Era pedido explicito: quem entrou em abril nao pode ser cobrado pelos
     * encontros de fevereiro e marco.
     */
    fun inicioEfetivo(periodo: Periodo, dataMatricula: LocalDate?): LocalDate {
        if (dataMatricula == null) return periodo.inicio
        return if (dataMatricula.isAfter(periodo.inicio)) dataMatricula else periodo.inicio
    }

    /**
     * @param encontrosFechados encontros do periodo que de fato aconteceram.
     *        Cancelados nao entram aqui: nao viram falta de ninguem.
     * @param presencas quantos comparecimentos.
     * @param justificadas faltas com motivo aceito. SAEM DA CONTA, em vez de
     *        contar contra -- foi a regra escolhida. O denominador encolhe.
     */
    fun apurar(
        encontrosFechados: Int,
        presencas: Int,
        justificadas: Int,
        minimo: Int = MINIMO_PADRAO,
        alerta: Int = ALERTA_PADRAO
    ): Resultado {
        val considerados = (encontrosFechados - justificadas).coerceAtLeast(0)
        val faltas = (considerados - presencas).coerceAtLeast(0)

        // Sem encontro apurado nao existe percentual. Devolver 0% seria injusto
        // (ninguem faltou) e devolver 100% seria mentira (ninguem compareceu).
        if (considerados == 0) {
            return Resultado(0, presencas, 0, justificadas, null, SituacaoFrequencia.SEM_APURACAO)
        }

        val percentual = (presencas.toDouble() / considerados) * 100
        val situacao = when {
            percentual < minimo -> SituacaoFrequencia.ABAIXO_DO_MINIMO
            percentual < alerta -> SituacaoFrequencia.EM_RISCO
            else -> SituacaoFrequencia.REGULAR
        }

        return Resultado(
            encontrosConsiderados = considerados,
            presencas = presencas,
            faltas = faltas,
            justificadas = justificadas,
            percentual = arredondar(percentual),
            situacao = situacao
        )
    }

    /** Uma casa decimal basta e evita 83.33333333333333 na tela. */
    fun arredondar(valor: Double): Double = Math.round(valor * 10.0) / 10.0
}
