package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.EtapaCatecumenato
import java.time.LocalDate

/**
 * As regras de movimentacao do catequisando: para onde ele pode ir, e quando.
 *
 * Objeto PURO de proposito -- sem Spring, sem repositorio, sem banco. Recebe o
 * que precisa saber e devolve um veredito. Isso importa por dois motivos:
 * regra de percurso e o tipo de coisa que muda depois de uma reuniao da
 * coordenacao, e regra escondida dentro de um servico com dez dependencias e
 * regra que ninguem consegue conferir nem testar.
 *
 * O vocabulario aqui e o do dominio: "inscricao" na tela, `matricula` no
 * codigo. Sao a mesma coisa -- ver a nota em
 * docs/especificacoes/tela-turmas-e-inscricoes.md.
 */
object RegrasDeMovimentacao {

    /** Idade minima para entrar em cada percurso, em anos completos. */
    const val IDADE_EUCARISTIA = 9
    const val IDADE_CRISMA = 13
    const val IDADE_ADULTOS = 18

    /**
     * Quanto o aniversario pode atrasar em relacao a data da inscricao.
     *
     * Decisao do Gabriel: a conta e a partir da DATA DA INSCRICAO. Quem faz 9
     * anos dentro dos tres meses seguintes entra -- segurar a crianca um ano
     * inteiro por causa de algumas semanas seria desproporcional.
     *
     * Adulto tem seis meses porque o percurso e de dois anos e a maioridade
     * chega antes do fim do primeiro.
     */
    const val TOLERANCIA_MESES_INFANTIL = 3L
    const val TOLERANCIA_MESES_ADULTO = 6L

    /** As quatro etapas do catecumenato, na ordem do RICA. */
    val ETAPAS_DO_CATECUMENATO = listOf(
        EtapaCatecumenato.PRE_CATECUMENATO,
        EtapaCatecumenato.CATECUMENATO,
        EtapaCatecumenato.PURIFICACAO_ILUMINACAO,
        EtapaCatecumenato.MISTAGOGIA
    )

    data class Veredito(val permitido: Boolean, val motivo: String? = null) {
        companion object {
            val OK = Veredito(true)
            fun nao(motivo: String) = Veredito(false, motivo)
        }
    }

    /** O que o servico precisa saber sobre a turma, sem arrastar a entidade. */
    data class Percurso(
        val idTurma: Long,
        val nome: String,
        val categoria: CategoriaTurma?,
        val etapa: Int?,
        val idComunidade: Long?
    )

    // ------------------------------------------------------------------ fases

    /**
     * So Eucaristia e Crisma tem FASE.
     *
     * `CategoriaTurma.anosPrevistos` diz quantos anos o percurso dura, e nao e
     * a mesma coisa: Adultos tambem dura dois anos, mas nao se divide em 1a e
     * 2a fase -- a pessoa segue no mesmo percurso. Usar `anosPrevistos` como se
     * fosse numero de fases oferecia "2a fase" em turma que nao tem fase
     * nenhuma, e era o que confundia o cadastro.
     *
     * Tudo que mostra ou pergunta fase passa por aqui: combo, listagem,
     * progressao do encerramento. Regra em dois lugares e regra que diverge.
     */
    fun temFases(categoria: CategoriaTurma?): Boolean =
        categoria == CategoriaTurma.EUCARISTIA || categoria == CategoriaTurma.CRISMA

    /** Quantas fases o percurso tem. Zero quando ele nao se divide em fases. */
    fun quantasFases(categoria: CategoriaTurma?): Int =
        if (temFases(categoria)) categoria?.anosPrevistos ?: 0 else 0

    // ------------------------------------------------------------------ idade

    /** Quantos meses de folga o percurso aceita depois da inscricao. */
    fun toleranciaEmMeses(categoria: CategoriaTurma?): Long = when (categoria) {
        CategoriaTurma.ADULTOS, CategoriaTurma.CATECUMENATO -> TOLERANCIA_MESES_ADULTO
        else -> TOLERANCIA_MESES_INFANTIL
    }

    /**
     * Idade minima do percurso. Nulo quando o percurso nao tem exigencia.
     *
     * So a PRIMEIRA fase tem porta de idade: quem ja esta na segunda entrou
     * pela primeira, e cobrar de novo barraria quem tem aniversario tardio no
     * meio do proprio percurso.
     */
    fun idadeMinima(categoria: CategoriaTurma?, etapa: Int?): Int? = when (categoria) {
        CategoriaTurma.EUCARISTIA -> if (etapa == null || etapa <= 1) IDADE_EUCARISTIA else null
        CategoriaTurma.CRISMA -> if (etapa == null || etapa <= 1) IDADE_CRISMA else null
        CategoriaTurma.ADULTOS, CategoriaTurma.CATECUMENATO -> IDADE_ADULTOS
        else -> null
    }

    /**
     * A idade permite entrar neste percurso?
     *
     * Sem data de nascimento a regra NAO barra: cadastro antigo costuma vir sem
     * ela, e recusar a inscricao de quem tem idade certa por causa de um campo
     * em branco seria pior do que aceitar. O aviso fica na tela.
     */
    fun idadePermite(
        categoria: CategoriaTurma?,
        etapa: Int?,
        nascimento: LocalDate?,
        dataInscricao: LocalDate
    ): Veredito {
        val minima = idadeMinima(categoria, etapa) ?: return Veredito.OK
        if (nascimento == null) return Veredito.OK

        val limite = dataInscricao.plusMonths(toleranciaEmMeses(categoria))
        val fazAniversarioAte = nascimento.plusYears(minima.toLong())
        if (!fazAniversarioAte.isAfter(limite)) return Veredito.OK

        val meses = toleranciaEmMeses(categoria)
        return Veredito.nao(
            "Idade minima de $minima anos para este percurso. " +
                "Completa $minima anos em $fazAniversarioAte, depois do limite de " +
                "$limite (a inscricao mais $meses meses)."
        )
    }

    // ---------------------------------------------------------------- destino

    /**
     * O catecumeno concluiu o itinerario inteiro?
     *
     * `etapasConcluidas` sao as que ja tem data de fim. Enquanto faltar
     * qualquer uma das quatro, ele continua catecumeno -- e catequese de
     * adultos nao e continuacao do catecumenato, e outro percurso.
     */
    fun concluiuOCatecumenato(etapasConcluidas: Collection<EtapaCatecumenato>): Boolean =
        ETAPAS_DO_CATECUMENATO.all { it in etapasConcluidas }

    /**
     * Pode mover a inscricao de `origem` para `destino`?
     *
     * A regra de fundo: **transferencia e mudanca de lugar, nao de percurso.**
     * Trocar de comunidade sem trocar de fase e mudanca de endereco; trocar de
     * fase e progressao, e progressao acontece no encerramento do ano, com a
     * coordenacao revisando -- nao no meio do ano, um a um.
     *
     * As duas excecoes sao percursos que nao sao percurso: pre-catequese e
     * perseveranca preparam para o proximo passo em vez de terem etapas
     * proprias, e por isso a saida delas e uma mudanca de categoria.
     */
    fun podeMover(
        origem: Percurso,
        destino: Percurso,
        nascimento: LocalDate?,
        dataMovimentacao: LocalDate,
        etapasDoCatecumenatoConcluidas: Collection<EtapaCatecumenato> = emptyList()
    ): Veredito {
        if (origem.idTurma == destino.idTurma) {
            return Veredito.nao("A turma de destino e a mesma de origem.")
        }
        if (destino.categoria == null) {
            return Veredito.nao(
                "A turma \"${destino.nome}\" ainda nao foi classificada por categoria. " +
                    "Classifique antes de mover alguem para ela."
            )
        }

        // Catecumeno so vai para a catequese de adultos depois dos quatro ritos.
        if (origem.categoria == CategoriaTurma.CATECUMENATO &&
            destino.categoria == CategoriaTurma.ADULTOS &&
            !concluiuOCatecumenato(etapasDoCatecumenatoConcluidas)
        ) {
            val faltam = ETAPAS_DO_CATECUMENATO.filterNot { it in etapasDoCatecumenatoConcluidas }
            return Veredito.nao(
                "Ainda esta no catecumenato: falta concluir " +
                    faltam.joinToString(", ") { rotuloEtapa(it) } +
                    ". A catequese de adultos nao e continuacao do catecumenato."
            )
        }

        val destinoPermitido = when (origem.categoria) {
            // Preparam para o passo seguinte: a saida e mudanca de categoria.
            CategoriaTurma.PRE_CATEQUESE ->
                destino.categoria == CategoriaTurma.EUCARISTIA && (destino.etapa ?: 1) == 1
            CategoriaTurma.PERSEVERANCA ->
                destino.categoria == CategoriaTurma.CRISMA && (destino.etapa ?: 1) == 1
            // Catecumeno que ja cumpriu os quatro ritos: o bloqueio acima ja
            // devolveu quem nao cumpriu, entao chegar aqui significa que ele
            // pode seguir para a catequese de adultos. Sem esta linha, o caso
            // caia na regra geral e era barrado por "categoria diferente" --
            // ou seja, quem completou o itinerario ficava preso nele.
            CategoriaTurma.CATECUMENATO ->
                destino.categoria == CategoriaTurma.ADULTOS ||
                    (destino.categoria == CategoriaTurma.CATECUMENATO &&
                        origem.etapa == destino.etapa)
            // Todo o resto: mesmo percurso, mesma fase, outra comunidade.
            else -> origem.categoria == destino.categoria && origem.etapa == destino.etapa
        }

        if (!destinoPermitido) {
            return Veredito.nao(motivoDoDestinoRecusado(origem, destino))
        }

        // Mesma categoria e mesma fase: so faz sentido se mudar de comunidade.
        val mesmoPercurso = origem.categoria == destino.categoria && origem.etapa == destino.etapa
        if (mesmoPercurso && origem.idComunidade != null &&
            origem.idComunidade == destino.idComunidade
        ) {
            return Veredito.nao(
                "As duas turmas sao da mesma comunidade e da mesma fase. " +
                    "Transferencia serve para mudar de comunidade; para trocar de turma " +
                    "dentro da mesma comunidade, cancele a inscricao e inscreva na outra."
            )
        }

        return idadePermite(destino.categoria, destino.etapa, nascimento, dataMovimentacao)
    }

    private fun motivoDoDestinoRecusado(origem: Percurso, destino: Percurso): String = when {
        origem.categoria == CategoriaTurma.PRE_CATEQUESE ->
            "Quem esta na pre-catequese so pode ir para a 1a fase da Eucaristia."
        origem.categoria == CategoriaTurma.PERSEVERANCA ->
            "Quem esta na perseveranca so pode ir para a 1a fase da Crisma."
        origem.categoria != destino.categoria ->
            "Nao da para mover de ${rotulo(origem.categoria)} para ${rotulo(destino.categoria)}: " +
                "transferencia muda de comunidade, nao de percurso."
        else ->
            "As fases sao diferentes (${fase(origem.etapa)} para ${fase(destino.etapa)}). " +
                "A mudanca de fase acontece no encerramento do ano, nao por transferencia."
    }

    // ------------------------------------------------------------- progressao

    /**
     * Para onde vai no encerramento do ano.
     *
     * Nulo = concluiu o percurso e sai. A progressao NAO atravessa categoria:
     * ir da pre-catequese para a Eucaristia e uma decisao com porta de idade,
     * feita na tela de movimentacao, e nao algo que o sistema faz sozinho na
     * virada do ano.
     */
    fun proximaFase(categoria: CategoriaTurma?, etapa: Int?): Int? {
        // Percurso sem fase nao promove ninguem de fase. Antes esta conta usava
        // `anosPrevistos` direto, e propunha "2a fase" para Adultos.
        val fases = quantasFases(categoria)
        if (fases == 0) return null
        val atual = etapa ?: 1
        return if (atual < fases) atual + 1 else null
    }

    fun temProximaFase(categoria: CategoriaTurma?, etapa: Int?): Boolean =
        proximaFase(categoria, etapa) != null

    // ----------------------------------------------------------------- texto

    private fun fase(etapa: Int?): String = etapa?.let { "${it}a fase" } ?: "fase nao definida"

    private fun rotulo(categoria: CategoriaTurma?): String = when (categoria) {
        CategoriaTurma.PRE_CATEQUESE -> "Pre-catequese"
        CategoriaTurma.EUCARISTIA -> "Eucaristia"
        CategoriaTurma.CRISMA -> "Crisma"
        CategoriaTurma.ADULTOS -> "Adultos"
        CategoriaTurma.CATECUMENATO -> "Catecumenato"
        CategoriaTurma.PERSEVERANCA -> "Perseveranca"
        null -> "turma sem categoria"
    }

    fun rotuloEtapa(etapa: EtapaCatecumenato): String = when (etapa) {
        EtapaCatecumenato.PRE_CATECUMENATO -> "pre-catecumenato"
        EtapaCatecumenato.CATECUMENATO -> "catecumenato"
        EtapaCatecumenato.PURIFICACAO_ILUMINACAO -> "purificacao e iluminacao"
        EtapaCatecumenato.MISTAGOGIA -> "mistagogia"
    }
}
