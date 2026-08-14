package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.FrequenciaCatequisandoDTO
import com.catequese.catequeseapi.dto.FrequenciaTurmaDTO
import com.catequese.catequeseapi.dto.PeriodoFrequenciaDTO
import com.catequese.catequeseapi.dto.ResumoFrequenciaDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.Encontro
import com.catequese.catequeseapi.model.EtapaCatecumenato
import com.catequese.catequeseapi.model.JanelaApuracao
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.Presenca
import com.catequese.catequeseapi.model.SituacaoEncontro
import com.catequese.catequeseapi.model.SituacaoFrequencia
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.CatequisandoRepository
import com.catequese.catequeseapi.repository.EncontroRepository
import com.catequese.catequeseapi.repository.EtapaCatecumenoRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import com.catequese.catequeseapi.repository.PresencaRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Monta a frequencia a partir do que esta no banco.
 *
 * A CONTA em si nao mora aqui: ela esta em `CalculoFrequencia`, que nao conhece
 * banco e por isso pode ser coberta por teste de unidade. Aqui fica o que
 * depende de dados -- qual e a janela de apuracao de cada categoria, quais
 * encontros entram, de quando comeca a contar para cada pessoa.
 *
 * Regras que valem para todas as categorias:
 *
 * - So encontro FECHADO entra na conta. Aberto ainda pode mudar; CANCELADO nao
 *   aconteceu e nao vira falta de ninguem.
 * - A contagem comeca na data de matricula. Quem entrou em abril nao responde
 *   pelos encontros de fevereiro e marco.
 * - Falta justificada sai do denominador em vez de contar contra.
 */
@Service
class FrequenciaService(
    private val turmaRepository: TurmaRepository,
    private val encontroRepository: EncontroRepository,
    private val presencaRepository: PresencaRepository,
    private val matriculaRepository: MatriculaRepository,
    private val catequisandoRepository: CatequisandoRepository,
    private val etapaCatecumenoRepository: EtapaCatecumenoRepository,
    private val configuracaoService: ConfiguracaoService,
    private val escopo: EscopoAcessoService
) {

    /** Janela a apurar, ja resolvida para uma pessoa concreta. */
    private data class Janela(
        val periodo: CalculoFrequencia.Periodo,
        val exigeFrequencia: Boolean,
        val etapa: EtapaCatecumenato? = null,
        val semestre: Int? = null
    )

    // ---- Consulta por turma ------------------------------------------------

    /**
     * A tela do catequista: a turma inteira, com a situacao de cada um.
     *
     * @param incluirInativos traz tambem quem foi transferido ou desistiu.
     *        Fora do relatorio de fim de ano eles so poluem a lista.
     */
    @Transactional(readOnly = true)
    fun daTurma(idTurma: Long, anoPedido: Int?, incluirInativos: Boolean = false): FrequenciaTurmaDTO {
        val turma = exigirTurma(idTurma)
        val ano = anoPedido ?: LocalDate.now().year
        val hoje = LocalDate.now()
        val alerta = configuracaoService.percentualAlerta()
        val categoria = turma.categoria

        val todosDoAno = encontroRepository.findByTurmaOrderByDataDesc(turma)
            .filter { it.data?.year == ano }

        // Retiro e missa NAO entram na conta dos 80%. Sao atividades extras:
        // faltar num retiro nao pode reprovar quem cumpriu os encontros da
        // catequese. A presenca fica registrada e consultavel, so nao pesa.
        val doAno = todosDoAno.filter { it.idEvento == null }
        val deEvento = todosDoAno.filter { it.idEvento != null }
        val fechados = doAno.filter { it.situacao == SituacaoEncontro.FECHADO }

        // Uma consulta so para as presencas do ano inteiro: com 40 encontros e
        // 30 matriculados, buscar por pessoa seriam 30 idas ao banco.
        val presencasPorPessoa: Map<Long?, List<Presenca>> = if (fechados.isEmpty()) {
            emptyMap()
        } else {
            presencaRepository.findByEncontroIn(fechados)
                .groupBy { it.catequisando?.idCatequisando }
        }

        val matriculas = matriculaRepository.findByTurmaAndAno(turma, ano)
            .filter { incluirInativos || it.situacao !in SITUACOES_FORA_DA_LISTA }
            .sortedBy { it.catequisando?.nome?.lowercase() ?: "" }

        // O coordenador so enxerga a propria comunidade. Filtro de DADOS: nao
        // adianta esconder na tela se a API continua devolvendo o resto.
        val visiveis = matriculas.filter {
            escopo.podeVerComunidade(it.catequisando?.comunidade?.idComunidade)
        }
        if (visiveis.isEmpty() && matriculas.isNotEmpty()) {
            throw AcessoNegadoException(
                "Esta turma nao tem catequisandos da sua comunidade."
            )
        }

        val linhas = visiveis.mapNotNull { matricula ->
            val catequisando = matricula.catequisando ?: return@mapNotNull null
            linhaDe(
                catequisando = catequisando,
                matricula = matricula,
                turma = turma,
                ano = ano,
                hoje = hoje,
                fechados = fechados,
                presencas = presencasPorPessoa[catequisando.idCatequisando].orEmpty(),
                alerta = alerta
            )
        }

        val alertasDaTurma = mutableListOf<String>()
        if (categoria == null) {
            alertasDaTurma += "Esta turma ainda nao foi classificada por categoria, " +
                "entao a frequencia nao esta sendo cobrada. Peca ao coordenador paroquial " +
                "para definir a categoria da turma."
        }
        val eventosEncerrados = deEvento.count { it.situacao == SituacaoEncontro.FECHADO }
        if (eventosEncerrados > 0) {
            alertasDaTurma += "Esta turma tem $eventosEncerrados chamada(s) de evento " +
                "(retiro, missa) que NAO entram no calculo dos ${CalculoFrequencia.MINIMO_PADRAO}%."
        }
        if (doAno.any { it.situacao == SituacaoEncontro.ABERTO }) {
            alertasDaTurma += "Ha encontro em aberto nesta turma. Ele so entra na conta " +
                "depois de encerrado."
        }

        return FrequenciaTurmaDTO(
            idTurma = turma.idTurma,
            nomeTurma = turma.nome,
            categoria = categoria,
            janela = categoria?.janela ?: JanelaApuracao.NENHUMA,
            exigeFrequencia = categoria?.exigeFrequencia == true,
            ano = ano,
            minimo = CalculoFrequencia.MINIMO_PADRAO,
            alerta = alerta,
            encontrosFechados = fechados.size,
            encontrosCancelados = doAno.count { it.situacao == SituacaoEncontro.CANCELADO },
            encontrosAbertos = doAno.count { it.situacao == SituacaoEncontro.ABERTO },
            resumo = resumir(linhas),
            linhas = linhas,
            alertas = alertasDaTurma
        )
    }

    // ---- Consulta por catequisando -----------------------------------------

    /**
     * A ficha da pessoa. Devolve uma linha por matricula do ano -- normalmente
     * uma so, duas quando houve transferencia de turma no meio do ano.
     */
    @Transactional(readOnly = true)
    fun doCatequisando(idCatequisando: Long, anoPedido: Int?): List<FrequenciaCatequisandoDTO> {
        val ano = anoPedido ?: LocalDate.now().year
        return historicoInterno(idCatequisando).filter { it.ano == ano }
    }

    /** Percurso completo, do ano mais recente para o mais antigo. */
    @Transactional(readOnly = true)
    fun historico(idCatequisando: Long): List<FrequenciaCatequisandoDTO> =
        historicoInterno(idCatequisando)

    private fun historicoInterno(idCatequisando: Long): List<FrequenciaCatequisandoDTO> {
        val catequisando = catequisandoRepository.findById(idCatequisando)
            .orElseThrow { ResourceNotFoundException("Catequisando nao encontrado") }

        if (!escopo.podeVerComunidade(catequisando.comunidade?.idComunidade)) {
            throw AcessoNegadoException("Este catequisando nao e da sua comunidade.")
        }

        val hoje = LocalDate.now()
        val alerta = configuracaoService.percentualAlerta()
        val turmasPermitidas = escopo.turmasDoCatequista()

        return matriculaRepository.findByCatequisandoOrderByAnoDesc(catequisando)
            .filter { matricula ->
                // O catequista ve o historico so das turmas em que atua.
                val idTurma = matricula.turma?.idTurma
                turmasPermitidas == null || (idTurma != null && idTurma in turmasPermitidas)
            }
            .mapNotNull { matricula ->
                val turma = matricula.turma ?: return@mapNotNull null
                val fechados = encontroRepository.findByTurmaOrderByDataDesc(turma)
                    .filter { it.data?.year == matricula.ano }
                    // Mesma regra do daTurma: evento nao entra na conta.
                    .filter { it.idEvento == null }
                    .filter { it.situacao == SituacaoEncontro.FECHADO }
                val presencas: List<Presenca> = if (fechados.isEmpty()) {
                    emptyList()
                } else {
                    presencaRepository.findByCatequisandoAndEncontroIn(catequisando, fechados)
                }

                linhaDe(
                    catequisando = catequisando,
                    matricula = matricula,
                    turma = turma,
                    ano = matricula.ano,
                    hoje = hoje,
                    fechados = fechados,
                    presencas = presencas,
                    alerta = alerta
                )
            }
    }

    // ---- Montagem de uma linha ---------------------------------------------

    private fun linhaDe(
        catequisando: Catequisando,
        matricula: Matricula,
        turma: Turma,
        ano: Int,
        hoje: LocalDate,
        fechados: List<Encontro>,
        presencas: List<Presenca>,
        alerta: Int
    ): FrequenciaCatequisandoDTO {
        val categoria = turma.categoria
        val dataMatricula = matricula.dataMatricula
        val etapaAtual = if (categoria == CategoriaTurma.CATECUMENATO) {
            etapaCatecumenoRepository.findFirstByCatequisandoAndDataFimIsNull(catequisando)?.etapa
        } else {
            null
        }

        val janelas = janelasDe(categoria, catequisando, ano, hoje)
        val periodos = janelas.map { janela ->
            apurar(janela, fechados, presencas, dataMatricula, hoje, alerta)
        }

        val situacao = if (categoria == null || categoria.exigeFrequencia.not()) {
            SituacaoFrequencia.NAO_SE_APLICA
        } else {
            SituacaoFrequencia.pior(
                periodos.filter { it.situacao != SituacaoFrequencia.NAO_SE_APLICA }
                    .map { it.situacao }
            )
        }

        val alertas = alertasDe(categoria, periodos, etapaAtual, matricula)
        val podeConcluir = podeConcluir(categoria, periodos, matricula)

        return FrequenciaCatequisandoDTO(
            idCatequisando = catequisando.idCatequisando,
            nome = catequisando.nome,
            idTurma = turma.idTurma,
            nomeTurma = turma.nome,
            categoria = categoria,
            ano = ano,
            dataMatricula = dataMatricula,
            situacaoMatricula = matricula.situacao,
            etapaAtual = etapaAtual,
            periodos = periodos,
            percentualAtual = percentualCorrente(periodos),
            situacao = situacao,
            podeConcluir = podeConcluir,
            alertas = alertas
        )
    }

    /**
     * Quais janelas apurar, por categoria.
     *
     * O catecumenato e o unico caso em que a janela depende da PESSOA: cada um
     * passa pelas etapas no seu tempo, entao os periodos saem do historico de
     * etapas dele, e nao do calendario.
     */
    private fun janelasDe(
        categoria: CategoriaTurma?,
        catequisando: Catequisando,
        ano: Int,
        hoje: LocalDate
    ): List<Janela> = when (categoria?.janela) {
        null, JanelaApuracao.NENHUMA -> emptyList()

        JanelaApuracao.ANO -> listOf(
            Janela(CalculoFrequencia.anoCivil(ano), exigeFrequencia = true)
        )

        JanelaApuracao.SEMESTRE -> {
            val janelas = mutableListOf(
                Janela(CalculoFrequencia.semestre(ano, 1), exigeFrequencia = true, semestre = 1)
            )
            // O 2o semestre so aparece quando existe: em marco, mostrar um
            // semestre vazio so confundiria quem le a tela.
            if (ano < hoje.year || CalculoFrequencia.semestreDe(hoje) == 2) {
                janelas += Janela(
                    CalculoFrequencia.semestre(ano, 2), exigeFrequencia = true, semestre = 2
                )
            }
            janelas
        }

        JanelaApuracao.ETAPA_CATECUMENATO -> janelasDeEtapa(catequisando, ano)
    }

    private fun janelasDeEtapa(catequisando: Catequisando, ano: Int): List<Janela> {
        val inicioAno = LocalDate.of(ano, 1, 1)
        val fimAno = LocalDate.of(ano, 12, 31)

        return etapaCatecumenoRepository.findByCatequisandoOrderByDataInicioAsc(catequisando)
            .mapNotNull { registro ->
                val inicio = registro.dataInicio ?: inicioAno
                val fim = registro.dataFim ?: fimAno
                // Etapa que nao encostou neste ano nao tem o que apurar aqui.
                if (fim.isBefore(inicioAno) || inicio.isAfter(fimAno)) return@mapNotNull null

                val recorteInicio = if (inicio.isBefore(inicioAno)) inicioAno else inicio
                val recorteFim = if (fim.isAfter(fimAno)) fimAno else fim
                val etapa = registro.etapa

                Janela(
                    periodo = CalculoFrequencia.Periodo(
                        inicio = recorteInicio,
                        fim = recorteFim,
                        rotulo = rotuloEtapa(etapa)
                    ),
                    exigeFrequencia = etapa.exigeFrequencia,
                    etapa = etapa
                )
            }
    }

    private fun apurar(
        janela: Janela,
        fechados: List<Encontro>,
        presencas: List<Presenca>,
        dataMatricula: LocalDate?,
        hoje: LocalDate,
        alerta: Int
    ): PeriodoFrequenciaDTO {
        val inicio = CalculoFrequencia.inicioEfetivo(janela.periodo, dataMatricula)
        val fim = janela.periodo.fim

        val noPeriodo = fechados.filter { encontro ->
            val data = encontro.data
            data != null && !data.isBefore(inicio) && !data.isAfter(fim)
        }
        val idsDoPeriodo = HashSet<Long>()
        noPeriodo.forEach { idsDoPeriodo.add(it.idEncontro) }

        val marcacoes = presencas.filter { presenca ->
            val idEncontro = presenca.encontro?.idEncontro
            idEncontro != null && idEncontro in idsDoPeriodo
        }
        val presentes = marcacoes.count { it.situacao == SituacaoPresenca.PRESENTE }
        val justificadas = marcacoes.count { it.situacao == SituacaoPresenca.JUSTIFICADA }

        val resultado = CalculoFrequencia.apurar(
            encontrosFechados = noPeriodo.size,
            presencas = presentes,
            justificadas = justificadas,
            minimo = CalculoFrequencia.MINIMO_PADRAO,
            alerta = alerta
        )

        // Pre-catequese, perseveranca e pre-catecumenato continuam com os
        // numeros a vista -- so nao viram cobranca.
        val situacao = if (janela.exigeFrequencia) {
            resultado.situacao
        } else {
            SituacaoFrequencia.NAO_SE_APLICA
        }

        return PeriodoFrequenciaDTO(
            rotulo = janela.periodo.rotulo,
            inicio = inicio,
            fim = fim,
            encontrosConsiderados = resultado.encontrosConsiderados,
            presencas = resultado.presencas,
            faltas = resultado.faltas,
            justificadas = resultado.justificadas,
            percentual = resultado.percentual,
            situacao = situacao,
            minimo = CalculoFrequencia.MINIMO_PADRAO,
            encerrado = hoje.isAfter(fim),
            etapa = janela.etapa,
            semestre = janela.semestre
        )
    }

    // ---- Avisos ------------------------------------------------------------

    /**
     * Regra dos adultos: abaixo do minimo no 1o semestre, nao conclui a
     * catequese naquele ano. O 2o semestre nao recupera.
     */
    private fun podeConcluir(
        categoria: CategoriaTurma?,
        periodos: List<PeriodoFrequenciaDTO>,
        matricula: Matricula
    ): Boolean {
        if (matricula.situacao == SituacaoMatricula.NAO_CONCLUIDO) return false
        if (categoria != CategoriaTurma.ADULTOS) return true

        val primeiroSemestre = periodos.firstOrNull { it.semestre == 1 } ?: return true
        val reprovado = primeiroSemestre.encerrado &&
            primeiroSemestre.situacao == SituacaoFrequencia.ABAIXO_DO_MINIMO
        return !reprovado
    }

    private fun alertasDe(
        categoria: CategoriaTurma?,
        periodos: List<PeriodoFrequenciaDTO>,
        etapaAtual: EtapaCatecumenato?,
        matricula: Matricula
    ): List<String> {
        val avisos = mutableListOf<String>()

        if (categoria == null) {
            avisos += "A turma ainda nao foi classificada, entao a frequencia nao esta sendo apurada."
            return avisos
        }
        if (!categoria.exigeFrequencia) return avisos

        if (categoria == CategoriaTurma.CATECUMENATO && etapaAtual == null) {
            avisos += "A etapa atual do catecumeno ainda nao foi registrada. " +
                "Sem ela nao ha periodo para apurar."
        }

        periodos.filter { it.situacao != SituacaoFrequencia.NAO_SE_APLICA }.forEach { periodo ->
            val texto = "${periodo.percentual ?: 0.0}% em ${periodo.rotulo}"
            when (periodo.situacao) {
                SituacaoFrequencia.ABAIXO_DO_MINIMO ->
                    avisos += if (periodo.encerrado) {
                        "Ficou abaixo dos ${periodo.minimo}% exigidos: $texto."
                    } else {
                        "Esta abaixo dos ${periodo.minimo}% exigidos: $texto. " +
                            "Ainda da tempo de recuperar neste periodo."
                    }

                SituacaoFrequencia.EM_RISCO ->
                    avisos += "Frequencia perto do limite: $texto."

                else -> Unit
            }
        }

        // O aviso mais importante da regra dos adultos, com o encaminhamento
        // que o usuario pediu: procurar o coordenador.
        if (categoria == CategoriaTurma.ADULTOS) {
            val primeiro = periodos.firstOrNull { it.semestre == 1 }
            if (primeiro != null && primeiro.situacao == SituacaoFrequencia.ABAIXO_DO_MINIMO) {
                avisos += if (primeiro.encerrado) {
                    "Como ficou abaixo de ${primeiro.minimo}% no 1o semestre, nao conclui a " +
                        "catequese neste ano. Procure o coordenador."
                } else {
                    "Atencao: mantendo este percentual no 1o semestre, nao sera possivel " +
                        "concluir a catequese neste ano. Procure o coordenador."
                }
            }
        }

        if (matricula.situacao == SituacaoMatricula.TRANSFERIDO) {
            avisos += "Matricula transferida: esta frequencia cobre apenas o periodo nesta turma."
        }

        return avisos
    }

    // ---- Apoio -------------------------------------------------------------

    /**
     * O numero que vai para a coluna da lista: o periodo em andamento; se
     * nenhum estiver aberto, o ultimo que teve apuracao.
     */
    private fun percentualCorrente(periodos: List<PeriodoFrequenciaDTO>): Double? {
        val comApuracao = periodos.filter { it.percentual != null }
        if (comApuracao.isEmpty()) return null
        return (comApuracao.lastOrNull { !it.encerrado } ?: comApuracao.last()).percentual
    }

    private fun resumir(linhas: List<FrequenciaCatequisandoDTO>) = ResumoFrequenciaDTO(
        total = linhas.size,
        regulares = linhas.count { it.situacao == SituacaoFrequencia.REGULAR },
        emRisco = linhas.count { it.situacao == SituacaoFrequencia.EM_RISCO },
        abaixoDoMinimo = linhas.count { it.situacao == SituacaoFrequencia.ABAIXO_DO_MINIMO },
        semApuracao = linhas.count { it.situacao == SituacaoFrequencia.SEM_APURACAO },
        naoSeAplica = linhas.count { it.situacao == SituacaoFrequencia.NAO_SE_APLICA }
    )

    private fun rotuloEtapa(etapa: EtapaCatecumenato): String = when (etapa) {
        EtapaCatecumenato.PRE_CATECUMENATO -> "Pre-catecumenato"
        EtapaCatecumenato.CATECUMENATO -> "Catecumenato"
        EtapaCatecumenato.PURIFICACAO_ILUMINACAO -> "Purificacao e iluminacao"
        EtapaCatecumenato.MISTAGOGIA -> "Mistagogia"
    }

    private fun exigirTurma(idTurma: Long): Turma {
        val turma = turmaRepository.findById(idTurma)
            .orElseThrow { ResourceNotFoundException("Turma nao encontrada") }

        val turmasDoCatequista = escopo.turmasDoCatequista()
        if (turmasDoCatequista != null && turma.idTurma !in turmasDoCatequista) {
            throw AcessoNegadoException("Voce nao atua nesta turma.")
        }
        return turma
    }

    private companion object {
        val SITUACOES_FORA_DA_LISTA = setOf(
            SituacaoMatricula.TRANSFERIDO,
            SituacaoMatricula.DESISTENTE
        )
    }
}
