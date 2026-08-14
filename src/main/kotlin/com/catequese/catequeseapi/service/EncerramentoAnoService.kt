package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.AplicarEncerramentoDTO
import com.catequese.catequeseapi.dto.PreviaAnoDTO
import com.catequese.catequeseapi.dto.PreviaEncerramentoDTO
import com.catequese.catequeseapi.dto.ResultadoEncerramentoDTO
import com.catequese.catequeseapi.dto.ResumoEncerramentoDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.EtapaCatecumenato
import com.catequese.catequeseapi.model.EtapaCatecumeno
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.SituacaoFrequencia
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.repository.EtapaCatecumenoRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Encerramento do ano da catequese.
 *
 * E a operacao mais destrutiva do sistema: e ela que decide quem concluiu.
 * Por isso trabalha em duas fases -- previa e aplicacao -- e a aplicacao so
 * mexe nas matriculas que o administrador escolheu explicitamente.
 *
 * As decisoes:
 *
 * - Frequencia abaixo do minimo no ano -> NAO_CONCLUIDO.
 * - Adultos abaixo de 80% no 1o semestre -> NAO_CONCLUIDO mesmo que o 2o
 *   semestre tenha sido bom. Foi regra explicita: o 2o semestre nao recupera.
 * - Sem nenhum encontro apurado -> o sistema NAO decide. Reprovar quem nunca
 *   teve encontro seria culpar a pessoa por uma falha de registro, e aprovar
 *   seria inventar um resultado. Fica para o administrador resolver a mao.
 * - Categoria sem exigencia de frequencia (pre-catequese, perseveranca) ->
 *   conclui, porque nao ha criterio a cumprir.
 *
 * O percurso da categoria (2 anos em Eucaristia, Crisma e Adultos) so fecha
 * quando a pessoa acumula os anos previstos; ate la ela conclui o ano e
 * segue para o seguinte.
 */
@Service
class EncerramentoAnoService(
    private val turmaRepository: TurmaRepository,
    private val matriculaRepository: MatriculaRepository,
    private val etapaCatecumenoRepository: EtapaCatecumenoRepository,
    private val frequenciaService: FrequenciaService,
    private val escopo: EscopoAcessoService
) {
    private val log = LoggerFactory.getLogger(EncerramentoAnoService::class.java)

    class EncerramentoInvalidoException(mensagem: String) : IllegalArgumentException(mensagem)

    // ---- Previa ------------------------------------------------------------

    @Transactional(readOnly = true)
    fun previa(anoPedido: Int?): PreviaAnoDTO {
        exigirAdmin()
        val ano = anoPedido ?: LocalDate.now().year
        val linhas = mutableListOf<PreviaEncerramentoDTO>()
        val alertas = mutableListOf<String>()

        turmaRepository.findAll().forEach { turma ->
            val matriculas = matriculaRepository.findByTurmaAndAno(turma, ano)
                .filter { it.situacao == SituacaoMatricula.CURSANDO }
            if (matriculas.isEmpty()) return@forEach

            if (turma.categoria == null) {
                alertas += "A turma ${turma.nome} nao tem categoria definida, entao " +
                    "${matriculas.size} matricula(s) dela ficam de fora do encerramento."
            }

            // Uma consulta de frequencia por turma, e nao por pessoa: com a
            // paroquia inteira, por pessoa seriam centenas de idas ao banco.
            val frequencias = frequenciaService.daTurma(turma.idTurma, ano, false)
                .linhas.associateBy { it.idCatequisando }

            matriculas.forEach { matricula ->
                val catequisando = matricula.catequisando ?: return@forEach
                val freq = frequencias[catequisando.idCatequisando]
                linhas += avaliar(matricula, freq, ano)
            }
        }

        linhas.sortWith(
            // Quem exige decisao manual primeiro, depois quem nao conclui:
            // e a ordem em que o administrador precisa olhar.
            compareBy<PreviaEncerramentoDTO> { it.aplicavel }
                .thenBy { it.situacaoProposta != SituacaoMatricula.NAO_CONCLUIDO }
                .thenBy { it.nome.lowercase() }
        )

        return PreviaAnoDTO(
            ano = ano,
            resumo = ResumoEncerramentoDTO(
                total = linhas.size,
                concluem = linhas.count { it.situacaoProposta == SituacaoMatricula.CONCLUIDO },
                naoConcluem = linhas.count { it.situacaoProposta == SituacaoMatricula.NAO_CONCLUIDO },
                semBase = linhas.count { !it.aplicavel },
                concluemPercurso = linhas.count { it.concluiPercurso },
                promocoesDeEtapa = linhas.count { it.proximaEtapa != null }
            ),
            linhas = linhas,
            alertas = alertas
        )
    }

    /** A decisao para uma matricula. Sem efeito colateral: so calcula. */
    private fun avaliar(
        matricula: Matricula,
        freq: com.catequese.catequeseapi.dto.FrequenciaCatequisandoDTO?,
        ano: Int
    ): PreviaEncerramentoDTO {
        val catequisando = matricula.catequisando
        val turma = matricula.turma
        val categoria = turma?.categoria

        val situacaoFrequencia = freq?.situacao ?: SituacaoFrequencia.SEM_APURACAO
        val anosCumpridos = if (categoria == null || catequisando == null) {
            0
        } else {
            matriculaRepository.findByCatequisandoOrderByAnoDesc(catequisando)
                .filter { it.ano != ano }
                .filter { it.turma?.categoria == categoria }
                .count { it.situacao == SituacaoMatricula.CONCLUIDO }
        }

        val base = PreviaEncerramentoDTO(
            idMatricula = matricula.idMatricula,
            idCatequisando = catequisando?.idCatequisando ?: 0,
            nome = catequisando?.nome ?: "(catequisando removido)",
            idTurma = turma?.idTurma,
            nomeTurma = turma?.nome,
            categoria = categoria,
            ano = ano,
            situacaoAtual = matricula.situacao,
            situacaoProposta = null,
            percentual = freq?.percentualAtual,
            situacaoFrequencia = situacaoFrequencia,
            anosCumpridos = anosCumpridos,
            anosPrevistos = categoria?.anosPrevistos,
            concluiPercurso = false,
            etapaAtual = freq?.etapaAtual,
            proximaEtapa = null,
            aplicavel = false,
            motivo = ""
        )

        if (categoria == null) {
            return base.copy(
                motivo = "Turma sem categoria: nao ha criterio para decidir. " +
                    "Classifique a turma antes de encerrar o ano."
            )
        }

        // Categorias sem exigencia de frequencia nao tem o que reprovar.
        if (!categoria.exigeFrequencia) {
            return base.copy(
                situacaoProposta = SituacaoMatricula.CONCLUIDO,
                aplicavel = true,
                concluiPercurso = true,
                motivo = "Categoria sem exigencia de frequencia: conclui o ano."
            )
        }

        if (situacaoFrequencia == SituacaoFrequencia.SEM_APURACAO) {
            return base.copy(
                motivo = "Nenhum encontro apurado no periodo. O sistema nao decide: " +
                    "reprovar seria culpar a pessoa por falta de registro."
            )
        }

        val reprovaPorFrequencia = situacaoFrequencia == SituacaoFrequencia.ABAIXO_DO_MINIMO
        val reprovaPorRegraDosAdultos =
            categoria == CategoriaTurma.ADULTOS && freq?.podeConcluir == false

        if (reprovaPorFrequencia || reprovaPorRegraDosAdultos) {
            val motivo = if (reprovaPorRegraDosAdultos && !reprovaPorFrequencia) {
                "Ficou abaixo do minimo no 1o semestre: pela regra dos adultos, " +
                    "nao conclui neste ano."
            } else {
                "Frequencia de ${freq?.percentualAtual ?: 0.0}%, abaixo do minimo exigido."
            }
            return base.copy(
                situacaoProposta = SituacaoMatricula.NAO_CONCLUIDO,
                aplicavel = true,
                motivo = motivo
            )
        }

        // Aprovado. Falta saber se o percurso da categoria termina aqui.
        val previstos = categoria.anosPrevistos
        val cumpridosDepois = anosCumpridos + 1
        val fechaPercurso = previstos != null && cumpridosDepois >= previstos

        val proxima = if (categoria == CategoriaTurma.CATECUMENATO) {
            proximaEtapaDe(freq?.etapaAtual)
        } else {
            null
        }

        val motivo = buildString {
            append("Frequencia de ${freq?.percentualAtual ?: 0.0}%: conclui o ano.")
            if (fechaPercurso) {
                append(" Completa os $previstos ano(s) da categoria e encerra o percurso.")
            } else if (previstos != null) {
                append(" Vai para o ano $cumpridosDepois de $previstos.")
            }
            if (proxima != null) {
                append(" Etapa avanca para ${proxima.name.lowercase().replace('_', ' ')}.")
            }
        }

        return base.copy(
            situacaoProposta = SituacaoMatricula.CONCLUIDO,
            aplicavel = true,
            concluiPercurso = fechaPercurso,
            proximaEtapa = proxima,
            motivo = motivo
        )
    }

    // ---- Aplicacao ---------------------------------------------------------

    @Transactional
    fun aplicar(dto: AplicarEncerramentoDTO): ResultadoEncerramentoDTO {
        exigirAdmin()
        val ano = dto.ano ?: LocalDate.now().year

        if (dto.idsMatricula.isEmpty()) {
            throw EncerramentoInvalidoException(
                "Nenhuma matricula foi selecionada. Confira a previa e marque quem deve ser encerrado."
            )
        }

        // Recalcula em vez de confiar no que a tela mandou: entre a previa e o
        // clique, uma chamada pode ter sido corrigida e mudado o resultado.
        val previa = previa(ano).linhas.associateBy { it.idMatricula }
        val escolhidas = dto.idsMatricula.toSet()
        val ignoradas = mutableListOf<String>()
        val agora = LocalDateTime.now().withNano(0)
        val autor = quem()

        var atualizadas = 0
        var promovidas = 0

        escolhidas.forEach { idMatricula ->
            val linha = previa[idMatricula]
            if (linha == null) {
                ignoradas += "Matricula $idMatricula nao esta mais entre as pendentes de $ano."
                return@forEach
            }
            val proposta = linha.situacaoProposta
            if (!linha.aplicavel || proposta == null) {
                ignoradas += "${linha.nome}: ${linha.motivo}"
                return@forEach
            }

            val matricula = matriculaRepository.findById(idMatricula).orElse(null)
                ?: run {
                    ignoradas += "Matricula $idMatricula nao encontrada."
                    return@forEach
                }

            matriculaRepository.save(
                matricula.copy(
                    situacao = proposta,
                    observacao = listOfNotNull(matricula.observacao, "Encerramento de $ano: ${linha.motivo}")
                        .joinToString(" | "),
                    atualizadoEm = agora,
                    atualizadoPor = autor
                )
            )
            atualizadas++

            if (dto.promoverEtapas && linha.proximaEtapa != null) {
                if (promoverEtapa(matricula, linha.proximaEtapa, agora, autor)) promovidas++
            }
        }

        log.warn(
            "ENCERRAMENTO de {} aplicado por '{}': {} matricula(s), {} promocao(oes) de etapa.",
            ano, autor ?: "?", atualizadas, promovidas
        )
        return ResultadoEncerramentoDTO(ano, atualizadas, promovidas, ignoradas)
    }

    /**
     * Fecha a etapa em andamento e abre a seguinte.
     *
     * Sao dois registros porque cada etapa tem apuracao propria de frequencia:
     * sem a data de fim da anterior e a de inicio da nova, nao daria para
     * dizer "de marco a agosto ele esteve no Catecumenato e teve 82% ali".
     */
    private fun promoverEtapa(
        matricula: Matricula,
        proxima: EtapaCatecumenato,
        agora: LocalDateTime,
        autor: String?
    ): Boolean {
        val catequisando = matricula.catequisando ?: return false
        val aberta = etapaCatecumenoRepository
            .findFirstByCatequisandoAndDataFimIsNull(catequisando) ?: return false

        val hoje = agora.toLocalDate()
        etapaCatecumenoRepository.save(aberta.copy(dataFim = hoje))
        etapaCatecumenoRepository.save(
            EtapaCatecumeno(
                catequisando = catequisando,
                etapa = proxima,
                dataInicio = hoje,
                dataFim = null,
                observacao = "Promovido no encerramento de ${matricula.ano}.",
                registradoPor = autor,
                registradoEm = agora
            )
        )
        return true
    }

    // ---- Apoio -------------------------------------------------------------

    /** A ordem do caminho. Mistagogia e a ultima: dali nao se promove. */
    private fun proximaEtapaDe(atual: EtapaCatecumenato?): EtapaCatecumenato? = when (atual) {
        EtapaCatecumenato.PRE_CATECUMENATO -> EtapaCatecumenato.CATECUMENATO
        EtapaCatecumenato.CATECUMENATO -> EtapaCatecumenato.PURIFICACAO_ILUMINACAO
        EtapaCatecumenato.PURIFICACAO_ILUMINACAO -> EtapaCatecumenato.MISTAGOGIA
        EtapaCatecumenato.MISTAGOGIA -> null
        null -> null
    }

    private fun exigirAdmin() {
        if (!escopo.ehAdmin()) {
            throw AcessoNegadoException(
                "Somente o coordenador paroquial pode encerrar o ano."
            )
        }
    }

    private fun quem(): String? = escopo.usuarioLogado()?.username
}
