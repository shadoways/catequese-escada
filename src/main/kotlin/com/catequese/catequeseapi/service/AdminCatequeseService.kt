package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.ClassificacaoTurmaDTO
import com.catequese.catequeseapi.dto.MatriculaAdminDTO
import com.catequese.catequeseapi.dto.NovaMatriculaDTO
import com.catequese.catequeseapi.dto.SituacaoMatriculaDTO
import com.catequese.catequeseapi.dto.TransferenciaDTO
import com.catequese.catequeseapi.dto.TurmaAdminDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.EtapaCatecumenato
import com.catequese.catequeseapi.model.JanelaApuracao
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.CatequisandoRepository
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.EtapaCatecumenoRepository
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
    private val comunidadeRepository: ComunidadeRepository,
    private val etapaCatecumenoRepository: EtapaCatecumenoRepository,
    private val escopo: EscopoAcessoService
) {
    private val log = LoggerFactory.getLogger(AdminCatequeseService::class.java)

    class OperacaoInvalidaException(mensagem: String) : IllegalArgumentException(mensagem)

    // ---- Turmas ------------------------------------------------------------

    @Transactional(readOnly = true)
    fun turmas(anoPedido: Int?): List<TurmaAdminDTO> {
        exigirAdmin()
        val ano = anoPedido ?: LocalDate.now().year

        // O ano E FILTRO, nao so o periodo da contagem.
        //
        // Antes ele so escolhia de que ano contar os inscritos, e a lista vinha
        // com `findAll()`: quem pedia 2026 recebia as turmas de 2025 junto,
        // todas com "0 inscritos" -- e a tela dizia 2026 no topo. Ler aquilo
        // como "a turma esvaziou" era o mais natural do mundo.
        //
        // Turma SEM ano aparece em qualquer ano, de proposito: e cadastro
        // antigo, esta e a unica tela onde ela pode ser classificada, e filtrar
        // por igualdade a tornaria inalcancavel para sempre. O aviso do topo
        // conta quantas sao.
        return turmaRepository.findAll()
            .filter { it.ano == null || it.ano == ano }
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
                "A fase da turma deve ser 1 (primeira fase) ou 2 (segunda fase)."
            )
        }

        val categoria = dto.categoria
        // Fase so existe em Eucaristia e Crisma -- ver RegrasDeMovimentacao.temFases.
        //
        // A tela esconde o campo nas demais, mas isso e conforto: a regra vale
        // aqui (invariante 1). Sem esta linha, uma turma que era Eucaristia 2 e
        // virou Adultos guardaria etapa=2 -- um valor que nenhuma tela mostra e
        // que ninguem consegue corrigir depois.
        val etapaFinal = if (RegrasDeMovimentacao.temFases(categoria)) etapa else null

        val salva = turmaRepository.save(
            turma.copy(
                categoria = categoria,
                etapa = etapaFinal,
                idComunidade = dto.idComunidade
            )
        )
        log.info(
            "Turma {} classificada como {} (fase {}) por '{}'",
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
    /**
     * Move a inscricao: para outra turma da paroquia, ou para fora dela.
     *
     * As regras de "pode ir para onde" vivem em RegrasDeMovimentacao, que e um
     * objeto puro. Aqui fica so o que precisa do banco: buscar, conferir a
     * permissao e gravar as duas pontas.
     *
     * Transferencia para OUTRA PAROQUIA nao cria inscricao nova: a inscricao
     * dele passa a ser de outro lugar. Guarda-se o nome da paroquia para que
     * "transferido" continue respondendo "para onde".
     */
    fun transferir(idMatricula: Long, dto: TransferenciaDTO): List<MatriculaAdminDTO> {
        exigirAdmin()
        val origem = exigirMatricula(idMatricula)
        val catequisando = origem.catequisando
            ?: throw OperacaoInvalidaException("Esta inscricao nao tem catequisando vinculado.")

        if (origem.situacao == SituacaoMatricula.TRANSFERIDO) {
            throw OperacaoInvalidaException("Esta inscricao ja foi transferida.")
        }

        val ano = origem.ano
        val data = dto.data ?: LocalDate.now()
        if (data.year != ano) {
            throw OperacaoInvalidaException(
                "A data da transferencia ($data) precisa ser do mesmo ano da inscricao ($ano)."
            )
        }

        val paroquia = dto.paroquiaDestino?.trim()?.ifBlank { null }
        val motivo = dto.motivo?.trim()?.ifBlank { null }
        val agora = LocalDateTime.now().withNano(0)

        // Os dois destinos sao exclusivos. Aceitar os dois deixaria a pessoa
        // com inscricao aqui E registrada como tendo saido.
        if (paroquia != null && dto.idTurmaDestino != null) {
            throw OperacaoInvalidaException(
                "Escolha um destino so: outra turma daqui, ou outra paroquia."
            )
        }

        // ---- saida para outra paroquia ------------------------------------
        if (paroquia != null) {
            val encerrada = matriculaRepository.save(
                origem.copy(
                    situacao = SituacaoMatricula.TRANSFERIDO,
                    paroquiaDestino = paroquia,
                    observacao = juntar(
                        origem.observacao,
                        "Transferido para a paroquia $paroquia em $data" +
                            (motivo?.let { ": $it" } ?: "")
                    ),
                    atualizadoEm = agora,
                    atualizadoPor = quem()
                )
            )
            log.info(
                "{} transferido para a paroquia '{}' em {} por '{}'",
                catequisando.nome, paroquia, data, quem()
            )
            return listOf(paraDTO(encerrada))
        }

        // ---- mudanca de turma dentro da paroquia --------------------------
        val idDestino = dto.idTurmaDestino
            ?: throw OperacaoInvalidaException(
                "Informe a turma de destino, ou o nome da paroquia para onde a pessoa foi."
            )
        val destino = exigirTurma(idDestino)

        if (matriculaRepository.existsByCatequisandoAndTurmaAndAno(catequisando, destino, ano)) {
            throw OperacaoInvalidaException(
                "${catequisando.nome} ja tem inscricao em ${destino.nome} em $ano."
            )
        }

        val veredito = RegrasDeMovimentacao.podeMover(
            origem = percursoDe(origem.turma),
            destino = percursoDe(destino),
            nascimento = catequisando.dataNascimento,
            dataMovimentacao = data,
            etapasDoCatecumenatoConcluidas = etapasConcluidasDe(catequisando)
        )
        if (!veredito.permitido) {
            throw OperacaoInvalidaException(veredito.motivo ?: "Movimentacao nao permitida.")
        }

        val encerrada = matriculaRepository.save(
            origem.copy(
                situacao = SituacaoMatricula.TRANSFERIDO,
                observacao = juntar(
                    origem.observacao,
                    "Transferido para ${destino.nome} em $data" + (motivo?.let { ": $it" } ?: "")
                ),
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

    /** Turma no formato que as regras entendem, sem arrastar a entidade. */
    private fun percursoDe(turma: Turma?): RegrasDeMovimentacao.Percurso =
        RegrasDeMovimentacao.Percurso(
            idTurma = turma?.idTurma ?: 0,
            nome = turma?.nome ?: "turma removida",
            categoria = turma?.categoria,
            etapa = turma?.etapa,
            idComunidade = turma?.idComunidade
        )

    /** Etapas do catecumenato que a pessoa ja FECHOU (tem data de fim). */
    private fun etapasConcluidasDe(catequisando: Catequisando): List<EtapaCatecumenato> =
        etapaCatecumenoRepository.findByCatequisandoOrderByDataInicioAsc(catequisando)
            .filter { it.dataFim != null }
            .map { it.etapa }

    private fun juntar(anterior: String?, novo: String): String =
        listOfNotNull(anterior, novo).joinToString(" | ")

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
            idComunidade = turma.idComunidade,
            nomeComunidade = turma.idComunidade
                ?.let { comunidadeRepository.findById(it).orElse(null)?.nome },
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
        atualizadoPor = matricula.atualizadoPor,
        paroquiaDestino = matricula.paroquiaDestino
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
