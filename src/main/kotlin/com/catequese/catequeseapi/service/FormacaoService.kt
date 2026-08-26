package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.ChamadaFormacaoDTO
import com.catequese.catequeseapi.dto.FormacaoDetalheDTO
import com.catequese.catequeseapi.dto.FormacaoFormDTO
import com.catequese.catequeseapi.dto.FormacaoResumoDTO
import com.catequese.catequeseapi.dto.InscritoFormacaoDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Formacao
import com.catequese.catequeseapi.model.FormacaoInscrito
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.PresencaFormacao
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.model.SituacaoFormacao
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.repository.CatequistaRepository
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.FormacaoInscritoRepository
import com.catequese.catequeseapi.repository.FormacaoRepository
import com.catequese.catequeseapi.repository.PresencaFormacaoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Trilhas de formacao de catequistas e a chamada delas.
 *
 * Formacao existe do nivel paroquial para cima, e todos esses niveis sao do
 * coordenador paroquial -- por isso a gestao aqui e sempre de admin, sem a
 * matriz de cinco niveis que a agenda tem. A checagem mesmo assim e feita no
 * servico: /api/formacoes nao esta sob /api/admin, e depender so da rota
 * deixaria a regra num arquivo de configuracao longe daqui.
 */
@Service
class FormacaoService(
    private val formacaoRepository: FormacaoRepository,
    private val inscritoRepository: FormacaoInscritoRepository,
    private val presencaRepository: PresencaFormacaoRepository,
    private val eventoRepository: EventoRepository,
    private val catequistaRepository: CatequistaRepository,
    private val frequencia: FrequenciaFormacaoService,
    private val agenda: AgendaService,
    private val escopo: EscopoAcessoService
) {

    fun listar(ano: Int?): List<FormacaoResumoDTO> {
        val todas = if (ano == null) formacaoRepository.findAll()
        else formacaoRepository.findByAnoOrderByNomeAsc(ano)

        return todas
            .sortedWith(compareByDescending<Formacao> { it.ano ?: 0 }.thenBy { it.nome })
            .map { resumo(it) }
    }

    fun detalhe(id: Long): FormacaoDetalheDTO {
        val formacao = buscar(id)
        val encontros = eventoRepository
            .findByIdFormacaoOrderByDataInicioAsc(id)
            .map { agenda.paraDTO(it) }

        val inscritos = inscritoRepository.findByIdFormacao(id)
            .mapNotNull { inscrito ->
                val catequista = catequistaRepository.findById(inscrito.idCatequista).orElse(null)
                    ?: return@mapNotNull null
                val freq = frequencia.calcular(id, inscrito.idCatequista)
                    ?: return@mapNotNull null
                InscritoFormacaoDTO(
                    idCatequista = inscrito.idCatequista,
                    nome = catequista.nome,
                    frequencia = freq
                )
            }
            .sortedBy { it.nome.lowercase() }

        return FormacaoDetalheDTO(
            formacao = resumo(formacao),
            encontros = encontros,
            inscritos = inscritos
        )
    }

    @Transactional
    fun criar(form: FormacaoFormDTO): FormacaoResumoDTO {
        exigirAdmin()
        val usuario = escopo.usuarioLogado()
        val nova = aplicar(Formacao(nome = ""), form).copy(
            criadoPor = usuario?.username,
            criadoEm = LocalDateTime.now()
        )
        return resumo(formacaoRepository.save(nova))
    }

    @Transactional
    fun atualizar(id: Long, form: FormacaoFormDTO): FormacaoResumoDTO {
        exigirAdmin()
        return resumo(formacaoRepository.save(aplicar(buscar(id), form)))
    }

    @Transactional
    fun excluir(id: Long) {
        exigirAdmin()
        val encontros = eventoRepository.findByIdFormacaoOrderByDataInicioAsc(id)
        if (encontros.isNotEmpty()) {
            throw IllegalArgumentException(
                "Esta formacao tem ${encontros.size} encontro(s) na agenda. " +
                    "Remova os encontros antes, ou encerre a formacao em vez de exclui-la."
            )
        }
        inscritoRepository.findByIdFormacao(id).forEach { inscritoRepository.delete(it) }
        formacaoRepository.deleteById(id)
    }

    @Transactional
    fun inscrever(idFormacao: Long, idCatequista: Long) {
        exigirAdmin()
        buscar(idFormacao)
        if (!catequistaRepository.existsById(idCatequista)) {
            throw ResourceNotFoundException("Catequista nao encontrado")
        }
        if (inscritoRepository.existsByIdFormacaoAndIdCatequista(idFormacao, idCatequista)) return

        inscritoRepository.save(
            FormacaoInscrito(
                idFormacao = idFormacao,
                idCatequista = idCatequista,
                inscritoEm = LocalDateTime.now()
            )
        )
    }

    @Transactional
    fun desinscrever(idFormacao: Long, idCatequista: Long) {
        exigirAdmin()
        inscritoRepository.deleteByIdFormacaoAndIdCatequista(idFormacao, idCatequista)
    }

    /**
     * Chamada de um encontro de formacao.
     *
     * Substitui as marcacoes daquele encontro em vez de somar: refazer a
     * chamada e o caso normal (alguem chegou atrasado), e acumular geraria
     * duas marcacoes para a mesma pessoa.
     */
    @Transactional
    fun registrarChamada(idEvento: Long, chamada: ChamadaFormacaoDTO) {
        exigirAdmin()

        val evento = eventoRepository.findById(idEvento)
            .orElseThrow { ResourceNotFoundException("Encontro nao encontrado") }

        val idFormacao = evento.idFormacao
            ?: throw IllegalArgumentException("Este evento nao e um encontro de formacao.")

        val inscritos = inscritoRepository.findByIdFormacao(idFormacao)
            .map { it.idCatequista }
            .toSet()

        val usuario = escopo.usuarioLogado()
        val agora = LocalDateTime.now()

        /*
         * Apaga E DESCARREGA antes de inserir. Sem o flush explicito o
         * Hibernate pode reordenar e mandar os inserts antes dos deletes na
         * mesma transacao, o que deixaria duas marcacoes para a mesma pessoa
         * no mesmo encontro -- e a frequencia passaria a contar em dobro.
         */
        presencaRepository.deleteAll(presencaRepository.findByIdEvento(idEvento))
        presencaRepository.flush()

        val novas = chamada.marcacoes.map { marcacao ->
            if (marcacao.idCatequista !in inscritos) {
                throw IllegalArgumentException(
                    "Ha marcacao para um catequista que nao esta inscrito nesta formacao."
                )
            }

            val situacao = runCatching { SituacaoPresenca.valueOf(marcacao.situacao) }
                .getOrElse { throw IllegalArgumentException("Situacao invalida: ${marcacao.situacao}") }

            if (situacao == SituacaoPresenca.JUSTIFICADA && marcacao.justificativa.isNullOrBlank()) {
                throw IllegalArgumentException("Falta justificada precisa de motivo.")
            }

            PresencaFormacao(
                idEvento = idEvento,
                idCatequista = marcacao.idCatequista,
                situacao = situacao,
                justificativa = marcacao.justificativa?.trim()?.takeIf { it.isNotEmpty() },
                marcadoPor = usuario?.username,
                marcadoEm = agora
            )
        }

        presencaRepository.saveAll(novas)

        // A chamada so vale depois que o encontro e dado como realizado -- e
        // fazer a chamada e justamente o sinal de que ele aconteceu.
        if (evento.situacao == SituacaoEvento.PREVISTO) {
            eventoRepository.save(
                evento.copy(
                    situacao = SituacaoEvento.REALIZADO,
                    alteradoPor = usuario?.username,
                    alteradoEm = agora
                )
            )
        }
    }

    fun marcacoesDoEncontro(idEvento: Long): List<PresencaFormacao> =
        presencaRepository.findByIdEvento(idEvento)

    // ------------------------------------------------------------------

    private fun buscar(id: Long): Formacao = formacaoRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("Formacao nao encontrada") }

    private fun exigirAdmin() {
        if (!escopo.ehAdmin()) {
            throw AcessoNegadoException(
                "So o coordenador paroquial gerencia formacoes de catequistas."
            )
        }
    }

    private fun aplicar(base: Formacao, form: FormacaoFormDTO): Formacao {
        val nome = form.nome?.trim().orEmpty()
        require(nome.isNotEmpty()) { "Informe o nome da formacao." }

        val nivel = form.nivel?.takeIf { it.isNotBlank() }?.let { valor ->
            runCatching { NivelEvento.valueOf(valor) }
                .getOrElse { throw IllegalArgumentException("Nivel invalido: $valor") }
        } ?: base.nivel

        if (!nivel.ehParoquialOuAcima) {
            throw IllegalArgumentException(
                "Formacao existe do nivel paroquial para cima: quem se forma e o catequista, nao a turma."
            )
        }

        val minimo = form.percentualMinimo ?: base.percentualMinimo
        if (minimo !in 1..100) {
            throw IllegalArgumentException("O percentual minimo precisa ficar entre 1 e 100.")
        }

        val situacao = form.situacao?.takeIf { it.isNotBlank() }?.let { valor ->
            runCatching { SituacaoFormacao.valueOf(valor) }
                .getOrElse { throw IllegalArgumentException("Situacao invalida: $valor") }
        } ?: base.situacao

        return base.copy(
            nome = nome,
            nivel = nivel,
            ano = form.ano ?: base.ano,
            descricao = form.descricao?.trim()?.takeIf { it.isNotEmpty() },
            percentualMinimo = minimo,
            situacao = situacao
        )
    }

    private fun resumo(formacao: Formacao): FormacaoResumoDTO {
        val encontros = eventoRepository.findByIdFormacaoOrderByDataInicioAsc(formacao.idFormacao)
        val inscritos = inscritoRepository.findByIdFormacao(formacao.idFormacao)

        val frequencias = inscritos.mapNotNull {
            frequencia.calcular(formacao.idFormacao, it.idCatequista)
        }

        return FormacaoResumoDTO(
            idFormacao = formacao.idFormacao,
            nome = formacao.nome,
            nivel = formacao.nivel,
            nivelRotulo = formacao.nivel.rotulo,
            ano = formacao.ano,
            descricao = formacao.descricao,
            percentualMinimo = formacao.percentualMinimo,
            situacao = formacao.situacao,
            totalEncontros = encontros.size,
            encontrosRealizados = encontros.count { it.situacao == SituacaoEvento.REALIZADO },
            totalInscritos = inscritos.size,
            inscritosEmDia = frequencias.count { it.atingiuMinimo },

            // Quem ainda nao tem percentual apurado nao entra em nenhum dos
            // dois lados: nao esta em dia nem abaixo, esta sem apuracao.
            inscritosAbaixo = frequencias.count { it.percentual != null && !it.atingiuMinimo },
            podeEditar = escopo.ehAdmin()
        )
    }
}
