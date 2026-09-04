package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.EtapaCatecumenato
import com.catequese.catequeseapi.model.SituacaoFrequencia
import com.catequese.catequeseapi.model.SituacaoMatricula

/**
 * O que aconteceria com uma matricula se o ano fosse encerrado agora.
 *
 * A previa existe porque encerrar o ano e a operacao mais destrutiva do
 * sistema: ela decide quem concluiu a catequese. Ninguem deveria descobrir o
 * resultado depois de aplicado.
 */
data class PreviaEncerramentoDTO(
    val idMatricula: Long,
    val idCatequisando: Long,
    val nome: String,
    val idTurma: Long?,
    val nomeTurma: String?,
    val categoria: CategoriaTurma?,
    val ano: Int,
    val situacaoAtual: SituacaoMatricula,
    /** Nula quando o sistema nao tem base para decidir. */
    val situacaoProposta: SituacaoMatricula?,
    val percentual: Double?,
    val situacaoFrequencia: SituacaoFrequencia,
    /** Quantos anos desta categoria a pessoa ja concluiu, sem contar este. */
    val anosCumpridos: Int,
    val anosPrevistos: Int?,
    /** True quando este ano fecha o percurso inteiro da categoria. */
    val concluiPercurso: Boolean,
    /**
     * A fase do percurso para a qual esta pessoa vai no ano que vem.
     *
     * Nula quando o percurso termina aqui. E isto que responde "a fase muda
     * sozinha na virada do ano": muda, mas passando por esta previa -- ninguem
     * sobe de fase sem o coordenador ver, e quem nao fechou a frequencia
     * aparece com a proposta de NAO_CONCLUIDO ao lado.
     */
    val proximaFase: Int? = null,
    val etapaAtual: EtapaCatecumenato? = null,
    /** Para onde a etapa do catecumeno iria. Nula se nao ha promocao. */
    val proximaEtapa: EtapaCatecumenato? = null,
    /**
     * False quando falta base para decidir -- turma sem categoria, ou sem
     * nenhum encontro apurado. Estes NAO entram no lote.
     */
    val aplicavel: Boolean,
    /** Frase pronta explicando a decisao, para a tela e para a conferencia. */
    val motivo: String
)

data class ResumoEncerramentoDTO(
    val total: Int,
    val concluem: Int,
    val naoConcluem: Int,
    val semBase: Int,
    val concluemPercurso: Int,
    val promocoesDeEtapa: Int
)

data class PreviaAnoDTO(
    val ano: Int,
    val resumo: ResumoEncerramentoDTO,
    val linhas: List<PreviaEncerramentoDTO>,
    val alertas: List<String> = emptyList()
)

/**
 * Aplicacao do encerramento.
 *
 * As matriculas vao explicitas em `idsMatricula` de proposito: um "aplicar
 * tudo" implicito faria o administrador encerrar linhas que ele nem chegou a
 * ler. Aqui ele escolhe, e o que nao foi escolhido nao e tocado.
 */
data class AplicarEncerramentoDTO(
    val ano: Int? = null,
    val idsMatricula: List<Long> = emptyList(),
    /** Se true, avanca tambem a etapa dos catecumenos aprovados. */
    val promoverEtapas: Boolean = false
)

data class ResultadoEncerramentoDTO(
    val ano: Int,
    val matriculasAtualizadas: Int,
    val etapasPromovidas: Int,
    val ignoradas: List<String> = emptyList()
)
