package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.ClassificacaoTurmaDTO
import com.catequese.catequeseapi.dto.MatriculaAdminDTO
import com.catequese.catequeseapi.dto.NovaMatriculaDTO
import com.catequese.catequeseapi.dto.SituacaoMatriculaDTO
import com.catequese.catequeseapi.dto.TransferenciaDTO
import com.catequese.catequeseapi.dto.TurmaAdminDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.JanelaApuracao
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.CatequisandoRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Operacoes que so o coordenador paroquial faz.
 *
 * Esta e a etapa que destrava as outras: sem categoria na turma a frequencia
 * nao e apurada, e sem matricula nao existe lista de chamada. Por isso as
 * mensagens de erro aqui explicam a consequencia, e nao apenas o que faltou.
 *
 * A checagem de administrador esta aqui e tambem na SecurityConfig, de
 * proposito: a regra de URL protege a rota, e esta protege o metodo, caso um
 * dia alguem o chame de outro lugar.
 */
@Service
class AdminCatequeseService(
    private val turmaRepository: TurmaRepository,
    private val matriculaRepository: MatriculaRepository,
    private val catequisandoRepository: CatequisandoRepository,
    private val escopo: EscopoAcessoService
) {
    private val log = LoggerFactory.getLogger(AdminCatequeseService::class.java)

    class OperacaoInvalidaException(mensagem: String) : IllegalArgumentException(mensagem)

    // ---- Turmas ------------------------------------------------------------

    @Transactional(readOnly = true)
    fun turmas(anoPedido: Int?): List<TurmaAdminDTO> {
        exigirAdmin()
        val ano = anoPedido ?: LocalDate.now().year

        return turmaRepository.findAll()
            .map { turma -> paraDTO(turma, ano) }
            // As pendentes de classificacao primeiro: sao as que travam a
            // frequencia, entao e o que o administrador precisa resolver.
            .sortedWith(
                compareByDescending<TurmaAdminDTO> { it.pendenteDeClassificacao }
                    .thenBy { it.nome.lowercase() }
            )
    }

    @Transactional
    fun classificar(idTurma: Long, dto: ClassificacaoTurmaDTO): TurmaAdminDTO {
        exigirAdmin()
        val turma = exigirTurma(idTurma)

        val etapa = dto.etapa
        if (etapa != null && etapa !in 1..2) {
            throw OperacaoInvalidaException(
                "A etapa da turma deve ser 1 (primeiro ano) ou 2 (segundo ano)."
            )
        }

        val categoria = dto.categoria
        // Etapa sem categoria nao significa nada: e a categoria que diz
        // quantos anos o percurso tem.
        val etapaFinal = if (categoria == null) null else etapa

        val salva = turmaRepository.save(turma.copy(categoria = categoria, etapa = etapaFinal))
        log.info(
            "Turma {} classificada como {} (etapa {}) por '{}'",
            salva.nome, categoria?.name ?: "SEM CATEGORIA", etapaFinal ?: "-", quem()
        )
        return paraDTO(salva, LocalDate.now().year)
    }

    // ---- Matriculas --------------------------------------------------------

    @Transactional(readOnly = true)
    fun matriculasDaTurma(idTurma: Long, anoPedido: Int?): List<MatriculaAdminDTO> {
        exigirAdmin()
        val turma = exigirTurma(idTurma)
        val ano = anoPedido ?: LocalDate.now().year

        return matriculaRepository.findByTurmaAndAno(turma, ano)
            .map { paraDTO(it) }
            .sortedBy { it.nomeCatequisando.lowercase() }
    }

    @Transactional
    fun matricular(dto: NovaMatriculaDTO): MatriculaAdminDTO {
        exigirAdmin()
        val turma = exigirTurma(dto.idTurma)
        val catequisando = catequisandoRepository.findById(dto.idCatequisando)
            .orElseThrow { ResourceNotFoundException("Catequisando nao encontrado") }

        val ano = dto.ano ?: LocalDate.now().year
        val data = dto.dataMatricula ?: LocalDate.now()

        if (matriculaRepository.existsByCatequisandoAndTurmaAndAno(catequisando, turma, ano)) {
            throw OperacaoInvalidaException(
                "${catequisando.nome} ja esta matriculado em ${turma.nome} em $ano."
            )
        }

        if (data.year != ano) {
            // Deixar passar produziria uma frequencia sem sentido: a janela de
            // apuracao e do ano, e a contagem comecaria fora dela.
            throw OperacaoInvalidaException(
                "A data da matricula ($data) precisa ser do mesmo ano da matricula ($ano)."
            )
        }

        val agora = LocalDateTime.now().withNano(0)
        val salva = matriculaRepository.save(
            Matricula(
                catequisando = catequisando,
                turma = turma,
                ano = ano,
                dataMatricula = data,
                situacao = SituacaoMatricula.CURSANDO,
                observacao = dto.observacao?.trim()?.ifBlank { null },
                criadoEm = agora,
                atualizadoEm = agora,
                atualizadoPor = quem()
            )
        )
        log.info(
            "Matricula de {} em {} ({}) criada por '{}'",
            catequisando.nome, turma.nome, ano, quem()
        )
        return paraDTO(salva)
    }

    @Transactional
    fun alterarSituacao(idMatricula: Long, dto: SituacaoMatriculaDTO): MatriculaAdminDTO {
        exigirAdmin()
        val matricula = exigirMatricula(idMatricula)

        val salva = matriculaRepository.save(
            matricula.copy(
                situacao = dto.situacao,
                observacao = dto.observacao?.trim()?.ifBlank { null } ?: matricula.observacao,
                atualizadoEm = LocalDateTime.now().withNano(0),
                atualizadoPor = quem()
            )
        )
        log.info(
            "Matricula {} de {} passou para {} por '{}'",
            idMatricula, salva.catequisando?.nome, dto.situacao, quem()
        )
        return paraDTO(salva)
    }

    /**
     * Transferencia no meio do ano.
     *
     * A origem vira TRANSFERIDO e o destino ganha uma matricula nova com a
     * data da mudanca. E o que faz cada turma cobrar apenas o periodo em que a
     * pessoa esteve nela -- se a matricula fosse simplesmente movida, a turma
     * de destino cobraria encontros de quando ela ainda nem estava la.
     */
    @Transactional
    fun transferir(idMatricula: Long, dto: TransferenciaDTO): List<MatriculaAdminDTO> {
        exigirAdmin()
        val origem = exigirMatricula(idMatricula)
        val destino = exigirTurma(dto.idTurmaDestino)
        val catequisando = origem.catequisando
            ?: throw OperacaoInvalidaException("Esta matricula nao tem catequisando vinculado.")

        if (origem.turma?.idTurma == destino.idTurma) {
            throw OperacaoInvalidaException("A turma de destino e a mesma de origem.")
        }
        if (origem.situacao == SituacaoMatricula.TRANSFERIDO) {
            throw OperacaoInvalidaException("Esta matricula ja foi transferida.")
        }

        val ano = origem.ano
        val data = dto.data ?: LocalDate.now()
        if (data.year != ano) {
            throw OperacaoInvalidaException(
                "A data da transferencia ($data) precisa ser do mesmo ano da matricula ($ano)."
            )
        }
        if (matriculaRepository.existsByCatequisandoAndTurmaAndAno(catequisando, destino, ano)) {
            throw OperacaoInvalidaException(
                "${catequisando.nome} ja tem matricula em ${destino.nome} em $ano."
            )
        }

        val motivo = dto.motivo?.trim()?.ifBlank { null }
        val agora = LocalDateTime.now().withNano(0)

        val encerrada = matriculaRepository.save(
            origem.copy(
                situacao = SituacaoMatricula.TRANSFERIDO,
                observacao = listOfNotNull(
                    origem.observacao,
                    "Transferido para ${destino.nome} em $data" + (motivo?.let { ": $it" } ?: "")
                ).joinToString(" | "),
                atualizadoEm = agora,
                atualizadoPor = quem()
            )
        )

        val nova = matriculaRepository.save(
            Matricula(
                catequisando = catequisando,
                turma = destino,
                ano = ano,
                dataMatricula = data,
                situacao = SituacaoMatricula.CURSANDO,
                observacao = "Transferido de ${origem.turma?.nome ?: "turma removida"} em $data" +
                    (motivo?.let { ": $it" } ?: ""),
                criadoEm = agora,
                atualizadoEm = agora,
                atualizadoPor = quem()
            )
        )

        log.info(
            "{} transferido de {} para {} em {} por '{}'",
            catequisando.nome, origem.turma?.nome, destino.nome, data, quem()
        )
        return listOf(paraDTO(encerrada), paraDTO(nova))
    }

    // ---- Apoio -------------------------------------------------------------

    private fun paraDTO(turma: Turma, ano: Int): TurmaAdminDTO {
        val categoria = turma.categoria
        return TurmaAdminDTO(
            idTurma = turma.idTurma,
            nome = turma.nome,
            descricao = turma.descricao,
            ano = turma.ano,
            nivel = turma.nivel,
            categoria = categoria,
            janela = categoria?.janela ?: JanelaApuracao.NENHUMA,
            exigeFrequencia = categoria?.exigeFrequencia == true,
            etapa = turma.etapa,
            nomeCatequista = turma.catequista?.nome,
            matriculadosNoAno = matriculaRepository.findByTurmaAndAno(turma, ano)
                .count { it.situacao != SituacaoMatricula.DESISTENTE },
            pendenteDeClassificacao = categoria == null
        )
    }

    private fun paraDTO(matricula: Matricula) = MatriculaAdminDTO(
        idMatricula = matricula.idMatricula,
        idCatequisando = matricula.catequisando?.idCatequisando ?: 0,
        nomeCatequisando = matricula.catequisando?.nome ?: "(catequisando removido)",
        idTurma = matricula.turma?.idTurma,
        nomeTurma = matricula.turma?.nome,
        ano = matricula.ano,
        dataMatricula = matricula.dataMatricula,
        situacao = matricula.situacao,
        observacao = matricula.observacao,
        atualizadoPor = matricula.atualizadoPor
    )

    private fun exigirTurma(idTurma: Long): Turma = turmaRepository.findById(idTurma)
        .orElseThrow { ResourceNotFoundException("Turma nao encontrada") }

    private fun exigirMatricula(idMatricula: Long): Matricula =
        matriculaRepository.findById(idMatricula)
            .orElseThrow { ResourceNotFoundException("Matricula nao encontrada") }

    private fun exigirAdmin() {
        if (!escopo.ehAdmin()) {
            throw AcessoNegadoException(
                "Somente o coordenador paroquial pode fazer esta operacao."
            )
        }
    }

    private fun quem(): String? = escopo.usuarioLogado()?.username
}
