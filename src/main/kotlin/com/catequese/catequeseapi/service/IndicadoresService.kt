package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.DirecaoBoa
import com.catequese.catequeseapi.dto.EventosIndicadorDTO
import com.catequese.catequeseapi.dto.FatiaDTO
import com.catequese.catequeseapi.dto.FrequenciaIndicadorDTO
import com.catequese.catequeseapi.dto.IndicadorDTO
import com.catequese.catequeseapi.dto.IndicadoresDTO
import com.catequese.catequeseapi.dto.ItemSimplesDTO
import com.catequese.catequeseapi.dto.LinhaComunidadeDTO
import com.catequese.catequeseapi.dto.LinhaFormacaoDTO
import com.catequese.catequeseapi.dto.MovimentoDTO
import com.catequese.catequeseapi.dto.OpcoesIndicadoresDTO
import com.catequese.catequeseapi.dto.PontoAnoDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.model.Comunidade
import com.catequese.catequeseapi.model.Formacao
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.model.TipoEvento
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.FormacaoInscritoRepository
import com.catequese.catequeseapi.repository.FormacaoRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import com.catequese.catequeseapi.repository.PresencaFormacaoRepository
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
 * contas. Por isso este servico nao escreve nada e nao decide nada -- ele
 * compoe FrequenciaService e os repositorios e devolve tudo ja comparado.
 *
 * Regra de acesso: so coordenador paroquial. A restricao esta no SecurityConfig
 * (`/api/indicadores/**`), e conferida de novo aqui -- permissao e de dados, nao
 * de tela, e este servico pode acabar sendo chamado de outro lugar.
 */
@Service
class IndicadoresService(
    private val matriculaRepository: MatriculaRepository,
    private val turmaRepository: TurmaRepository,
    private val comunidadeRepository: ComunidadeRepository,
    private val turmaCatequistaRepository: TurmaCatequistaRepository,
    private val eventoRepository: EventoRepository,
    private val formacaoRepository: FormacaoRepository,
    private val formacaoInscritoRepository: FormacaoInscritoRepository,
    private val presencaFormacaoRepository: PresencaFormacaoRepository,
    private val frequenciaService: FrequenciaService,
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
        val ativas: List<Matricula>
            get() = matriculas.filter { it.situacao == SituacaoMatricula.CURSANDO }
    }

    fun opcoes(): OpcoesIndicadoresDTO {
        exigirCoordenadorParoquial()
        val anos = matriculaRepository.findAll()
            .map { it.ano }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
        val hoje = LocalDate.now().year
        return OpcoesIndicadoresDTO(
            anos = (if (anos.contains(hoje)) anos else listOf(hoje) + anos),
            comunidades = comunidadeRepository.findAll()
                .filter { it.ativo }
                .sortedBy { it.nome }
                .map { ItemSimplesDTO(it.idComunidade, it.nome) }
        )
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
            "Catequisandos", atual.ativas.size, base?.ativas?.size, DirecaoBoa.MAIOR
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
            evolucaoCatequisandos = evolucao(anosComDado, ano, todas, turmas, idComunidade) { it.ativas.size },
            evolucaoCatequistas = evolucao(anosComDado, ano, todas, turmas, idComunidade) { it.catequistas.size },
            movimento = movimento(ano, anoBase, corte, todas, turmas, idComunidade),
            situacaoMatriculas = situacoes(atual, base),
            porComunidade = porComunidade(atual, base, comunidades),
            formacoes = formacoes(ano, anoBase, corte),
            frequencia = frequencia(atual, base),
            eventos = eventos(ano, anoBase, corte, idComunidade)
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

    private fun situacoes(atual: Recorte, base: Recorte?): List<FatiaDTO> =
        SituacaoMatricula.entries.map { s ->
            FatiaDTO(
                chave = s.name,
                rotulo = rotuloSituacao(s),
                valor = atual.matriculas.count { it.situacao == s }.toDouble(),
                base = base?.matriculas?.count { it.situacao == s }?.toDouble()
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

    private fun formacoes(ano: Int, anoBase: Int?, corte: LocalDate): List<LinhaFormacaoDTO> {
        val niveis = listOf(NivelEvento.DIOCESANO, NivelEvento.REGIONAL, NivelEvento.PAROQUIAL)
        val doAno = formacaoRepository.findByAnoOrderByNomeAsc(ano)
        val doBase = anoBase?.let {
            formacaoRepository.findByAnoOrderByNomeAsc(it)
        }.orEmpty()

        return niveis.map { nivel ->
            val atual = apurarFormacoes(doAno.filter { it.nivel == nivel }, corte)
            val base =
                if (anoBase == null) null
                else apurarFormacoes(doBase.filter { it.nivel == nivel }, corteEquivalente(anoBase, corte))

            LinhaFormacaoDTO(
                nivel = nivel,
                rotulo = nivel.rotulo,
                formacoes = atual.formacoes,
                encontrosRealizados = atual.encontros,
                inscritos = IndicadorDTO.de("Inscritos", atual.inscritos, base?.inscritos, DirecaoBoa.MAIOR),
                participaram = IndicadorDTO.de(
                    "Participaram", atual.participaram, base?.participaram, DirecaoBoa.MAIOR,
                    detalhe = "com ao menos uma presença"
                ),
                atingiramMinimo = IndicadorDTO.de(
                    "Atingiram o mínimo", atual.atingiram, base?.atingiram, DirecaoBoa.MAIOR
                ),
                taxaParticipacao = IndicadorDTO.de(
                    "Participação", atual.taxa ?: 0.0, base?.taxa, DirecaoBoa.MAIOR, percentual = true
                ),
                minimo = atual.minimo
            )
        }
    }

    private data class ApuracaoFormacao(
        val formacoes: Int,
        val encontros: Int,
        val inscritos: Int,
        val participaram: Int,
        val atingiram: Int,
        val taxa: Double?,
        val minimo: Int
    )

    private fun apurarFormacoes(
        formacoes: List<Formacao>,
        corte: LocalDate
    ): ApuracaoFormacao {
        val inscritos = mutableSetOf<Long>()
        val participaram = mutableSetOf<Long>()
        val atingiram = mutableSetOf<Long>()
        var encontros = 0
        var minimo = CalculoFrequencia.MINIMO_PADRAO

        formacoes.forEach { f ->
            minimo = f.percentualMinimo
            val daFormacao = formacaoInscritoRepository.findByIdFormacao(f.idFormacao)
                .map { it.idCatequista }
            inscritos.addAll(daFormacao)

            // So encontro REALIZADO conta -- mesma regra do encontro fechado da
            // turma. Previsto do resto do ano nao pode virar falta de ninguem.
            val realizados = eventoRepository.findByIdFormacaoOrderByDataInicioAsc(f.idFormacao)
                .filter {
                    it.situacao == SituacaoEvento.REALIZADO &&
                        it.dataInicio != null &&
                        !it.dataInicio!!.isAfter(corte)
                }
            encontros += realizados.size
            if (realizados.isEmpty()) return@forEach

            val presencas = presencaFormacaoRepository
                .findByIdEventoIn(realizados.map { it.idEvento })
                .filter { it.situacao == SituacaoPresenca.PRESENTE }

            val porCatequista = presencas.groupBy { it.idCatequista }
            porCatequista.forEach { (idCatequista, lista) ->
                participaram.add(idCatequista)
                val percentual = lista.size.toDouble() / realizados.size * 100.0
                if (percentual >= f.percentualMinimo) atingiram.add(idCatequista)
            }
        }

        return ApuracaoFormacao(
            formacoes = formacoes.size,
            encontros = encontros,
            inscritos = inscritos.size,
            participaram = participaram.size,
            atingiram = atingiram.size,
            taxa = if (inscritos.isEmpty()) null else participaram.size.toDouble() / inscritos.size * 100.0,
            minimo = minimo
        )
    }

    private fun frequencia(atual: Recorte, base: Recorte?): FrequenciaIndicadorDTO {
        val a = apurarFrequencia(atual)
        val b = base?.let { apurarFrequencia(it) }
        return FrequenciaIndicadorDTO(
            media = IndicadorDTO.de("Frequência média", a.media ?: 0.0, b?.media, DirecaoBoa.MAIOR, percentual = true),
            abaixoDoMinimo = IndicadorDTO.de("Abaixo do mínimo", a.abaixo, b?.abaixo, DirecaoBoa.MENOR),
            emRisco = IndicadorDTO.de("Em risco", a.emRisco, b?.emRisco, DirecaoBoa.MENOR),
            turmasApuradas = a.apuradas,
            turmasSemApuracao = a.semApuracao,
            turmasNaoSeAplica = a.naoSeAplica,
            minimo = CalculoFrequencia.MINIMO_PADRAO
        )
    }

    private data class ApuracaoFrequencia(
        val media: Double?,
        val abaixo: Int,
        val emRisco: Int,
        val apuradas: Int,
        val semApuracao: Int,
        val naoSeAplica: Int
    )

    /**
     * Percorre turma a turma reusando o FrequenciaService. As cinco regras de
     * contagem vivem la (so encontro fechado conta, cancelado nao entra,
     * justificada sai da conta, realizado sem marcacao e falta, e sem encontro
     * apurado o percentual e NULO, nao zero). Recalcular aqui seria duplicar
     * regra que um dia divergiria da tela de Frequencia.
     *
     * Custo: uma apuracao por turma, vezes dois anos. Se passar de ~1s, o
     * caminho e @Query com GROUP BY no repositorio -- nunca cache no navegador,
     * porque relatorio com numero velho e pior que relatorio lento.
     */
    private fun apurarFrequencia(r: Recorte): ApuracaoFrequencia {
        var soma = 0.0
        var quantos = 0
        var abaixo = 0
        var emRisco = 0
        var apuradas = 0
        var semApuracao = 0
        var naoSeAplica = 0

        r.turmasPorId.values.forEach { turma ->
            if (turma.categoria != null && !turma.categoria!!.exigeFrequencia) {
                naoSeAplica++
                return@forEach
            }
            val dto = runCatching { frequenciaService.daTurma(turma.idTurma, r.ano) }.getOrNull()
                ?: return@forEach

            abaixo += dto.resumo.abaixoDoMinimo
            emRisco += dto.resumo.emRisco

            val percentuais = dto.linhas.mapNotNull { it.percentualAtual }
            if (percentuais.isEmpty()) {
                semApuracao++
            } else {
                apuradas++
                soma += percentuais.sum()
                quantos += percentuais.size
            }
        }

        return ApuracaoFrequencia(
            media = if (quantos == 0) null else soma / quantos,
            abaixo = abaixo,
            emRisco = emRisco,
            apuradas = apuradas,
            semApuracao = semApuracao,
            naoSeAplica = naoSeAplica
        )
    }

    private fun eventos(
        ano: Int,
        anoBase: Int?,
        corte: LocalDate,
        idComunidade: Long?
    ): EventosIndicadorDTO {
        fun doAno(a: Int, ate: LocalDate) =
            eventoRepository.findNoPeriodo(LocalDate.of(a, 1, 1), ate)
                .filter { idComunidade == null || it.idComunidade == idComunidade }

        val atual = doAno(ano, corte)
        val base = anoBase?.let { doAno(it, corteEquivalente(it, corte)) }

        return EventosIndicadorDTO(
            total = IndicadorDTO.de("Eventos", atual.size, base?.size, DirecaoBoa.NEUTRA),
            realizados = IndicadorDTO.de(
                "Realizados",
                atual.count { it.situacao == SituacaoEvento.REALIZADO },
                base?.count { it.situacao == SituacaoEvento.REALIZADO },
                DirecaoBoa.NEUTRA
            ),
            cancelados = IndicadorDTO.de(
                "Cancelados",
                atual.count { it.situacao == SituacaoEvento.CANCELADO },
                base?.count { it.situacao == SituacaoEvento.CANCELADO },
                DirecaoBoa.MENOR
            ),
            porTipo = TipoEvento.entries.map { t ->
                FatiaDTO(
                    chave = t.name,
                    rotulo = t.rotulo,
                    valor = atual.count { it.tipo == t }.toDouble(),
                    base = base?.count { it.tipo == t }?.toDouble()
                )
            }
        )
    }

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

    private fun rotuloSituacao(s: SituacaoMatricula): String = when (s) {
        SituacaoMatricula.CURSANDO -> "Cursando"
        SituacaoMatricula.CONCLUIDO -> "Concluiu"
        SituacaoMatricula.NAO_CONCLUIDO -> "Não concluiu"
        SituacaoMatricula.TRANSFERIDO -> "Transferido"
        SituacaoMatricula.DESISTENTE -> "Desistente"
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

