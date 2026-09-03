package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.NivelEvento
import java.time.LocalDate

/**
 * A regra que governa esta tela inteira: numero sem comparacao nao e indicador.
 *
 * Por isso nenhum valor sai do servidor sozinho -- sai sempre com a base do ano
 * anterior ao lado e com a comparacao JA RESOLVIDA aqui. A tela nao calcula
 * variacao: se o JS recalculasse, o numero da tela e o do papel divergiriam no
 * dia em que alguem mexesse num dos dois lados.
 */
enum class SituacaoComparacao {
    /** Tem base e da para comparar: variacao e percentual preenchidos. */
    COMPARAVEL,

    /** A base era zero e agora existe algo. "+100%" seria mentira. */
    NOVO,

    /** Primeiro ano apurado: nao existe ano anterior no sistema. */
    SEM_BASE,

    /**
     * Base pequena demais para percentual. De 1 para 2 daria "+100%", que tem
     * cara de tendencia e e ruido. Mostra-se so a diferenca absoluta.
     */
    BASE_PEQUENA
}

/**
 * Para onde o numero deveria ir. Verde e vermelho so onde a direcao e
 * inequivoca -- numero de eventos subir nao e bom nem ruim, e pintar de verde
 * sugeriria uma meta que ninguem definiu.
 */
enum class DirecaoBoa { MAIOR, MENOR, NEUTRA }

data class IndicadorDTO(
    val rotulo: String,
    val valor: Double,
    val base: Double?,
    val variacao: Double?,
    val variacaoPercentual: Double?,
    val situacao: SituacaoComparacao,
    val direcaoBoa: DirecaoBoa,
    /** Muda so a formatacao na tela: 79,4% em vez de 79. */
    val percentual: Boolean = false,
    /** Detalhe opcional embaixo do numero ("dos quais 12 concluiram"). */
    val detalhe: String? = null
) {
    companion object {
        /**
         * Abaixo deste piso a variacao sai so em numero absoluto.
         * Dez e o menor valor em que 1 de diferenca ainda cabe num percentual
         * honesto (10%).
         */
        const val PISO_PARA_PERCENTUAL = 10.0

        fun de(
            rotulo: String,
            valor: Number,
            base: Number?,
            direcaoBoa: DirecaoBoa = DirecaoBoa.MAIOR,
            percentual: Boolean = false,
            detalhe: String? = null
        ): IndicadorDTO {
            val v = valor.toDouble()
            val b = base?.toDouble()

            if (b == null) {
                return IndicadorDTO(
                    rotulo, v, null, null, null,
                    SituacaoComparacao.SEM_BASE, direcaoBoa, percentual, detalhe
                )
            }

            val delta = v - b

            // Base zero: se surgiu algo, e "novo"; se continua zero, nao houve
            // movimento nenhum e comparar zero com zero e legitimo.
            if (b == 0.0) {
                val situacao =
                    if (v == 0.0) SituacaoComparacao.COMPARAVEL else SituacaoComparacao.NOVO
                return IndicadorDTO(
                    rotulo, v, 0.0, delta, null, situacao, direcaoBoa, percentual, detalhe
                )
            }

            if (b < PISO_PARA_PERCENTUAL) {
                return IndicadorDTO(
                    rotulo, v, b, delta, null,
                    SituacaoComparacao.BASE_PEQUENA, direcaoBoa, percentual, detalhe
                )
            }

            return IndicadorDTO(
                rotulo, v, b, delta, delta / b * 100.0,
                SituacaoComparacao.COMPARAVEL, direcaoBoa, percentual, detalhe
            )
        }
    }
}

/** Um ponto da linha de evolucao. */
data class PontoAnoDTO(val ano: Int, val valor: Double)

/** Uma fatia nomeada (tipo de evento, situacao de matricula). */
data class FatiaDTO(
    val chave: String,
    val rotulo: String,
    val valor: Double,
    val base: Double?
)

/**
 * Quem entrou, quem saiu e quem ficou.
 *
 * Comparar dois totais nao responde isso: 300 num ano e 300 no outro pode ser
 * a mesma gente ou uma turma inteira trocada.
 */
data class MovimentoDTO(
    val entraram: IndicadorDTO,
    val permaneceram: IndicadorDTO,
    /** Saiu porque terminou o percurso. Resultado bom. */
    val concluiram: IndicadorDTO,
    /** Saiu sem terminar. E este o numero que preocupa. */
    val abandonaram: IndicadorDTO,
    /** Mudou de turma/paroquia: nao e saida da catequese, sai dos dois lados. */
    val transferidos: Int,
    val saldo: Int,
    val retencao: IndicadorDTO
)

data class LinhaComunidadeDTO(
    val idComunidade: Long?,
    val nome: String,
    val catequisandos: IndicadorDTO,
    val catequistas: IndicadorDTO
)

data class LinhaFormacaoDTO(
    val nivel: NivelEvento,
    val rotulo: String,
    val formacoes: Int,
    val encontrosRealizados: Int,
    val inscritos: IndicadorDTO,
    val participaram: IndicadorDTO,
    val atingiramMinimo: IndicadorDTO,
    val taxaParticipacao: IndicadorDTO,
    val minimo: Int
)

data class FrequenciaIndicadorDTO(
    val media: IndicadorDTO,
    val abaixoDoMinimo: IndicadorDTO,
    val emRisco: IndicadorDTO,
    val turmasApuradas: Int,
    val turmasSemApuracao: Int,
    val turmasNaoSeAplica: Int,
    val minimo: Int
)

data class EventosIndicadorDTO(
    val total: IndicadorDTO,
    val realizados: IndicadorDTO,
    val cancelados: IndicadorDTO,
    val porTipo: List<FatiaDTO>
)

/** As opcoes da barra: dois controles, so. Relatorio nao e painel de controle. */
data class OpcoesIndicadoresDTO(
    val anos: List<Int>,
    val comunidades: List<ItemSimplesDTO>
)

data class ItemSimplesDTO(val id: Long, val nome: String)

data class IndicadoresDTO(
    val ano: Int,
    val anoBase: Int?,
    /**
     * Ate que dia os dois anos foram contados. Com o ano em curso, comparar com
     * o ano anterior INTEIRO mostraria uma queda que nao existe.
     */
    val ateODia: LocalDate,
    val anoEmCurso: Boolean,
    val idComunidade: Long?,
    val nomeComunidade: String?,
    /** Frase pronta do cabecalho. E ela que vai impressa. */
    val cabecalho: String,
    /** Ressalvas do dado (turma sem comunidade, matricula sem data). */
    val avisos: List<String>,
    val catequisandos: IndicadorDTO,
    val pessoasDistintas: IndicadorDTO?,
    val catequistas: IndicadorDTO,
    val evolucaoCatequisandos: List<PontoAnoDTO>,
    val evolucaoCatequistas: List<PontoAnoDTO>,
    val movimento: MovimentoDTO,
    val situacaoMatriculas: List<FatiaDTO>,
    val porComunidade: List<LinhaComunidadeDTO>,
    val formacoes: List<LinhaFormacaoDTO>,
    val frequencia: FrequenciaIndicadorDTO,
    val eventos: EventosIndicadorDTO
)
