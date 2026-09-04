package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.DirecaoBoa
import com.catequese.catequeseapi.dto.IndicadorDTO
import com.catequese.catequeseapi.dto.IndicadoresDTO
import com.catequese.catequeseapi.dto.LinhaComunidadeDTO
import com.catequese.catequeseapi.dto.MovimentoDTO
import com.catequese.catequeseapi.dto.PontoAnoDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.model.Comunidade
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.FormacaoRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import com.catequese.catequeseapi.repository.TurmaCatequistaRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * O relatorio da catequese: como o ano esta indo, comparado com o anterior.
 *
 * Nao confundir com as telas de gestao. Ali o coordenador OPERA (corrige
 * chamada, classifica turma); aqui ele so LE, para reuniao e prestacao de
 * contas. Por isso este servico nao escreve nada e nao decide nada.
 *
 * Guarda so o RESUMO GERAL. Matriculas, frequencia, formacao e eventos ganharam
 * tela propria, com filtros proprios, no IndicadoresDetalheService -- e a
 * apuracao de frequencia, que percorre turma a turma em dois anos, e justamente
 * a mais cara. Mante-la fora daqui e o que deixa a pagina de entrada rapida.
 *
 * Regra de acesso: so coordenador paroquial. A restricao esta no SecurityConfig
 * (no matcher de `/api/indicadores`), e conferida de novo aqui -- permissao e de
 * dados, nao de tela, e este servico pode acabar sendo chamado de outro lugar.
 */
@Service
class IndicadoresService(
    private val matriculaRepository: MatriculaRepository,
    private val turmaRepository: TurmaRepository,
    private val comunidadeRepository: ComunidadeRepository,
    private val turmaCatequistaRepository: TurmaCatequistaRepository,
    private val eventoRepository: EventoRepository,
    private val formacaoRepository: FormacaoRepository,
    private val escopo: EscopoAcessoService
) {

    companion object {
        /** Quantos anos a linha de evolucao mostra, quando existirem. */
        const val ANOS_NA_EVOLUCAO = 5
        const val SEM_COMUNIDADE = "Sem comunidade definida"
        private val DIA = DateTimeFormatter.ofPattern("dd/MM")
    }

    /**
     * Um ano ja recortado: as matriculas que valem, as turmas envolvidas e os
     * catequistas que atuaram. Montado uma vez por ano apurado e reusado por
     * todos os blocos -- assim os numeros da tela falam todos do mesmo recorte.
     */
    private data class Recorte(
        val ano: Int,
        val matriculas: List<Matricula>,
        val turmasPorId: Map<Long, Turma>,
        val catequistasPorComunidade: Map<Long?, Set<Long>>,
        val catequistas: Set<Long>
    ) {
        /**
         * Quem foi catequisando naquele ano -- TODAS as matriculas, qualquer
         * que tenha sido o desfecho.
         *
         * A primeira versao contava so `CURSANDO`, e isso zerava todo ano
         * fechado: no encerramento cada matricula vira CONCLUIDO,
         * NAO_CONCLUIDO, DESISTENTE ou TRANSFERIDO, e nenhuma continua
         * cursando. O ano em curso mostrava 300 e o anterior mostrava 0 --
         * a comparacao dizia "novo", como se a catequese tivesse comecado
         * este ano. Quem concluiu a Crisma em 2025 FOI catequisando em 2025.
         *
         * A quebra por situacao continua existindo: e a tela de Matriculas.
         */
        val ativas: List<Matricula>
            get() = matriculas
    }

    @Transactional(readOnly = true)
    fun relatorio(anoPedido: Int?, idComunidade: Long?): IndicadoresDTO {
        exigirCoordenadorParoquial()

        val hoje = LocalDate.now()
        val ano = anoPedido ?: hoje.year
        val anoEmCurso = ano == hoje.year

        // O corte da comparacao. Com o ano em curso, comparar contra o ano
        // anterior INTEIRO mostraria uma queda que nao existe -- em setembro o
        // ano passado teve doze meses e este teve nove.
        val corte = if (anoEmCurso) hoje else LocalDate.of(ano, 12, 31)

        val todas = matriculaRepository.findAll()
        val anosComDado = todas.map { it.ano }.filter { it > 0 }.distinct().sorted()
        val anoBase = anosComDado.filter { it < ano }.maxOrNull()

        val turmas = turmaRepository.findAll().associateBy { it.idTurma }
        val comunidades = comunidadeRepository.findAll().associateBy { it.idComunidade }

        val atual = recortar(ano, todas, turmas, corte, idComunidade)
        val base = anoBase?.let { recortar(it, todas, turmas, corteEquivalente(it, corte), idComunidade) }

        val avisos = montarAvisos(atual, comunidades.keys)

        // --- os cartoes do topo -------------------------------------------
        val catequisandos = IndicadorDTO.de(
            "Catequisandos", atual.ativas.size, base?.ativas?.size, DirecaoBoa.MAIOR,
            detalhe = "matriculados no ano, qualquer que tenha sido o desfecho"
        )
        val pessoas = atual.ativas.mapNotNull { it.catequisando?.idCatequisando }.distinct().size
        val pessoasDistintas =
            if (pessoas == atual.ativas.size) null
            else IndicadorDTO.de(
                "Pessoas distintas", pessoas,
                base?.ativas?.mapNotNull { it.catequisando?.idCatequisando }?.distinct()?.size,
                DirecaoBoa.MAIOR,
                detalhe = "alguém matriculado em duas turmas conta duas matrículas"
            )
        val catequistas = IndicadorDTO.de(
            "Catequistas", atual.catequistas.size, base?.catequistas?.size, DirecaoBoa.MAIOR
        )

        // Duas contagens baratas (uma consulta cada) que valem como manchete:
        // dizem se o ano teve movimento, e levam para as telas de detalhe.
        val formacoesNoAno = IndicadorDTO.de(
            "Formações", formacaoRepository.findByAnoOrderByNomeAsc(ano).size,
            anoBase?.let { formacaoRepository.findByAnoOrderByNomeAsc(it).size },
            DirecaoBoa.NEUTRA, detalhe = "trilhas de formação de catequista"
        )
        val eventosNoAno = IndicadorDTO.de(
            "Eventos", eventosDoAno(ano, corte, idComunidade),
            anoBase?.let { eventosDoAno(it, corteEquivalente(it, corte), idComunidade) },
            DirecaoBoa.NEUTRA, detalhe = "na agenda, até a data"
        )

        return IndicadoresDTO(
            ano = ano,
            anoBase = anoBase,
            ateODia = corte,
            anoEmCurso = anoEmCurso,
            idComunidade = idComunidade,
            nomeComunidade = idComunidade?.let { comunidades[it]?.nome },
            cabecalho = cabecalho(ano, anoBase, corte, anoEmCurso, idComunidade?.let { comunidades[it]?.nome }),
            avisos = avisos,
            catequisandos = catequisandos,
            pessoasDistintas = pessoasDistintas,
            catequistas = catequistas,
            formacoesNoAno = formacoesNoAno,
            eventosNoAno = eventosNoAno,
            evolucaoCatequisandos = evolucao(anosComDado, ano, todas, turmas, idComunidade) { it.ativas.size },
            evolucaoCatequistas = evolucao(anosComDado, ano, todas, turmas, idComunidade) { it.catequistas.size },
            movimento = movimento(ano, anoBase, corte, todas, turmas, idComunidade),
            porComunidade = porComunidade(atual, base, comunidades)
        )
    }

    // ------------------------------------------------------------------
    // Recorte
    // ------------------------------------------------------------------

    /** O mesmo dia e mes, no ano anterior. 29/02 vira 28/02 em ano comum. */
    private fun corteEquivalente(ano: Int, corte: LocalDate): LocalDate {
        val ultimoDia = LocalDate.of(ano, corte.monthValue, 1).lengthOfMonth()
        return LocalDate.of(ano, corte.monthValue, minOf(corte.dayOfMonth, ultimoDia))
    }

    private fun recortar(
        ano: Int,
        todas: List<Matricula>,
        turmas: Map<Long, Turma>,
        corte: LocalDate,
        idComunidade: Long?
    ): Recorte {
        val doAno = todas.filter { it.ano == ano && dentroDoCorte(it, corte) }
        val filtradas =
            if (idComunidade == null) doAno
            else doAno.filter { comunidadeDe(it, turmas) == idComunidade }

        // As turmas do ano saem das proprias matriculas, e nao de `turma.ano`:
        // turma antiga costuma ter o ano em branco, e o vinculo que nunca
        // mente e a matricula.
        val idsTurma = filtradas.mapNotNull { it.turma?.idTurma }.toSet()
        val turmasDoAno = idsTurma.mapNotNull { turmas[it] }.associateBy { it.idTurma }

        // Catequista do ano = quem atuou numa turma daquele ano. Usar
        // `usuario.ativo` daria o numero de HOJE nos dois lados da comparacao,
        // e a comparacao viraria enfeite: os dois anos dariam sempre igual.
        val porComunidade = mutableMapOf<Long?, MutableSet<Long>>()
        turmasDoAno.values.forEach { turma ->
            val equipe = turmaCatequistaRepository.findByIdTurma(turma.idTurma)
                .map { it.idCatequista }
                .toMutableSet()
            turma.catequista?.idCatequista?.let { equipe.add(it) }
            porComunidade.getOrPut(turma.idComunidade) { mutableSetOf() }.addAll(equipe)
        }

        return Recorte(
            ano = ano,
            matriculas = filtradas,
            turmasPorId = turmasDoAno,
            catequistasPorComunidade = porComunidade,
            // Distinto na paroquia: quem atua em duas comunidades e uma pessoa
            // so. Por isso a soma das comunidades pode passar deste total.
            catequistas = porComunidade.values.flatten().toSet()
        )
    }

    /**
     * Matricula sem data entra sempre. Chutar janeiro criaria um pico que nunca
     * existiu, e descartar sumiria com gente que esta matriculada de verdade.
     * O aviso na tela conta quantas sao.
     */
    private fun dentroDoCorte(m: Matricula, corte: LocalDate): Boolean =
        m.dataMatricula == null || !m.dataMatricula!!.isAfter(corte)

    private fun comunidadeDe(m: Matricula, turmas: Map<Long, Turma>): Long? =
        m.turma?.idTurma?.let { turmas[it]?.idComunidade }

    // ------------------------------------------------------------------
    // Blocos
    // ------------------------------------------------------------------

    private fun evolucao(
        anosComDado: List<Int>,
        ano: Int,
        todas: List<Matricula>,
        turmas: Map<Long, Turma>,
        idComunidade: Long?,
        medir: (Recorte) -> Int
    ): List<PontoAnoDTO> =
        anosComDado.filter { it <= ano }
            .takeLast(ANOS_NA_EVOLUCAO)
            .map { a ->
                // Cada ano fechado conta o ano inteiro; so o ano em curso e
                // cortado em hoje, senao a ultima coluna pareceria uma queda.
                val fim = if (a == LocalDate.now().year) LocalDate.now() else LocalDate.of(a, 12, 31)
                PontoAnoDTO(a, medir(recortar(a, todas, turmas, fim, idComunidade)).toDouble())
            }

    /**
     * Quem entrou, quem saiu e quem ficou -- do ano pedido E do ano anterior,
     * para que ate estes cartoes tenham comparacao.
     *
     * A primeira versao mandava `base = null` aqui, e a tela escrevia "primeiro
     * ano apurado" embaixo de "Entraram 61" num ano que tinha base. Era mentira:
     * o certo nao e dizer que nao ha ano anterior, e sim apurar o fluxo dele.
     */
    private data class Fluxo(
        val entraram: Int,
        val permaneceram: Int,
        val concluiram: Int,
        val abandonaram: Int,
        val transferidos: Int,
        val saldo: Int,
        val retencao: Double?
    )

    private fun fluxoDe(
        ano: Int,
        corte: LocalDate,
        todas: List<Matricula>,
        turmas: Map<Long, Turma>,
        idComunidade: Long?
    ): Fluxo {
        val doAno = recortar(ano, todas, turmas, corte, idComunidade)
        val anterior = recortar(
            ano - 1, todas, turmas, corteEquivalente(ano - 1, corte), idComunidade
        )

        val agora = doAno.matriculas.mapNotNull { it.catequisando?.idCatequisando }.toSet()
        val antes = anterior.matriculas.mapNotNull { it.catequisando?.idCatequisando }.toSet()

        val entraram = agora - antes
        val sairam = antes - agora
        val permaneceram = agora intersect antes

        // Como cada pessoa saiu. Transferido mudou de turma ou de paroquia: nao
        // e perda da catequese, e sai dos dois lados da conta.
        val porPessoa = anterior.matriculas
            .filter { it.catequisando?.idCatequisando in sairam }
            .groupBy { it.catequisando!!.idCatequisando }

        var concluiram = 0
        var abandonaram = 0
        var transferidos = 0
        sairam.forEach { id ->
            val delas = porPessoa[id].orEmpty().map { it.situacao }
            when {
                delas.contains(SituacaoMatricula.CONCLUIDO) -> concluiram++
                delas.isNotEmpty() && delas.all { it == SituacaoMatricula.TRANSFERIDO } -> transferidos++
                else -> abandonaram++
            }
        }

        // A retencao desconta quem concluiu do denominador. Sem isso, a paroquia
        // que forma muita gente apareceria com retencao ruim justamente por
        // estar indo bem.
        val denominador = antes.size - concluiram - transferidos
        return Fluxo(
            entraram = entraram.size,
            permaneceram = permaneceram.size,
            concluiram = concluiram,
            abandonaram = abandonaram,
            transferidos = transferidos,
            saldo = entraram.size - sairam.size,
            retencao = if (denominador > 0) permaneceram.size.toDouble() / denominador * 100.0 else null
        )
    }

    private fun movimento(
        ano: Int,
        anoBase: Int?,
        corte: LocalDate,
        todas: List<Matricula>,
        turmas: Map<Long, Turma>,
        idComunidade: Long?
    ): MovimentoDTO {
        val agora = fluxoDe(ano, corte, todas, turmas, idComunidade)
        val antes = anoBase?.let {
            fluxoDe(it, corteEquivalente(it, corte), todas, turmas, idComunidade)
        }

        return MovimentoDTO(
            entraram = IndicadorDTO.de(
                "Entraram", agora.entraram, antes?.entraram, DirecaoBoa.MAIOR,
                detalhe = "sem matrícula no ano anterior"
            ),
            permaneceram = IndicadorDTO.de(
                "Permaneceram", agora.permaneceram, antes?.permaneceram, DirecaoBoa.MAIOR
            ),
            concluiram = IndicadorDTO.de(
                "Concluíram", agora.concluiram, antes?.concluiram, DirecaoBoa.MAIOR,
                detalhe = "saíram por terem terminado o percurso"
            ),
            abandonaram = IndicadorDTO.de(
                "Abandonaram", agora.abandonaram, antes?.abandonaram, DirecaoBoa.MENOR,
                detalhe = "saíram sem terminar"
            ),
            transferidos = agora.transferidos,
            saldo = agora.saldo,
            retencao = IndicadorDTO.de(
                "Retenção", agora.retencao ?: 0.0, antes?.retencao, DirecaoBoa.MAIOR,
                percentual = true, detalhe = "de quem podia continuar"
            )
        )
    }

    private fun porComunidade(
        atual: Recorte,
        base: Recorte?,
        comunidades: Map<Long, Comunidade>
    ): List<LinhaComunidadeDTO> {
        val idsAtual = atual.ativas.map { comunidadeDeRecorte(it, atual) }.distinct()
        val idsBase = base?.let { b -> b.ativas.map { comunidadeDeRecorte(it, b) } }.orEmpty().distinct()
        val ids = (idsAtual + idsBase).distinct()

        return ids.map { id ->
            LinhaComunidadeDTO(
                idComunidade = id,
                nome = id?.let { comunidades[it]?.nome ?: "Comunidade $it" } ?: SEM_COMUNIDADE,
                catequisandos = IndicadorDTO.de(
                    "Catequisandos",
                    atual.ativas.count { comunidadeDeRecorte(it, atual) == id },
                    base?.let { b -> b.ativas.count { comunidadeDeRecorte(it, b) == id } },
                    DirecaoBoa.MAIOR
                ),
                catequistas = IndicadorDTO.de(
                    "Catequistas",
                    atual.catequistasPorComunidade[id].orEmpty().size,
                    base?.catequistasPorComunidade?.get(id)?.size,
                    DirecaoBoa.MAIOR
                )
            )
        }.sortedByDescending { it.catequisandos.valor }
    }

    private fun comunidadeDeRecorte(m: Matricula, r: Recorte): Long? =
        m.turma?.idTurma?.let { r.turmasPorId[it]?.idComunidade }

    /** Contagem simples de eventos do periodo -- o detalhe vive na tela de Eventos. */
    private fun eventosDoAno(ano: Int, ate: LocalDate, idComunidade: Long?): Int =
        eventoRepository.findNoPeriodo(LocalDate.of(ano, 1, 1), ate)
            .count { idComunidade == null || it.idComunidade == idComunidade }

    // ------------------------------------------------------------------
    // Texto
    // ------------------------------------------------------------------

    private fun cabecalho(
        ano: Int,
        anoBase: Int?,
        corte: LocalDate,
        anoEmCurso: Boolean,
        comunidade: String?
    ): String {
        val partes = mutableListOf("Catequese em $ano")
        partes += if (anoBase == null) "primeiro ano apurado" else "comparado com $anoBase"
        if (anoEmCurso) partes += "até ${corte.format(DIA)} nos dois anos"
        partes += comunidade ?: "paróquia inteira"
        return partes.joinToString(" · ")
    }

    private fun montarAvisos(atual: Recorte, comunidadesConhecidas: Set<Long>): List<String> {
        val avisos = mutableListOf<String>()

        val turmasSemComunidade = atual.turmasPorId.values.filter { it.idComunidade == null }
        if (turmasSemComunidade.isNotEmpty()) {
            val matriculas = atual.ativas.count { comunidadeDeRecorte(it, atual) == null }
            avisos += "${turmasSemComunidade.size} turma(s) ainda sem comunidade definida — " +
                "$matriculas matrícula(s) aparecem em \"$SEM_COMUNIDADE\". " +
                "Classifique em Turmas e matrículas."
        }

        val orfas = atual.turmasPorId.values
            .mapNotNull { it.idComunidade }
            .filter { it !in comunidadesConhecidas }
            .distinct()
        if (orfas.isNotEmpty()) {
            avisos += "${orfas.size} turma(s) apontam para uma comunidade que não existe mais."
        }

        val semData = atual.matriculas.count { it.dataMatricula == null }
        if (semData > 0) {
            avisos += "$semData matrícula(s) sem data. Elas entram na contagem dos dois " +
                "anos, porque descartar sumiria com gente matriculada de verdade."
        }

        val semCategoria = atual.turmasPorId.values.count { it.categoria == null }
        if (semCategoria > 0) {
            avisos += "$semCategoria turma(s) sem categoria: a frequência delas não é apurada."
        }

        return avisos
    }

    /**
     * Conferido aqui alem do SecurityConfig: permissao e de dados, nao de tela.
     * Se um dia alguem montar outra rota que chame este servico, a regra vem
     * junto em vez de ficar para tras no arquivo de configuracao.
     */
    private fun exigirCoordenadorParoquial() {
        if (!escopo.ehAdmin()) {
            throw AcessoNegadoException(
                "O painel de indicadores é exclusivo do coordenador paroquial."
            )
        }
    }
}

