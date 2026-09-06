package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.NivelEvento
import java.time.LocalDate

/**
 * Estado do aproveitamento de um catequista no ano -- ver tela-catequistas.md
 * regra 6. Enum, e nao um texto de cor solto, porque e ele quem decide o selo
 * (`.status ok/warning/error/neutro`) que a tela mostra.
 *
 * NEUTRO cobre dois casos diferentes de proposito: "ainda sem apuracao" (nao
 * houve encontro realizado) e "abaixo do minimo mas longe do fechamento" (o
 * ano ainda nao acabou, entao ainda nao e para alarmar). Os dois merecem o
 * mesmo tratamento visual discreto -- so ATENCAO e ABAIXO precisam gritar.
 */
enum class EstadoCurriculo(val rotulo: String) {
    VERDE("Dentro do mínimo"),
    AMARELO("Abaixo do mínimo — ano de formação fechando"),
    VERMELHO("Abaixo do mínimo — ano de formação encerrado"),
    NEUTRO("Sem apuração ainda")
}

/** Uma linha da lista de Consultar Catequistas. */
data class CurriculoResumoDTO(
    val idCatequista: Long,
    val nome: String,
    val comunidade: String?,
    val ano: Int,

    /** Agregado do ano: soma de presenca/encontro de TODAS as formacoes, antes de dividir. */
    val percentual: Int?,
    val minimoAgregado: Int,
    val estado: EstadoCurriculo,
    val estadoRotulo: String
)

/** Um encontro dentro do curriculo -- o que aconteceu, nao so a data. */
data class CurriculoEncontroDTO(
    val data: LocalDate?,

    /** PRESENTE / FALTA / JUSTIFICADA -- nunca nulo: encontro realizado sem marcacao e FALTA. */
    val situacao: String,
    val justificativa: String?
)

/** Uma formacao em que o catequista esta inscrito, com seus encontros. */
data class CurriculoFormacaoDTO(
    val idFormacao: Long,
    val nome: String,
    val nivel: NivelEvento,
    val nivelRotulo: String,
    val ano: Int?,

    /** Minimo DESTA formacao (pode ser diferente do agregado da tela). */
    val percentualMinimo: Int,
    val percentual: Int?,

    /** O "checkbox": ja tem o conhecimento desta formacao especifica. */
    val atingiuMinimo: Boolean,
    val encontros: List<CurriculoEncontroDTO>
)

/** O curriculo completo de um catequista num ano -- a tela de detalhe. */
data class CurriculoCatequistaDTO(
    val idCatequista: Long,
    val nome: String,
    val comunidade: String?,
    val ano: Int,
    val percentualAgregado: Int?,
    val minimoAgregado: Int,
    val estado: EstadoCurriculo,
    val estadoRotulo: String,
    val diocesana: List<CurriculoFormacaoDTO>,
    val regional: List<CurriculoFormacaoDTO>,
    val paroquial: List<CurriculoFormacaoDTO>
)

/**
 * Uma linha da aba "Formações" (histórico completo, todos os anos) -- ao
 * contrário do resumo e das colunas por nível, que só olham o ano corrente
 * (regra 2), esta lista existe justamente para o filtro de ano/mês pedido
 * ter o que filtrar. Uma linha por ENCONTRO realizado, não por formação --
 * é o "foi ou não foi" que o Gabriel pediu para ver em detalhe.
 */
data class CurriculoHistoricoEncontroDTO(
    val idFormacao: Long,
    val formacaoNome: String,
    val nivel: NivelEvento,
    val nivelRotulo: String,
    val ano: Int?,
    val data: LocalDate?,

    /** PRESENTE / FALTA / JUSTIFICADA -- mesma regra do curriculo: sem marcação é FALTA. */
    val situacao: String,
    val justificativa: String?
)
