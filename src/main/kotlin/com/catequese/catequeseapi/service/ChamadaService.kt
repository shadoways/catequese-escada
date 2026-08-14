package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.AbrirEncontroDTO
import com.catequese.catequeseapi.dto.ChamadaDTO
import com.catequese.catequeseapi.dto.CorrecaoChamadaDTO
import com.catequese.catequeseapi.dto.EncontroDTO
import com.catequese.catequeseapi.dto.FinalizarEncontroDTO
import com.catequese.catequeseapi.dto.ItemChamadaDTO
import com.catequese.catequeseapi.dto.MarcarLoteDTO
import com.catequese.catequeseapi.dto.TurmaChamadaDTO
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.Encontro
import com.catequese.catequeseapi.model.Presenca
import com.catequese.catequeseapi.model.SituacaoEncontro
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.EncontroRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import com.catequese.catequeseapi.repository.PresencaRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A chamada do encontro.
 *
 * Regras que vieram do uso real, e nao da tecnica:
 *
 * - Nao se abre um encontro novo com o anterior ainda aberto. Sem isso, sobram
 *   listas em aberto de semanas atras e ninguem sabe qual vale.
 * - Encontro sem nenhum presente e um encontro que nao aconteceu: fechar assim
 *   exige motivo e o registro vira CANCELADO.
 * - Encontro cancelado nao entra em conta nenhuma. Feriado, chuva ou catequista
 *   doente nao podem virar falta de ninguem.
 * - Toda marcacao guarda quem marcou e quando.
 */
@Service
class ChamadaService(
    private val encontroRepository: EncontroRepository,
    private val presencaRepository: PresencaRepository,
    private val matriculaRepository: MatriculaRepository,
    private val turmaRepository: TurmaRepository,
    private val escopo: EscopoAcessoService
) {
    private val log = LoggerFactory.getLogger(ChamadaService::class.java)

    /** Erro de regra da chamada; o RestExceptionHandler traduz para 400. */
    class ChamadaInvalidaException(mensagem: String) : IllegalArgumentException(mensagem)

    // ---- Consulta ----------------------------------------------------------

    /**
     * As turmas em que este usuario pode fazer chamada.
     *
     * Catequista ve as turmas em que atua -- pelo vinculo novo
     * (tb_turma_catequista) e tambem pelo campo antigo de responsavel, senao
     * quem ainda nao foi migrado abriria a tela vazia. Coordenador ve as
     * turmas que tem alguem da comunidade dele. Administrador ve todas.
     */
    fun minhasTurmas(anoPedido: Int?): List<TurmaChamadaDTO> {
        val ano = anoPedido ?: LocalDate.now().year
        val turmasDoCatequista = escopo.turmasDoCatequista()
        val comunidades = escopo.comunidadesPermitidas()
        val idCatequistaDoUsuario = escopo.usuarioLogado()?.idCatequista

        return turmaRepository.findAll()
            .map { turma -> turma to matriculadosDaTurma(turma, ano) }
            .filter { (turma, matriculados) ->
                when {
                    turmasDoCatequista != null ->
                        turma.idTurma in turmasDoCatequista ||
                            (
                                idCatequistaDoUsuario != null &&
                                    turma.catequista?.idCatequista == idCatequistaDoUsuario
                                )

                    comunidades != null -> matriculados.any { catequisando ->
                        val idComunidade = catequisando.comunidade?.idComunidade
                        idComunidade != null && idComunidade in comunidades
                    }

                    else -> true
                }
            }
            .map { (turma, matriculados) ->
                val encontros = encontroRepository.findByTurmaOrderByDataDesc(turma)
                val aberto = encontros.firstOrNull { it.estaAberto() }
                val categoria = turma.categoria

                TurmaChamadaDTO(
                    idTurma = turma.idTurma,
                    nome = turma.nome,
                    categoria = categoria,
                    etapa = turma.etapa,
                    ano = ano,
                    matriculados = matriculados.size,
                    exigeFrequencia = categoria?.exigeFrequencia == true,
                    encontroAberto = aberto?.let { resumo(it, matriculados.size) },
                    ultimoEncontro = encontros.firstOrNull { !it.estaAberto() }?.data
                )
            }
            .sortedBy { it.nome.lowercase() }
    }

    fun encontrosDaTurma(idTurma: Long): List<EncontroDTO> {
        val turma = exigirTurma(idTurma)
        return encontroRepository.findByTurmaOrderByDataDesc(turma).map { resumo(it) }
    }

    fun chamada(idEncontro: Long): ChamadaDTO {
        val encontro = exigirEncontro(idEncontro)
        val matriculados = matriculadosDe(encontro)
        val presencas = presencaRepository.findByEncontro(encontro).associateBy {
            it.catequisando?.idCatequisando
        }

        val itens = matriculados.map { catequisando ->
            val marcacao = presencas[catequisando.idCatequisando]
            ItemChamadaDTO(
                idCatequisando = catequisando.idCatequisando,
                nome = catequisando.nome,
                situacao = marcacao?.situacao,
                justificativa = marcacao?.justificativa,
                marcadoPor = marcacao?.marcadoPor,
                marcadoEm = marcacao?.marcadoEm
            )
        }

        return ChamadaDTO(resumo(encontro, matriculados.size), itens)
    }

    // ---- Abertura ----------------------------------------------------------

    @Transactional
    fun abrir(dto: AbrirEncontroDTO, quem: String?): EncontroDTO {
        val turma = exigirTurma(dto.idTurma)
        val data = dto.data ?: LocalDate.now()

        if (data.isAfter(LocalDate.now())) {
            throw ChamadaInvalidaException("Nao da para abrir a chamada de um encontro futuro.")
        }

        // Se ja existe encontro nessa data, o certo e continuar aquele.
        encontroRepository.findByTurmaAndData(turma, data)?.let { existente ->
            if (existente.estaAberto()) return resumo(existente)
            throw ChamadaInvalidaException(
                "Ja existe um encontro registrado nesta turma em ${data}, e ele ja foi encerrado."
            )
        }

        // Um aberto por vez: senao sobram listas antigas em aberto.
        encontroRepository.findFirstByTurmaAndSituacao(turma, SituacaoEncontro.ABERTO)
            ?.let { aberto ->
                throw ChamadaInvalidaException(
                    "Ha um encontro de ${aberto.data} ainda aberto nesta turma. " +
                        "Encerre aquele antes de abrir um novo."
                )
            }

        val encontro = encontroRepository.save(
            Encontro(
                turma = turma,
                data = data,
                tema = dto.tema?.trim()?.ifBlank { null },
                situacao = SituacaoEncontro.ABERTO,
                abertoPor = quem,
                abertoEm = LocalDateTime.now().withNano(0)
            )
        )

        log.info("Encontro {} aberto na turma {} por '{}'", data, turma.nome, quem ?: "?")
        return resumo(encontro)
    }

    // ---- Marcacao ----------------------------------------------------------

    @Transactional
    fun marcar(idEncontro: Long, dto: MarcarLoteDTO, quem: String?): ChamadaDTO {
        val encontro = exigirEncontro(idEncontro)
        exigirEditavel(encontro)

        val matriculados = matriculadosDe(encontro).associateBy { it.idCatequisando }
        val existentes = presencaRepository.findByEncontro(encontro).associateBy {
            it.catequisando?.idCatequisando
        }
        val agora = LocalDateTime.now().withNano(0)

        val paraGravar = dto.marcacoes.map { marcacao ->
            val catequisando = matriculados[marcacao.idCatequisando]
                ?: throw ChamadaInvalidaException(
                    "Catequisando ${marcacao.idCatequisando} nao esta matriculado nesta turma."
                )

            val justificativa = marcacao.justificativa?.trim()?.ifBlank { null }
            if (marcacao.situacao == SituacaoPresenca.JUSTIFICADA && justificativa == null) {
                throw ChamadaInvalidaException(
                    "Informe o motivo da falta justificada de ${catequisando.nome}."
                )
            }

            val anterior = existentes[marcacao.idCatequisando]
            (anterior ?: novaPresenca(encontro, catequisando)).copy(
                situacao = marcacao.situacao,
                justificativa = justificativa,
                presente = marcacao.situacao == SituacaoPresenca.PRESENTE,
                marcadoPor = quem,
                marcadoEm = agora
            )
        }

        if (paraGravar.isNotEmpty()) presencaRepository.saveAll(paraGravar)
        return chamada(idEncontro)
    }

    // ---- Encerramento ------------------------------------------------------

    @Transactional
    fun fechar(idEncontro: Long, dto: FinalizarEncontroDTO, quem: String?): EncontroDTO {
        val encontro = exigirEncontro(idEncontro)
        exigirEditavel(encontro)

        val matriculados = matriculadosDe(encontro)
        val presencas = presencaRepository.findByEncontro(encontro)
        val presentes = presencas.count { it.compareceu() }
        val motivo = dto.motivo?.trim()?.ifBlank { null }

        // Ninguem presente = o encontro nao aconteceu. Vira cancelamento, e
        // cancelamento exige motivo -- senao ficaria um buraco inexplicado no
        // historico da turma.
        if (presentes == 0) {
            if (motivo == null) {
                throw ChamadaInvalidaException(
                    "Nenhuma presenca foi marcada. Se o encontro nao aconteceu, " +
                        "informe o motivo para registrar o cancelamento."
                )
            }
            return cancelarInterno(encontro, motivo, quem, automatico = false)
        }

        // Quem nao foi marcado fica como falta: a lista fechada precisa estar completa.
        completarFaltas(encontro, matriculados, presencas, quem)

        val fechado = encontroRepository.save(
            encontro.copy(
                situacao = SituacaoEncontro.FECHADO,
                tema = dto.tema?.trim()?.ifBlank { null } ?: encontro.tema,
                fechadoPor = quem,
                fechadoEm = LocalDateTime.now().withNano(0),
                fechamentoAutomatico = false
            )
        )

        log.info(
            "Encontro {} da turma {} fechado por '{}' com {} presente(s)",
            fechado.data, fechado.turma?.nome, quem ?: "?", presentes
        )
        return resumo(fechado, matriculados.size)
    }

    @Transactional
    fun cancelar(idEncontro: Long, dto: FinalizarEncontroDTO, quem: String?): EncontroDTO {
        val encontro = exigirEncontro(idEncontro)
        exigirEditavel(encontro)

        val motivo = dto.motivo?.trim()?.ifBlank { null }
            ?: throw ChamadaInvalidaException("Informe o motivo do cancelamento.")

        return cancelarInterno(encontro, motivo, quem, automatico = false)
    }

    /**
     * Correcao de chamada JA ENCERRADA. So administrador.
     *
     * Existe em vez de obrigar a sequencia reabrir -> marcar -> fechar por dois
     * motivos: e uma transacao so, entao nao ha como o encontro ficar aberto
     * por acidente no meio do caminho; e o motivo passa a ser obrigatorio.
     * Mexer em lista fechada muda a frequencia de alguem, e daqui a seis meses
     * ninguem vai lembrar por que aquele numero mudou.
     */
    @Transactional
    fun corrigir(idEncontro: Long, dto: CorrecaoChamadaDTO, quem: String?): ChamadaDTO {
        if (!escopo.ehAdmin()) {
            throw ChamadaInvalidaException(
                "Somente o coordenador paroquial pode corrigir uma chamada encerrada."
            )
        }

        val motivo = dto.motivo?.trim()?.ifBlank { null }
            ?: throw ChamadaInvalidaException(
                "Informe o motivo da correcao. Ele fica registrado no historico do encontro."
            )

        val encontro = exigirEncontro(idEncontro)
        if (encontro.estaAberto()) {
            throw ChamadaInvalidaException(
                "Este encontro ainda esta aberto: use a chamada normal em vez da correcao."
            )
        }
        if (dto.correcoes.isEmpty()) {
            throw ChamadaInvalidaException("Nenhuma correcao foi informada.")
        }

        val matriculados = matriculadosDe(encontro).associateBy { it.idCatequisando }
        val existentes = presencaRepository.findByEncontro(encontro).associateBy {
            it.catequisando?.idCatequisando
        }
        val agora = LocalDateTime.now().withNano(0)
        // O autor fica marcado como correcao para o historico nao confundir
        // com a marcacao original feita pelo catequista no dia.
        val autor = "${quem ?: AUTOR_SISTEMA} (correcao)"

        val paraGravar = dto.correcoes.map { correcao ->
            val catequisando = matriculados[correcao.idCatequisando]
                ?: throw ChamadaInvalidaException(
                    "Catequisando ${correcao.idCatequisando} nao esta matriculado nesta turma."
                )

            val justificativa = correcao.justificativa?.trim()?.ifBlank { null }
            if (correcao.situacao == SituacaoPresenca.JUSTIFICADA && justificativa == null) {
                throw ChamadaInvalidaException(
                    "Informe o motivo da falta justificada de ${catequisando.nome}."
                )
            }

            val anterior = existentes[correcao.idCatequisando]
            (anterior ?: novaPresenca(encontro, catequisando)).copy(
                situacao = correcao.situacao,
                justificativa = justificativa,
                presente = correcao.situacao == SituacaoPresenca.PRESENTE,
                marcadoPor = autor,
                marcadoEm = agora
            )
        }

        presencaRepository.saveAll(paraGravar)

        log.warn(
            "CORRECAO na chamada de {} da turma {} por '{}' ({} registro(s)). Motivo: {}",
            encontro.data, encontro.turma?.nome, quem ?: "?", paraGravar.size, motivo
        )
        return chamada(idEncontro)
    }

    /** Reabertura para corrigir engano. So administrador. */
    @Transactional
    fun reabrir(idEncontro: Long, quem: String?): EncontroDTO {
        if (!escopo.ehAdmin()) {
            throw ChamadaInvalidaException(
                "Somente o coordenador paroquial pode reabrir um encontro encerrado."
            )
        }

        val encontro = exigirEncontro(idEncontro)
        if (encontro.estaAberto()) return resumo(encontro)

        val reaberto = encontroRepository.save(
            encontro.copy(
                situacao = SituacaoEncontro.ABERTO,
                motivoCancelamento = null,
                fechadoPor = null,
                fechadoEm = null,
                fechamentoAutomatico = false
            )
        )
        log.warn(
            "Encontro {} da turma {} REABERTO por '{}'",
            reaberto.data, reaberto.turma?.nome, quem ?: "?"
        )
        return resumo(reaberto)
    }

    /**
     * Versao para disparo manual pela API: exige administrador.
     * A rotina noturna usa `fecharEsquecidos`, que roda sem usuario logado.
     */
    @Transactional
    fun fecharEsquecidosPeloAdmin(): Int {
        if (!escopo.ehAdmin()) {
            throw ChamadaInvalidaException(
                "Somente o coordenador paroquial pode disparar o fechamento em lote."
            )
        }
        return fecharEsquecidos()
    }

    /**
     * Fecha o que ficou aberto. Chamado pela rotina diaria, sem usuario logado.
     * O encontro do dia D vale durante D e D+1; na virada para D+2, encerra.
     */
    @Transactional
    fun fecharEsquecidos(hoje: LocalDate = LocalDate.now()): Int {
        val limite = hoje.minusDays(1)
        val esquecidos = encontroRepository.findAllBySituacaoAndDataBefore(
            SituacaoEncontro.ABERTO, limite
        )
        if (esquecidos.isEmpty()) return 0

        esquecidos.forEach { encontro ->
            val presencas = presencaRepository.findByEncontro(encontro)
            if (presencas.none { it.compareceu() }) {
                cancelarInterno(
                    encontro,
                    "Encerrado automaticamente: a chamada nao foi enviada e nenhuma " +
                        "presenca havia sido marcada.",
                    quem = null,
                    automatico = true
                )
            } else {
                completarFaltas(encontro, matriculadosDe(encontro), presencas, quem = null)
                encontroRepository.save(
                    encontro.copy(
                        situacao = SituacaoEncontro.FECHADO,
                        fechadoPor = AUTOR_SISTEMA,
                        fechadoEm = LocalDateTime.now().withNano(0),
                        fechamentoAutomatico = true
                    )
                )
            }
        }

        log.info("Fechamento automatico: {} encontro(s) encerrados.", esquecidos.size)
        return esquecidos.size
    }

    // ---- Apoio -------------------------------------------------------------

    private fun cancelarInterno(
        encontro: Encontro,
        motivo: String,
        quem: String?,
        automatico: Boolean
    ): EncontroDTO {
        val cancelado = encontroRepository.save(
            encontro.copy(
                situacao = SituacaoEncontro.CANCELADO,
                motivoCancelamento = motivo,
                fechadoPor = quem ?: AUTOR_SISTEMA,
                fechadoEm = LocalDateTime.now().withNano(0),
                fechamentoAutomatico = automatico
            )
        )
        log.info(
            "Encontro {} da turma {} cancelado por '{}': {}",
            cancelado.data, cancelado.turma?.nome, quem ?: AUTOR_SISTEMA, motivo
        )
        return resumo(cancelado)
    }

    /** Fecha a lista: quem nao foi marcado vira falta. */
    private fun completarFaltas(
        encontro: Encontro,
        matriculados: List<Catequisando>,
        presencas: List<Presenca>,
        quem: String?
    ) {
        val jaMarcados = presencas.mapNotNull { it.catequisando?.idCatequisando }.toSet()
        val agora = LocalDateTime.now().withNano(0)

        val faltantes = matriculados
            .filter { it.idCatequisando !in jaMarcados }
            .map {
                novaPresenca(encontro, it).copy(
                    situacao = SituacaoPresenca.FALTA,
                    presente = false,
                    marcadoPor = quem ?: AUTOR_SISTEMA,
                    marcadoEm = agora
                )
            }

        if (faltantes.isNotEmpty()) presencaRepository.saveAll(faltantes)
    }

    private fun novaPresenca(encontro: Encontro, catequisando: Catequisando) = Presenca(
        data = encontro.data,
        presente = false,
        catequisando = catequisando,
        encontro = encontro
    )

    /** Quem esta na turma no ano do encontro, sem os que sairam. */
    private fun matriculadosDe(encontro: Encontro): List<Catequisando> {
        val turma = encontro.turma ?: return emptyList()
        val ano = encontro.data?.year ?: LocalDate.now().year
        return matriculadosDaTurma(turma, ano)
    }

    private fun matriculadosDaTurma(turma: Turma, ano: Int): List<Catequisando> =
        matriculaRepository.findByTurmaAndAno(turma, ano)
            .filter { it.situacao != SituacaoMatricula.TRANSFERIDO }
            .filter { it.situacao != SituacaoMatricula.DESISTENTE }
            .mapNotNull { it.catequisando }
            .sortedBy { it.nome.lowercase() }

    private fun resumo(encontro: Encontro, totalMatriculados: Int? = null): EncontroDTO {
        val presencas = presencaRepository.findByEncontro(encontro)
        return EncontroDTO.de(
            encontro = encontro,
            presentes = presencas.count { it.situacao == SituacaoPresenca.PRESENTE },
            faltas = presencas.count { it.situacao == SituacaoPresenca.FALTA },
            justificadas = presencas.count { it.situacao == SituacaoPresenca.JUSTIFICADA },
            totalMatriculados = totalMatriculados ?: matriculadosDe(encontro).size,
            editavel = encontro.estaAberto()
        )
    }

    private fun exigirEditavel(encontro: Encontro) {
        if (encontro.estaAberto()) return
        throw ChamadaInvalidaException(
            "Este encontro ja foi encerrado e nao aceita mais alteracao. " +
                "Peca ao coordenador paroquial para reabri-lo."
        )
    }

    private fun exigirTurma(idTurma: Long): Turma {
        val turma = turmaRepository.findById(idTurma)
            .orElseThrow { ResourceNotFoundException("Turma nao encontrada") }
        exigirAcessoATurma(turma)
        return turma
    }

    private fun exigirEncontro(idEncontro: Long): Encontro {
        val encontro = encontroRepository.findById(idEncontro)
            .orElseThrow { ResourceNotFoundException("Encontro nao encontrado") }
        encontro.turma?.let { exigirAcessoATurma(it) }
        return encontro
    }

    /**
     * Catequista so mexe nas turmas em que atua. Coordenador e admin passam
     * por aqui livremente -- o recorte por comunidade deles e aplicado na
     * listagem de turmas.
     */
    private fun exigirAcessoATurma(turma: Turma) {
        val turmasDoCatequista = escopo.turmasDoCatequista() ?: return
        if (turma.idTurma in turmasDoCatequista) return

        throw ChamadaInvalidaException("Voce nao atua nesta turma.")
    }

    private companion object {
        const val AUTOR_SISTEMA = "sistema"
    }
}
