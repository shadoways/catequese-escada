package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.AgendaDTO
import com.catequese.catequeseapi.dto.ChecagemConflitoDTO
import com.catequese.catequeseapi.dto.EventoAgendaDTO
import com.catequese.catequeseapi.dto.EventoFormDTO
import com.catequese.catequeseapi.dto.FrequenciaFormacaoDTO
import com.catequese.catequeseapi.dto.OpcaoDTO
import com.catequese.catequeseapi.dto.OpcoesAgendaDTO
import com.catequese.catequeseapi.dto.ResumoAgendaDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ConflitoAgendaException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.model.TipoEvento
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.FormacaoRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * A agenda da catequese: listagem, cadastro e edicao dos eventos.
 *
 * A permissao mora em AgendaPermissaoService e e chamada aqui em todo caminho
 * de escrita -- inclusive nos que a tela ja esconderia, porque esconder botao
 * nao impede ninguem de chamar a API direto.
 */
@Service
class AgendaService(
    private val eventoRepository: EventoRepository,
    private val formacaoRepository: FormacaoRepository,
    private val turmaRepository: TurmaRepository,
    private val comunidadeRepository: ComunidadeRepository,
    private val permissao: AgendaPermissaoService,
    private val conflito: ConflitoAgendaService,
    private val escopo: EscopoAcessoService,
    private val frequenciaFormacao: FrequenciaFormacaoService
) {

    fun agendaDoAno(ano: Int?): AgendaDTO {
        val anoAlvo = ano ?: LocalDate.now().year
        val doAno = eventoRepository
            .findNoPeriodo(LocalDate.of(anoAlvo, 1, 1), LocalDate.of(anoAlvo, 12, 31))

        /*
         * A frequencia e calculada UMA vez por formacao, e nao uma vez por
         * encontro. Uma trilha de 8 encontros produziria 8 apuracoes
         * identicas -- cada uma varrendo os encontros e as marcacoes da mesma
         * formacao de novo.
         */
        val cache = doAno.mapNotNull { it.idFormacao }
            .distinct()
            .mapNotNull { id -> frequenciaFormacao.minhaFrequencia(id)?.let { id to it } }
            .toMap()

        val eventos = doAno.map { paraDTO(it, cache) }
        return AgendaDTO(ano = anoAlvo, resumo = montarResumo(eventos), eventos = eventos)
    }

    fun porId(id: Long): EventoAgendaDTO = paraDTO(buscar(id))

    @Transactional
    fun criar(form: EventoFormDTO): EventoAgendaDTO {
        val usuario = escopo.usuarioLogado()
            ?: throw AcessoNegadoException("Sessao expirada. Entre de novo para cadastrar um evento.")

        val evento = aplicarForm(Evento(titulo = ""), form).copy(
            criadoPor = usuario.username,
            criadoEm = LocalDateTime.now()
        )

        if (!permissao.podeEditar(evento, usuario)) {
            throw AcessoNegadoException(mensagemDeRecusa(evento.nivel))
        }

        exigirAgendaLivre(evento, ignorarId = null, confirmou = form.confirmarConflito)

        return paraDTO(eventoRepository.save(evento))
    }

    @Transactional
    fun atualizar(id: Long, form: EventoFormDTO): EventoAgendaDTO {
        val usuario = escopo.usuarioLogado()
            ?: throw AcessoNegadoException("Sessao expirada. Entre de novo para alterar o evento.")

        val atual = buscar(id)

        // Checa ANTES e DEPOIS: sem o segundo teste, quem pode editar um evento
        // da propria turma poderia "promove-lo" a paroquial e passar a mandar
        // num evento que nunca foi dele.
        if (!permissao.podeEditar(atual, usuario)) {
            throw AcessoNegadoException("Este evento nao e seu para alterar.")
        }

        val alterado = aplicarForm(atual, form).copy(
            alteradoPor = usuario.username,
            alteradoEm = LocalDateTime.now()
        )

        if (!permissao.podeEditar(alterado, usuario)) {
            throw AcessoNegadoException(mensagemDeRecusa(alterado.nivel))
        }

        exigirAgendaLivre(alterado, ignorarId = id, confirmou = form.confirmarConflito)

        return paraDTO(eventoRepository.save(alterado))
    }

    /** Checagem prévia para a tela avisar antes de a pessoa clicar em Salvar. */
    fun checarConflito(
        data: LocalDate,
        nivel: String,
        idComunidade: Long?,
        idTurma: Long?,
        ignorarId: Long?
    ): ChecagemConflitoDTO {
        val nivelEnum = runCatching { NivelEvento.valueOf(nivel) }
            .getOrElse { throw IllegalArgumentException("Nivel invalido: $nivel") }

        val conflitos = conflito.conflitosPara(data, nivelEnum, idComunidade, idTurma, ignorarId)
        return ChecagemConflitoDTO(temConflito = conflitos.isNotEmpty(), conflitos = conflitos)
    }

    /**
     * Recusa a gravacao quando ja ha evento disputando o mesmo publico, a nao
     * ser que o usuario tenha visto a lista e confirmado.
     *
     * Nao e um bloqueio absoluto de proposito: existe caso legitimo de dois
     * eventos no mesmo dia para o mesmo publico (a missa de manha e o retiro a
     * tarde). Travar sem saida transformaria a regra num estorvo e levaria
     * alguem a cadastrar com data errada so para conseguir salvar -- o que e
     * pior do que o conflito que se queria evitar.
     */
    private fun exigirAgendaLivre(evento: Evento, ignorarId: Long?, confirmou: Boolean) {
        if (confirmou) return

        val conflitos = conflito.conflitosDe(evento, ignorarId)
        if (conflitos.isEmpty()) return

        val quantos = if (conflitos.size == 1) "Já existe um evento"
        else "Já existem ${conflitos.size} eventos"

        throw ConflitoAgendaException(
            "$quantos marcado(s) para esta data atingindo as mesmas pessoas.",
            conflitos
        )
    }

    @Transactional
    fun excluir(id: Long) {
        val evento = buscar(id)
        if (!permissao.podeEditar(evento)) {
            throw AcessoNegadoException("Este evento nao e seu para excluir.")
        }
        eventoRepository.delete(evento)
    }

    fun opcoes(): OpcoesAgendaDTO {
        val usuario = escopo.usuarioLogado()
        val niveis = permissao.niveisQuePodeCriar(usuario)

        val comunidades = comunidadeRepository.findAll()
            .filter { it.ativo }
            .sortedBy { it.nome }
            .map { OpcaoDTO(it.idComunidade.toString(), it.nome) }

        // O catequista so escolhe entre as turmas dele: oferecer as demais so
        // produziria um erro depois de o formulario ja estar preenchido.
        val minhasTurmas = escopo.turmasDoCatequista()
        val turmas = turmaRepository.findAll()
            .filter { minhasTurmas == null || it.idTurma in minhasTurmas }
            .sortedBy { it.nome }
            .map { OpcaoDTO(it.idTurma.toString(), it.nome) }

        val formacoes = formacaoRepository.findAll()
            .sortedByDescending { it.ano ?: 0 }
            .map { OpcaoDTO(it.idFormacao.toString(), nomeDaFormacao(it.nome, it.ano)) }

        return OpcoesAgendaDTO(
            niveisQuePodeCriar = niveis.map { OpcaoDTO(it.name, it.rotulo) },
            tipos = TipoEvento.entries.map { OpcaoDTO(it.name, it.rotulo) },
            comunidades = comunidades,
            turmas = turmas,
            formacoes = formacoes,
            podeCriar = niveis.isNotEmpty()
        )
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private fun buscar(id: Long): Evento = eventoRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("Evento nao encontrado") }

    private fun nomeDaFormacao(nome: String, ano: Int?): String =
        if (ano == null) nome else "$nome ($ano)"

    private fun mensagemDeRecusa(nivel: NivelEvento?): String = when (nivel) {
        null -> "Escolha o nivel do evento."
        NivelEvento.DIOCESANO, NivelEvento.REGIONAL, NivelEvento.PAROQUIAL ->
            "Evento ${nivel.rotulo.lowercase()} so pode ser cadastrado pelo coordenador paroquial."
        NivelEvento.COMUNIDADE -> "Voce so cadastra eventos da sua comunidade."
        NivelEvento.TURMA -> "Voce so cadastra eventos das turmas em que atua."
    }

    /**
     * Copia o formulario sobre o evento, validando o que o enum nao garante.
     *
     * A validacao de coerencia (nivel COMUNIDADE exige comunidade, TURMA exige
     * turma) fica aqui e nao na entidade porque e regra de entrada: os
     * registros antigos, migrados, podem legitimamente estar sem nivel.
     */
    private fun aplicarForm(base: Evento, form: EventoFormDTO): Evento {
        val titulo = form.titulo?.trim().orEmpty()
        require(titulo.isNotEmpty()) { "Informe o titulo do evento." }

        val nivel = form.nivel?.takeIf { it.isNotBlank() }?.let { valor ->
            runCatching { NivelEvento.valueOf(valor) }
                .getOrElse { throw IllegalArgumentException("Nivel invalido: $valor") }
        } ?: throw IllegalArgumentException("Escolha o nivel do evento.")

        val tipo = form.tipo?.takeIf { it.isNotBlank() }?.let { valor ->
            runCatching { TipoEvento.valueOf(valor) }
                .getOrElse { throw IllegalArgumentException("Tipo invalido: $valor") }
        } ?: TipoEvento.ENCONTRO

        val situacao = form.situacao?.takeIf { it.isNotBlank() }?.let { valor ->
            runCatching { SituacaoEvento.valueOf(valor) }
                .getOrElse { throw IllegalArgumentException("Situacao invalida: $valor") }
        } ?: base.situacao

        val dataInicio = form.dataInicio
            ?: throw IllegalArgumentException("Informe a data do evento.")

        if (form.dataFim != null && form.dataFim.isBefore(dataInicio)) {
            throw IllegalArgumentException("A data de termino nao pode ser anterior a de inicio.")
        }

        // Zera o vinculo que nao pertence ao nivel escolhido. Sem isso, mudar
        // um evento de TURMA para PAROQUIAL deixaria o id_turma antigo gravado
        // e a permissao passaria a olhar uma turma que nao tem mais relacao.
        val idComunidade = if (nivel.exigeComunidade) {
            form.idComunidade ?: throw IllegalArgumentException("Escolha a comunidade do evento.")
        } else null

        val idTurma = if (nivel.exigeTurma) {
            form.idTurma ?: throw IllegalArgumentException("Escolha a turma do evento.")
        } else null

        if (idTurma != null && !turmaRepository.existsById(idTurma)) {
            throw IllegalArgumentException("Turma nao encontrada.")
        }
        if (idComunidade != null && !comunidadeRepository.existsById(idComunidade)) {
            throw IllegalArgumentException("Comunidade nao encontrada.")
        }

        val idFormacao = form.idFormacao?.takeIf { tipo == TipoEvento.FORMACAO }
        if (tipo == TipoEvento.FORMACAO && idFormacao == null) {
            throw IllegalArgumentException(
                "Um encontro de formacao precisa dizer a qual formacao pertence."
            )
        }
        if (idFormacao != null && !formacaoRepository.existsById(idFormacao)) {
            throw IllegalArgumentException("Formacao nao encontrada.")
        }

        if (situacao == SituacaoEvento.CANCELADO && form.motivoCancelamento.isNullOrBlank()) {
            throw IllegalArgumentException("Evento cancelado precisa de motivo registrado.")
        }

        return base.copy(
            titulo = titulo,
            tipo = tipo,
            nivel = nivel,
            idComunidade = idComunidade,
            idTurma = idTurma,
            idFormacao = idFormacao,
            descricao = form.descricao?.trim()?.takeIf { it.isNotEmpty() },
            dataInicio = dataInicio,
            dataFim = form.dataFim,
            horaInicio = form.horaInicio?.trim()?.takeIf { it.isNotEmpty() },
            local = form.local?.trim()?.takeIf { it.isNotEmpty() },
            situacao = situacao,
            motivoCancelamento = form.motivoCancelamento?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * @param cacheFrequencia frequencias ja apuradas por formacao. Vazio quando
     *   se converte um evento avulso -- ai apura na hora.
     */
    @JvmOverloads
    fun paraDTO(
        evento: Evento,
        cacheFrequencia: Map<Long, FrequenciaFormacaoDTO> = emptyMap()
    ): EventoAgendaDTO {
        val formacao = evento.idFormacao?.let { formacaoRepository.findById(it).orElse(null) }

        return EventoAgendaDTO(
            idEvento = evento.idEvento,
            titulo = evento.titulo,
            tipo = evento.tipo,
            tipoRotulo = evento.tipo.rotulo,
            nivel = evento.nivel,
            nivelRotulo = evento.nivel?.rotulo,
            idComunidade = evento.idComunidade,
            comunidadeNome = evento.idComunidade
                ?.let { comunidadeRepository.findById(it).orElse(null)?.nome },
            idTurma = evento.idTurma,
            turmaNome = evento.idTurma
                ?.let { turmaRepository.findById(it).orElse(null)?.nome },
            idFormacao = evento.idFormacao,
            formacaoNome = formacao?.nome,
            descricao = evento.descricao,
            dataInicio = evento.dataInicio,
            dataFim = evento.dataFim,
            horaInicio = evento.horaInicio,
            local = evento.local,
            situacao = evento.situacao,
            motivoCancelamento = evento.motivoCancelamento,
            podeEditar = permissao.podeEditar(evento),
            minhaFrequencia = evento.idFormacao?.let {
                cacheFrequencia[it] ?: frequenciaFormacao.minhaFrequencia(it)
            }
        )
    }

    private fun montarResumo(eventos: List<EventoAgendaDTO>): ResumoAgendaDTO {
        val hoje = LocalDate.now()
        val minhasTurmas = escopo.turmasDoCatequista().orEmpty()

        val proximo = eventos
            .filter { it.situacao != SituacaoEvento.CANCELADO }
            .filter { it.dataInicio != null && !it.dataInicio.isBefore(hoje) }
            .minByOrNull { it.dataInicio!! }

        // Uma linha por formacao: o mesmo percentual repetido em cada encontro
        // da trilha nao acrescenta nada na faixa de resumo.
        val emRisco = eventos
            .mapNotNull { it.minhaFrequencia }
            .distinctBy { it.idFormacao }
            .filter { it.percentual != null && !it.atingiuMinimo }

        return ResumoAgendaDTO(
            proximoEvento = proximo,
            formacoesEmRisco = emRisco,
            eventosDasMinhasTurmas = eventos.count { it.idTurma != null && it.idTurma in minhasTurmas },
            totalNoAno = eventos.size
        )
    }
}
