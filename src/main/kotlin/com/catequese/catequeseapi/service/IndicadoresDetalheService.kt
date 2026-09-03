package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.DirecaoBoa
import com.catequese.catequeseapi.dto.EventosDetalheDTO
import com.catequese.catequeseapi.dto.FatiaDTO
import com.catequese.catequeseapi.dto.FormacaoDetalheDTO
import com.catequese.catequeseapi.dto.FrequenciaDetalheDTO
import com.catequese.catequeseapi.dto.IndicadorDTO
import com.catequese.catequeseapi.dto.ItemSimplesDTO
import com.catequese.catequeseapi.dto.LinhaAnoSituacaoDTO
import com.catequese.catequeseapi.dto.LinhaCatequisandoFrequenciaDTO
import com.catequese.catequeseapi.dto.LinhaCatequistaFormacaoDTO
import com.catequese.catequeseapi.dto.LinhaComunidadeFormacaoDTO
import com.catequese.catequeseapi.dto.LinhaEventoDTO
import com.catequese.catequeseapi.dto.LinhaFormacaoDTO
import com.catequese.catequeseapi.dto.LinhaFormacaoItemDTO
import com.catequese.catequeseapi.dto.LinhaTurmaFrequenciaDTO
import com.catequese.catequeseapi.dto.LinhaTurmaMatriculaDTO
import com.catequese.catequeseapi.dto.MatriculasDetalheDTO
import com.catequese.catequeseapi.dto.OpcaoCatequistaDTO
import com.catequese.catequeseapi.dto.OpcaoDTO
import com.catequese.catequeseapi.dto.OpcaoTurmaDTO
import com.catequese.catequeseapi.dto.OpcoesIndicadoresCompletasDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.model.Comunidade
import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.model.TipoEvento
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.CatequistaRepository
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.EncontroRepository
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.FormacaoInscritoRepository
import com.catequese.catequeseapi.repository.FormacaoRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import com.catequese.catequeseapi.repository.PresencaFormacaoRepository
import com.catequese.catequeseapi.repository.PresencaRepository
import com.catequese.catequeseapi.repository.TurmaCatequistaRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * As telas de detalhe do relatorio: matriculas, frequencia, formacao e eventos.
 *
 * Separado do IndicadoresService de proposito. Aquele responde uma pergunta so
 * ("como o ano esta indo") e por isso cabe numa chamada; estes sao quatro
 * assuntos com filtros proprios, e juntar tudo faria a tela buscar dez vezes
 * mais dado do que a pessoa esta olhando.
 *
 * Exclusivo do coordenador paroquial, conferido aqui alem do SecurityConfig.
 */
@Service
class IndicadoresDetalheService(
    private val matriculaRepository: MatriculaRepository,
    private val turmaRepository: TurmaRepository,
    private val comunidadeRepository: ComunidadeRepository,
    private val catequistaRepository: CatequistaRepository,
    private val turmaCatequistaRepository: TurmaCatequistaRepository,
    private val encontroRepository: EncontroRepository,
    private val presencaRepository: PresencaRepository,
    private val eventoRepository: EventoRepository,
    private val formacaoRepository: FormacaoRepository,
    private val formacaoInscritoRepository: FormacaoInscritoRepository,
    private val presencaFormacaoRepository: PresencaFormacaoRepository,
    private val frequenciaService: FrequenciaService,
    private val configuracaoService: ConfiguracaoService,
    private val escopo: EscopoAcessoService
) {

    companion object {
        const val SEM_COMUNIDADE = "Sem comunidade definida"
        const val SEM_TURMA = "Sem turma"
    }

    // ------------------------------------------------------------------ opcoes

    @Transactional(readOnly = true)
    fun opcoes(): OpcoesIndicadoresCompletasDTO {
        exigir()
        val anos = matriculaRepository.findAll().map { it.ano }.filter { it > 0 }
            .distinct().sortedDescending()
        val hoje = LocalDate.now().year

        return OpcoesIndicadoresCompletasDTO(
            anos = if (anos.contains(hoje)) anos else listOf(hoje) + anos,
            comunidades = comunidadeRepository.findAll().filter { it.ativo }
                .sortedBy { it.nome }
                .map { ItemSimplesDTO(it.idComunidade, it.nome) },
            turmas = turmaRepository.findAll().sortedBy { it.nome }
                .map { OpcaoTurmaDTO(it.idTurma, it.nome, it.idComunidade) },
            // A comunidade do catequista sai das turmas em que ele atua: e o
            // unico vinculo que existe hoje. Quem atua em duas fica na primeira
            // para efeito de filtro, e aparece nas duas nas contagens.
            catequistas = catequistaRepository.findAll().filter { it.ativo }
                .sortedBy { it.nome }
                .map { OpcaoCatequistaDTO(it.idCatequista, it.nome, comunidadeDoCatequista(it.idCatequista)) },
            situacoesMatricula = SituacaoMatricula.entries.map { OpcaoDTO(it.name, rotuloSituacao(it)) },
            tiposEvento = TipoEvento.entries.map { OpcaoDTO(it.name, it.rotulo) },
            niveisEvento = NivelEvento.entries.map { OpcaoDTO(it.name, it.rotulo) }
        )
    }

    // -------------------------------------------------------------- matriculas

    @Transactional(readOnly = true)
    fun matriculas(
        ano: Int?,
        idComunidade: Long?,
        idTurma: Long?,
        situacao: SituacaoMatricula?
    ): MatriculasDetalheDTO {
        exigir()
        val hoje = LocalDate.now()
        val anoAlvo = ano ?: hoje.year
        val turmas = turmaRepository.findAll().associateBy { it.idTurma }
        val comunidades = comunidadeRepository.findAll().associateBy { it.idComunidade }
        val todas = matriculaRepository.findAll()

        fun recorte(a: Int) = todas.filter { m ->
            m.ano == a &&
                (idComunidade == null || comunidadeDe(m, turmas) == idComunidade) &&
                (idTurma == null || m.turma?.idTurma == idTurma) &&
                (situacao == null || m.situacao == situacao)
        }

        val doAno = recorte(anoAlvo)
        val anosComDado = todas.map { it.ano }.filter { it > 0 }.distinct().sorted()
        val anoBase = anosComDado.filter { it < anoAlvo }.maxOrNull()
        val base = anoBase?.let { recorte(it) }

        fun conta(lista: List<Matricula>?, s: SituacaoMatricula) = lista?.count { it.situacao == s }

        val porAno = anosComDado.takeLast(6).map { a ->
            val lista = recorte(a)
            LinhaAnoSituacaoDTO(
                ano = a,
                cursando = lista.count { it.situacao == SituacaoMatricula.CURSANDO },
                concluiram = lista.count { it.situacao == SituacaoMatricula.CONCLUIDO },
                naoConcluiram = lista.count { it.situacao == SituacaoMatricula.NAO_CONCLUIDO },
                transferidos = lista.count { it.situacao == SituacaoMatricula.TRANSFERIDO },
                desistentes = lista.count { it.situacao == SituacaoMatricula.DESISTENTE },
                total = lista.size
            )
        }

        val porTurma = doAno.groupBy { it.turma?.idTurma }
            .map { (id, lista) ->
                val turma = id?.let { turmas[it] }
                LinhaTurmaMatriculaDTO(
                    idTurma = id,
                    turma = turma?.nome ?: SEM_TURMA,
                    comunidade = nomeComunidade(turma?.idComunidade, comunidades),
                    categoria = turma?.categoria?.name,
                    cursando = lista.count { it.situacao == SituacaoMatricula.CURSANDO },
                    concluiram = lista.count { it.situacao == SituacaoMatricula.CONCLUIDO },
                    desistentes = lista.count { it.situacao == SituacaoMatricula.DESISTENTE },
                    total = lista.size
                )
            }
            .sortedByDescending { it.total }

        val avisos = mutableListOf<String>()
        val semComunidade = doAno.count { comunidadeDe(it, turmas) == null }
        if (semComunidade > 0) {
            avisos += "$semComunidade matrícula(s) em turma sem comunidade definida."
        }

        return MatriculasDetalheDTO(
            cabecalho = cabecalho(
                "Matrículas", anoAlvo, anoBase,
                nomeComunidade(idComunidade, comunidades).takeIf { idComunidade != null },
                idTurma?.let { turmas[it]?.nome },
                situacao?.let { rotuloSituacao(it) }
            ),
            total = IndicadorDTO.de("Matrículas", doAno.size, base?.size, DirecaoBoa.MAIOR),
            cursando = IndicadorDTO.de(
                "Cursando", conta(doAno, SituacaoMatricula.CURSANDO) ?: 0,
                conta(base, SituacaoMatricula.CURSANDO), DirecaoBoa.MAIOR
            ),
            desistentes = IndicadorDTO.de(
                "Desistentes", conta(doAno, SituacaoMatricula.DESISTENTE) ?: 0,
                conta(base, SituacaoMatricula.DESISTENTE), DirecaoBoa.MENOR
            ),
            concluiram = IndicadorDTO.de(
                "Concluíram", conta(doAno, SituacaoMatricula.CONCLUIDO) ?: 0,
                conta(base, SituacaoMatricula.CONCLUIDO), DirecaoBoa.MAIOR
            ),
            porAno = porAno,
            porTurma = porTurma,
            avisos = avisos
        )
    }

    // -------------------------------------------------------------- frequencia

    @Transactional(readOnly = true)
    fun frequencia(ano: Int?, idComunidade: Long?, idTurma: Long?): FrequenciaDetalheDTO {
        exigir()
        val anoAlvo = ano ?: LocalDate.now().year
        val turmas = turmaRepository.findAll().associateBy { it.idTurma }
        val comunidades = comunidadeRepository.findAll().associateBy { it.idComunidade }
        val todas = matriculaRepository.findAll()

        val alerta = configuracaoService.percentualAlerta()
        val minimo = CalculoFrequencia.MINIMO_PADRAO

        // As turmas do ano saem das matriculas: `turma.ano` costuma estar em
        // branco nas turmas antigas, e a matricula nunca mente sobre o ano.
        fun turmasDoAno(a: Int): List<Turma> = todas
            .filter { it.ano == a }
            .mapNotNull { it.turma?.idTurma }
            .distinct()
            .mapNotNull { turmas[it] }
            .filter { idComunidade == null || it.idComunidade == idComunidade }
            .filter { idTurma == null || it.idTurma == idTurma }

        val linhasTurma = mutableListOf<LinhaTurmaFrequenciaDTO>()
        val linhasCatequisando = mutableListOf<LinhaCatequisandoFrequenciaDTO>()
        var soma = 0.0
        var apurados = 0
        var regulares = 0
        var perto = 0
        var abaixo = 0

        turmasDoAno(anoAlvo).forEach { turma ->
            val dto = runCatching { frequenciaService.daTurma(turma.idTurma, anoAlvo) }.getOrNull()
                ?: return@forEach
            val percentuais = dto.linhas.mapNotNull { it.percentualAtual }

            soma += percentuais.sum()
            apurados += percentuais.size
            regulares += dto.resumo.regulares
            perto += dto.resumo.emRisco
            abaixo += dto.resumo.abaixoDoMinimo

            linhasTurma += LinhaTurmaFrequenciaDTO(
                idTurma = turma.idTurma,
                turma = turma.nome,
                comunidade = nomeComunidade(turma.idComunidade, comunidades),
                categoria = turma.categoria?.name,
                exigeFrequencia = dto.exigeFrequencia,
                apurados = percentuais.size,
                media = if (percentuais.isEmpty()) null else percentuais.average(),
                regulares = dto.resumo.regulares,
                pertoDoLimite = dto.resumo.emRisco,
                abaixo = dto.resumo.abaixoDoMinimo,
                encontrosFechados = dto.encontrosFechados,
                encontrosCancelados = dto.encontrosCancelados
            )

            // A lista pessoa a pessoa so vem com uma turma escolhida. Sem o
            // filtro seriam centenas de linhas que ninguem consegue ler, e a
            // apuracao de cada uma tem custo.
            if (idTurma != null) {
                dto.linhas.forEach { linha ->
                    val periodo = linha.periodos.lastOrNull()
                    linhasCatequisando += LinhaCatequisandoFrequenciaDTO(
                        idCatequisando = linha.idCatequisando,
                        nome = linha.nome,
                        idTurma = linha.idTurma,
                        turma = linha.nomeTurma,
                        percentual = linha.percentualAtual,
                        situacao = linha.situacao,
                        presencas = periodo?.presencas ?: 0,
                        faltas = periodo?.faltas ?: 0,
                        justificadas = periodo?.justificadas ?: 0,
                        encontros = periodo?.encontrosConsiderados ?: 0
                    )
                }
            }
        }

        // Base de comparacao: a mesma conta no ano anterior.
        val anoBase = todas.map { it.ano }.filter { it in 1..(anoAlvo - 1) }.maxOrNull()
        var mediaBase: Double? = null
        var abaixoBase: Int? = null
        var pertoBase: Int? = null
        var regularesBase: Int? = null
        if (anoBase != null) {
            var somaB = 0.0
            var apuradosB = 0
            var abaixoB = 0
            var pertoB = 0
            var regularesB = 0
            turmasDoAno(anoBase).forEach { turma ->
                val dto = runCatching { frequenciaService.daTurma(turma.idTurma, anoBase) }.getOrNull()
                    ?: return@forEach
                val p = dto.linhas.mapNotNull { it.percentualAtual }
                somaB += p.sum(); apuradosB += p.size
                abaixoB += dto.resumo.abaixoDoMinimo
                pertoB += dto.resumo.emRisco
                regularesB += dto.resumo.regulares
            }
            mediaBase = if (apuradosB == 0) null else somaB / apuradosB
            abaixoBase = abaixoB; pertoBase = pertoB; regularesBase = regularesB
        }

        val avisos = mutableListOf<String>()
        val semCategoria = linhasTurma.count { it.categoria == null }
        if (semCategoria > 0) {
            avisos += "$semCategoria turma(s) sem categoria: a frequência delas não é apurada."
        }
        if (idTurma == null) {
            avisos += "Escolha uma turma para ver a frequência de cada catequisando."
        }

        return FrequenciaDetalheDTO(
            cabecalho = cabecalho(
                "Frequência", anoAlvo, anoBase,
                nomeComunidade(idComunidade, comunidades).takeIf { idComunidade != null },
                idTurma?.let { turmas[it]?.nome },
                null
            ),
            aproveitamento = IndicadorDTO.de(
                "Aproveitamento médio", if (apurados == 0) 0.0 else soma / apurados,
                mediaBase, DirecaoBoa.MAIOR, percentual = true,
                detalhe = if (apurados == 0) "nenhum encontro fechado ainda" else "$apurados catequisandos apurados"
            ),
            regulares = IndicadorDTO.de("Regulares", regulares, regularesBase, DirecaoBoa.MAIOR,
                detalhe = "no mínimo $minimo%"),
            pertoDoLimite = IndicadorDTO.de("Perto do limite", perto, pertoBase, DirecaoBoa.MENOR,
                detalhe = "entre $alerta% e $minimo% — ainda dá para recuperar"),
            abaixo = IndicadorDTO.de("Abaixo do mínimo", abaixo, abaixoBase, DirecaoBoa.MENOR,
                detalhe = "abaixo de $minimo%"),
            minimo = minimo,
            alerta = alerta,
            turmas = linhasTurma.sortedWith(compareBy(nullsLast<Double>()) { it.media }),
            catequisandos = linhasCatequisando.sortedWith(compareBy(nullsLast<Double>()) { it.percentual }),
            avisos = avisos
        )
    }

    // ---------------------------------------------------------------- formacao

    @Transactional(readOnly = true)
    fun formacao(
        ano: Int?,
        nivel: NivelEvento?,
        idComunidade: Long?,
        idCatequista: Long?
    ): FormacaoDetalheDTO {
        exigir()
        val anoAlvo = ano ?: LocalDate.now().year
        val comunidades = comunidadeRepository.findAll().associateBy { it.idComunidade }
        val nomes = catequistaRepository.findAll().associateBy { it.idCatequista }

        val formacoes = formacaoRepository.findByAnoOrderByNomeAsc(anoAlvo)
            .filter { nivel == null || it.nivel == nivel }

        // Presenca por catequista, somada em todas as formacoes do recorte.
        data class Acumulado(var formacoes: Int = 0, var possiveis: Int = 0, var presencas: Int = 0,
                             var atingiu: Int = 0)
        val porCatequista = mutableMapOf<Long, Acumulado>()
        val itens = mutableListOf<LinhaFormacaoItemDTO>()

        formacoes.forEach { f ->
            val inscritos = formacaoInscritoRepository.findByIdFormacao(f.idFormacao)
                .map { it.idCatequista }
            val realizados = eventoRepository.findByIdFormacaoOrderByDataInicioAsc(f.idFormacao)
                .filter { it.situacao == SituacaoEvento.REALIZADO }
            val presencas = if (realizados.isEmpty()) emptyList()
            else presencaFormacaoRepository.findByIdEventoIn(realizados.map { it.idEvento })
                .filter { it.situacao == SituacaoPresenca.PRESENTE }
            val presencasPorCatequista = presencas.groupBy { it.idCatequista }

            inscritos.forEach { id ->
                val acumulado = porCatequista.getOrPut(id) { Acumulado() }
                acumulado.formacoes += 1
                acumulado.possiveis += realizados.size
                val minhas = presencasPorCatequista[id].orEmpty().size
                acumulado.presencas += minhas
                if (realizados.isNotEmpty() &&
                    minhas.toDouble() / realizados.size * 100.0 >= f.percentualMinimo
                ) acumulado.atingiu += 1
            }

            itens += LinhaFormacaoItemDTO(
                idFormacao = f.idFormacao,
                nome = f.nome,
                nivel = f.nivel,
                rotuloNivel = f.nivel.rotulo,
                encontrosRealizados = realizados.size,
                inscritos = inscritos.size,
                participaram = presencasPorCatequista.keys.count { it in inscritos },
                atingiram = inscritos.count { id ->
                    realizados.isNotEmpty() &&
                        presencasPorCatequista[id].orEmpty().size.toDouble() /
                        realizados.size * 100.0 >= f.percentualMinimo
                },
                minimo = f.percentualMinimo
            )
        }

        val linhasCatequista = porCatequista
            .map { (id, a) ->
                LinhaCatequistaFormacaoDTO(
                    idCatequista = id,
                    nome = nomes[id]?.nome ?: "Catequista $id",
                    comunidade = nomeComunidade(comunidadeDoCatequista(id), comunidades),
                    formacoes = a.formacoes,
                    encontrosPossiveis = a.possiveis,
                    presencas = a.presencas,
                    percentual = if (a.possiveis == 0) null else a.presencas.toDouble() / a.possiveis * 100.0,
                    atingiuMinimo = a.atingiu > 0 && a.atingiu == a.formacoes
                )
            }
            .filter { idCatequista == null || it.idCatequista == idCatequista }
            .filter { linha ->
                idComunidade == null || comunidadeDoCatequista(linha.idCatequista) == idComunidade
            }
            // Do mais presente para o menos: a pergunta e quem foi e quem nao foi.
            .sortedWith(compareByDescending<LinhaCatequistaFormacaoDTO> { it.percentual ?: -1.0 }
                .thenBy { it.nome })

        val linhasComunidade = linhasCatequista
            .groupBy { it.comunidade }
            .map { (nome, lista) ->
                val participaram = lista.count { (it.presencas) > 0 }
                LinhaComunidadeFormacaoDTO(
                    idComunidade = comunidades.values.firstOrNull { it.nome == nome }?.idComunidade,
                    nome = nome,
                    catequistas = lista.size,
                    participaram = participaram,
                    percentual = if (lista.isEmpty()) null else participaram.toDouble() / lista.size * 100.0
                )
            }
            .sortedByDescending { it.percentual ?: -1.0 }

        val inscritosTotal = linhasCatequista.size
        val participaramTotal = linhasCatequista.count { it.presencas > 0 }
        val atingiramTotal = linhasCatequista.count { it.atingiuMinimo }

        val avisos = mutableListOf<String>()
        if (formacoes.isEmpty()) avisos += "Nenhuma formação cadastrada em $anoAlvo com este filtro."
        val semEncontro = itens.count { it.encontrosRealizados == 0 }
        if (semEncontro > 0) {
            avisos += "$semEncontro formação(ões) ainda sem encontro realizado: " +
                "quem está inscrito nelas aparece com 0% até o primeiro encontro acontecer."
        }

        return FormacaoDetalheDTO(
            cabecalho = cabecalho(
                "Formação de catequistas", anoAlvo, null,
                nomeComunidade(idComunidade, comunidades).takeIf { idComunidade != null },
                idCatequista?.let { nomes[it]?.nome },
                nivel?.rotulo
            ),
            inscritos = IndicadorDTO.de("Inscritos", inscritosTotal, null, DirecaoBoa.MAIOR),
            participaram = IndicadorDTO.de(
                "Participaram", participaramTotal, null, DirecaoBoa.MAIOR,
                detalhe = "com ao menos uma presença"
            ),
            atingiramMinimo = IndicadorDTO.de(
                "Atingiram o mínimo", atingiramTotal, null, DirecaoBoa.MAIOR,
                detalhe = "em todas as formações em que se inscreveram"
            ),
            porNivel = listOf(NivelEvento.DIOCESANO, NivelEvento.REGIONAL, NivelEvento.PAROQUIAL)
                .map { n ->
                    val doNivel = itens.filter { it.nivel == n }
                    val inscritos = doNivel.sumOf { it.inscritos }
                    val participaram = doNivel.sumOf { it.participaram }
                    LinhaFormacaoDTO(
                        nivel = n,
                        rotulo = n.rotulo,
                        formacoes = doNivel.size,
                        encontrosRealizados = doNivel.sumOf { it.encontrosRealizados },
                        inscritos = IndicadorDTO.de("Inscritos", inscritos, null, DirecaoBoa.MAIOR),
                        participaram = IndicadorDTO.de("Participaram", participaram, null, DirecaoBoa.MAIOR),
                        atingiramMinimo = IndicadorDTO.de(
                            "Atingiram o mínimo", doNivel.sumOf { it.atingiram }, null, DirecaoBoa.MAIOR
                        ),
                        taxaParticipacao = IndicadorDTO.de(
                            "Participação",
                            if (inscritos == 0) 0.0 else participaram.toDouble() / inscritos * 100.0,
                            null, DirecaoBoa.MAIOR, percentual = true
                        ),
                        minimo = doNivel.firstOrNull()?.minimo ?: CalculoFrequencia.MINIMO_PADRAO
                    )
                },
            catequistas = linhasCatequista,
            comunidades = linhasComunidade,
            formacoes = itens.sortedBy { it.nome },
            avisos = avisos
        )
    }

    // ----------------------------------------------------------------- eventos

    @Transactional(readOnly = true)
    fun eventos(
        ano: Int?,
        tipo: TipoEvento?,
        nivel: NivelEvento?,
        idComunidade: Long?
    ): EventosDetalheDTO {
        exigir()
        val hoje = LocalDate.now()
        val anoAlvo = ano ?: hoje.year
        val corte = if (anoAlvo == hoje.year) hoje else LocalDate.of(anoAlvo, 12, 31)
        val turmas = turmaRepository.findAll().associateBy { it.idTurma }
        val comunidades = comunidadeRepository.findAll().associateBy { it.idComunidade }
        val formacoes = formacaoRepository.findAll().associateBy { it.idFormacao }

        fun buscar(a: Int, ate: LocalDate) = eventoRepository
            .findNoPeriodo(LocalDate.of(a, 1, 1), ate)
            .filter { tipo == null || it.tipo == tipo }
            .filter { nivel == null || it.nivel == nivel }
            .filter { idComunidade == null || it.idComunidade == idComunidade }

        val doAno = buscar(anoAlvo, corte)
        val anoBase = anoAlvo - 1
        val base = buscar(anoBase, corteEquivalente(anoBase, corte))

        val linhas = doAno.map { e ->
            LinhaEventoDTO(
                idEvento = e.idEvento,
                titulo = e.titulo,
                tipo = e.tipo.name,
                rotuloTipo = e.tipo.rotulo,
                nivel = e.nivel?.name,
                rotuloNivel = e.nivel?.rotulo,
                data = e.dataInicio,
                situacao = e.situacao.name,
                local = e.local,
                comunidade = e.idComunidade?.let { nomeComunidade(it, comunidades) },
                turma = e.idTurma?.let { turmas[it]?.nome },
                formacao = e.idFormacao?.let { formacoes[it]?.nome },
                publico = publicoDe(e, turmas, comunidades),
                catequistasPresentes = presentesDeCatequista(e),
                catequisandosPresentes = presentesDeCatequisando(e)
            )
        }.sortedByDescending { it.data ?: LocalDate.MIN }

        val avisos = mutableListOf<String>()
        val semPresenca = linhas.count {
            it.situacao == SituacaoEvento.REALIZADO.name &&
                it.catequistasPresentes == null && it.catequisandosPresentes == null
        }
        if (semPresenca > 0) {
            avisos += "$semPresenca evento(s) realizados sem nenhuma chamada registrada — " +
                "não dá para dizer quem participou."
        }

        return EventosDetalheDTO(
            cabecalho = cabecalho(
                "Eventos", anoAlvo, anoBase,
                nomeComunidade(idComunidade, comunidades).takeIf { idComunidade != null },
                tipo?.rotulo, nivel?.rotulo
            ),
            total = IndicadorDTO.de("Eventos", doAno.size, base.size, DirecaoBoa.NEUTRA),
            realizados = IndicadorDTO.de(
                "Realizados", doAno.count { it.situacao == SituacaoEvento.REALIZADO },
                base.count { it.situacao == SituacaoEvento.REALIZADO }, DirecaoBoa.NEUTRA
            ),
            cancelados = IndicadorDTO.de(
                "Cancelados", doAno.count { it.situacao == SituacaoEvento.CANCELADO },
                base.count { it.situacao == SituacaoEvento.CANCELADO }, DirecaoBoa.MENOR
            ),
            porTipo = TipoEvento.entries.map { t ->
                FatiaDTO(t.name, t.rotulo, doAno.count { it.tipo == t }.toDouble(),
                    base.count { it.tipo == t }.toDouble())
            },
            porNivel = NivelEvento.entries.map { n ->
                FatiaDTO(n.name, n.rotulo, doAno.count { it.nivel == n }.toDouble(),
                    base.count { it.nivel == n }.toDouble())
            },
            eventos = linhas,
            avisos = avisos
        )
    }

    /** Quem o evento atinge, em uma frase. E a resposta de "para quem foi isso?". */
    private fun publicoDe(
        e: Evento,
        turmas: Map<Long, Turma>,
        comunidades: Map<Long, Comunidade>
    ): String = when (e.nivel) {
        NivelEvento.DIOCESANO -> "Toda a diocese"
        NivelEvento.REGIONAL -> "Toda a região"
        NivelEvento.PAROQUIAL -> "Toda a paróquia"
        NivelEvento.COMUNIDADE -> "Comunidade " + nomeComunidade(e.idComunidade, comunidades)
        NivelEvento.TURMA -> "Turma " + (e.idTurma?.let { turmas[it]?.nome } ?: "não informada")
        null -> e.publicoAlvo ?: "Não informado"
    }

    /** Presenca de catequista so existe em encontro de formacao. */
    private fun presentesDeCatequista(e: Evento): Int? {
        if (e.tipo != TipoEvento.FORMACAO) return null
        val presencas = presencaFormacaoRepository.findByIdEvento(e.idEvento)
        if (presencas.isEmpty()) return null
        return presencas.count { it.situacao == SituacaoPresenca.PRESENTE }
    }

    /**
     * Presenca de catequisando so existe onde alguem abriu a chamada de um
     * encontro amarrado ao evento. Nulo (e nao zero) quando nao ha chamada:
     * "ninguem foi" e "ninguem marcou" sao coisas diferentes.
     */
    private fun presentesDeCatequisando(e: Evento): Int? {
        val encontros = encontroRepository.findByIdEvento(e.idEvento)
        if (encontros.isEmpty()) return null
        val presencas = presencaRepository.findByEncontroIn(encontros)
        if (presencas.isEmpty()) return null
        return presencas.count { it.situacao == SituacaoPresenca.PRESENTE }
    }

    // ---------------------------------------------------------------- comuns

    private fun comunidadeDoCatequista(idCatequista: Long): Long? =
        turmaCatequistaRepository.findByIdCatequista(idCatequista)
            .mapNotNull { turmaRepository.findById(it.idTurma).orElse(null)?.idComunidade }
            .firstOrNull()

    private fun comunidadeDe(m: Matricula, turmas: Map<Long, Turma>): Long? =
        m.turma?.idTurma?.let { turmas[it]?.idComunidade }

    private fun nomeComunidade(id: Long?, comunidades: Map<Long, Comunidade>): String =
        id?.let { comunidades[it]?.nome ?: "Comunidade $it" } ?: SEM_COMUNIDADE

    private fun corteEquivalente(ano: Int, corte: LocalDate): LocalDate {
        val ultimoDia = LocalDate.of(ano, corte.monthValue, 1).lengthOfMonth()
        return LocalDate.of(ano, corte.monthValue, minOf(corte.dayOfMonth, ultimoDia))
    }

    /** O cabecalho e o que vai impresso: sem ele o papel vira numero sem contexto. */
    private fun cabecalho(titulo: String, ano: Int, anoBase: Int?, vararg filtros: String?): String {
        val partes = mutableListOf("$titulo · $ano")
        if (anoBase != null) partes += "comparado com $anoBase"
        filtros.filterNotNull().forEach { partes += it }
        if (filtros.all { it == null }) partes += "paróquia inteira"
        return partes.joinToString(" · ")
    }

    private fun rotuloSituacao(s: SituacaoMatricula): String = when (s) {
        SituacaoMatricula.CURSANDO -> "Cursando"
        SituacaoMatricula.CONCLUIDO -> "Concluiu"
        SituacaoMatricula.NAO_CONCLUIDO -> "Não concluiu"
        SituacaoMatricula.TRANSFERIDO -> "Transferido"
        SituacaoMatricula.DESISTENTE -> "Desistente"
    }

    private fun exigir() {
        if (!escopo.ehAdmin()) {
            throw AcessoNegadoException(
                "O painel de indicadores é exclusivo do coordenador paroquial."
            )
        }
    }
}
