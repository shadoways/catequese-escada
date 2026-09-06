package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.CurriculoCatequistaDTO
import com.catequese.catequeseapi.dto.CurriculoEncontroDTO
import com.catequese.catequeseapi.dto.CurriculoFormacaoDTO
import com.catequese.catequeseapi.dto.CurriculoHistoricoEncontroDTO
import com.catequese.catequeseapi.dto.CurriculoResumoDTO
import com.catequese.catequeseapi.dto.EstadoCurriculo
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Catequista
import com.catequese.catequeseapi.model.Comunidade
import com.catequese.catequeseapi.model.Formacao
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.repository.CatequistaRepository
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.FormacaoInscritoRepository
import com.catequese.catequeseapi.repository.FormacaoRepository
import com.catequese.catequeseapi.repository.PresencaFormacaoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * O "currículo" de formação de um catequista -- tela-catequistas.md.
 *
 * Existe separado de FrequenciaFormacaoService porque a pergunta é diferente:
 * aquele calcula uma formação de cada vez; aqui a conta é o ANO INTEIRO do
 * catequista, diocesana + regional + paroquial somadas ANTES de dividir
 * (especificação, regra 2) -- por isso soma presenças/faltas cruas em vez de
 * tirar a média dos percentuais de cada formação, o que penalizaria diferente
 * quem tem poucos encontros numa trilha e muitos noutra.
 *
 * O indicador de Formação (dentro de Indicadores) tem uma conta parecida que
 * NÃO exclui falta justificada da base -- descoberto ao especificar esta tela.
 * Não foi corrigido lá de propósito (o Gabriel decidiu: indicadores serve a
 * relatório, não precisa bater com esta tela número a número).
 */
@Service
class CurriculoCatequistaService(
    private val catequistaRepository: CatequistaRepository,
    private val comunidadeRepository: ComunidadeRepository,
    private val formacaoRepository: FormacaoRepository,
    private val formacaoInscritoRepository: FormacaoInscritoRepository,
    private val presencaRepository: PresencaFormacaoRepository,
    private val eventoRepository: EventoRepository,
    private val frequencia: FrequenciaFormacaoService,
    private val configuracao: ConfiguracaoService,
    private val escopo: EscopoAcessoService
) {

    @Transactional(readOnly = true)
    fun listar(ano: Int?): List<CurriculoResumoDTO> {
        val anoAlvo = ano ?: LocalDate.now().year
        val permitidos = escopo.catequistasPermitidos()
        val comunidades = comunidadeRepository.findAll().associateBy { it.idComunidade }

        val catequistas = (if (permitidos == null) catequistaRepository.findAll()
        else catequistaRepository.findAllById(permitidos)).filter { it.ativo }

        return catequistas
            .map { resumo(it, anoAlvo, comunidades) }
            .sortedWith(
                // Quem precisa de atenção primeiro -- é a lista que o
                // coordenador abre para saber quem chamar para a próxima formação.
                compareBy<CurriculoResumoDTO> { ordemDoEstado(it.estado) }.thenBy { it.nome.lowercase() }
            )
    }

    @Transactional(readOnly = true)
    fun detalhe(idCatequista: Long, ano: Int?): CurriculoCatequistaDTO {
        if (!escopo.podeVerCatequista(idCatequista)) {
            throw AcessoNegadoException("Você só pode consultar o próprio currículo.")
        }
        val catequista = catequistaRepository.findById(idCatequista)
            .orElseThrow { ResourceNotFoundException("Catequista não encontrado") }
        val anoAlvo = ano ?: LocalDate.now().year

        val formacoesDoAno = formacoesInscritasNoAno(idCatequista, anoAlvo)
        val porNivel = formacoesDoAno.groupBy { it.nivel }
        val linhasPorFormacao = formacoesDoAno.associate { it.idFormacao to montarFormacao(it, idCatequista) }

        val minimo = configuracao.minimoAgregadoFormacao()
        val percentual = agregarAno(formacoesDoAno, idCatequista)
        val estado = estadoDe(percentual, minimo, anoAlvo)

        fun linhasDoNivel(nivel: NivelEvento) =
            (porNivel[nivel] ?: emptyList()).mapNotNull { linhasPorFormacao[it.idFormacao] }
                // Mais recente primeiro -- é o que responde "e ele, está em dia AGORA?".
                .sortedByDescending { f -> f.encontros.lastOrNull()?.data ?: LocalDate.MIN }

        return CurriculoCatequistaDTO(
            idCatequista = catequista.idCatequista,
            nome = catequista.nome,
            comunidade = nomeComunidade(escopo.comunidadeDoCatequista(idCatequista)),
            ano = anoAlvo,
            percentualAgregado = percentual,
            minimoAgregado = minimo,
            estado = estado,
            estadoRotulo = estado.rotulo,
            diocesana = linhasDoNivel(NivelEvento.DIOCESANO),
            regional = linhasDoNivel(NivelEvento.REGIONAL),
            paroquial = linhasDoNivel(NivelEvento.PAROQUIAL)
        )
    }

    /**
     * O histórico completo (TODOS os anos) de encontros de formação de um
     * catequista, uma linha por encontro `REALIZADO` -- para a aba
     * "Formações" e seus filtros de situação/ano/mês. Diferente de
     * `formacoesInscritasNoAno`: aqui não se recorta por ano nenhum, porque o
     * filtro por ano É a tela, não uma pergunta já respondida antes de
     * chegar nela (ao contrário do resumo, regra 2, que é sempre do ano
     * corrente).
     */
    @Transactional(readOnly = true)
    fun historico(idCatequista: Long): List<CurriculoHistoricoEncontroDTO> {
        if (!escopo.podeVerCatequista(idCatequista)) {
            throw AcessoNegadoException("Você só pode consultar o próprio histórico de formação.")
        }
        val formacoes = formacaoInscritoRepository.findByIdCatequista(idCatequista)
            .mapNotNull { formacaoRepository.findById(it.idFormacao).orElse(null) }
            // Mesma regra 9 do curriculo: nunca duplicar a mesma formacao na lista.
            .distinctBy { it.idFormacao }

        return formacoes.flatMap { formacao ->
            val realizados = eventoRepository.findByIdFormacaoOrderByDataInicioAsc(formacao.idFormacao)
                .filter { it.situacao == SituacaoEvento.REALIZADO }
            if (realizados.isEmpty()) return@flatMap emptyList()

            val marcacoes = presencaRepository.findByIdEventoIn(realizados.map { it.idEvento })
                .filter { it.idCatequista == idCatequista }
                .associateBy { it.idEvento }

            realizados.map { evento ->
                val marcacao = marcacoes[evento.idEvento]
                CurriculoHistoricoEncontroDTO(
                    idFormacao = formacao.idFormacao,
                    formacaoNome = formacao.nome,
                    nivel = formacao.nivel,
                    nivelRotulo = formacao.nivel.rotulo,
                    ano = formacao.ano,
                    data = evento.dataInicio,
                    // Encontro realizado sem marcacao e falta -- mesma regra de sempre.
                    situacao = (marcacao?.situacao ?: SituacaoPresenca.FALTA).name,
                    justificativa = marcacao?.justificativa
                )
            }
        }.sortedByDescending { it.data ?: LocalDate.MIN }
    }

    // ------------------------------------------------------------------

    private fun formacoesInscritasNoAno(idCatequista: Long, ano: Int): List<Formacao> =
        formacaoInscritoRepository.findByIdCatequista(idCatequista)
            .mapNotNull { formacaoRepository.findById(it.idFormacao).orElse(null) }
            .filter { it.ano == ano }
            // Duas inscricoes na mesma formacao nao deveriam existir (o banco
            // ja tem uk_formacao_catequista), mas o agrupamento por id evita
            // fileira duplicada na tela se algum dia acontecer.
            .distinctBy { it.idFormacao }

    private fun resumo(catequista: Catequista, ano: Int, comunidades: Map<Long, Comunidade>): CurriculoResumoDTO {
        val formacoesDoAno = formacoesInscritasNoAno(catequista.idCatequista, ano)
        val minimo = configuracao.minimoAgregadoFormacao()
        val percentual = agregarAno(formacoesDoAno, catequista.idCatequista)
        val estado = estadoDe(percentual, minimo, ano)
        val idComunidade = escopo.comunidadeDoCatequista(catequista.idCatequista)

        return CurriculoResumoDTO(
            idCatequista = catequista.idCatequista,
            nome = catequista.nome,
            comunidade = idComunidade?.let { comunidades[it]?.nome },
            ano = ano,
            percentual = percentual,
            minimoAgregado = minimo,
            estado = estado,
            estadoRotulo = estado.rotulo
        )
    }

    /**
     * Soma presença e encontro possível de TODAS as formações da lista, e só
     * então divide -- é o que faz a conta ser "o ano inteiro", não a média de
     * várias formações pequenas. Mesmas três regras de
     * `FrequenciaFormacaoService`: só `REALIZADO` conta, `JUSTIFICADA` sai da
     * base, encontro sem marcação é falta -- para as duas contas nunca
     * discordarem sobre o que é presença.
     */
    private fun agregarAno(formacoes: List<Formacao>, idCatequista: Long): Int? {
        var presencas = 0
        var faltas = 0

        formacoes.forEach { f ->
            val realizados = eventoRepository.findByIdFormacaoOrderByDataInicioAsc(f.idFormacao)
                .filter { it.situacao == SituacaoEvento.REALIZADO }
            if (realizados.isEmpty()) return@forEach

            val marcacoes = presencaRepository.findByIdEventoIn(realizados.map { it.idEvento })
                .filter { it.idCatequista == idCatequista }
                .associateBy { it.idEvento }

            realizados.forEach { evento ->
                when (marcacoes[evento.idEvento]?.situacao) {
                    SituacaoPresenca.PRESENTE -> presencas++
                    SituacaoPresenca.JUSTIFICADA -> Unit // fora da base -- nem a favor, nem contra
                    SituacaoPresenca.FALTA, null -> faltas++
                }
            }
        }

        val base = presencas + faltas
        // Null, e nao 0: ninguem teve chance de participar ainda neste ano,
        // mostrar 0% assustaria sem motivo (mesmo raciocinio de FrequenciaFormacaoService).
        return if (base == 0) null else (presencas * 100) / base
    }

    private fun estadoDe(percentual: Int?, minimo: Int, ano: Int): EstadoCurriculo {
        if (percentual == null) return EstadoCurriculo.NEUTRO
        if (percentual >= minimo) return EstadoCurriculo.VERDE

        val hoje = LocalDate.now()
        val fechamento = LocalDate.of(ano, configuracao.fechamentoMesFormacao(), 1)
        val alerta = fechamento.minusMonths(configuracao.alertaMesesAntesFormacao().toLong())

        return when {
            !hoje.isBefore(fechamento) -> EstadoCurriculo.VERMELHO
            !hoje.isBefore(alerta) -> EstadoCurriculo.AMARELO
            // Abaixo do minimo, mas o ano nem chegou na janela de alerta --
            // reprovar cedo demais e o mesmo erro que EncerramentoAnoService
            // ja evita para catequisando.
            else -> EstadoCurriculo.NEUTRO
        }
    }

    private fun ordemDoEstado(estado: EstadoCurriculo): Int = when (estado) {
        EstadoCurriculo.VERMELHO -> 0
        EstadoCurriculo.AMARELO -> 1
        EstadoCurriculo.NEUTRO -> 2
        EstadoCurriculo.VERDE -> 3
    }

    private fun montarFormacao(formacao: Formacao, idCatequista: Long): CurriculoFormacaoDTO {
        val realizados = eventoRepository.findByIdFormacaoOrderByDataInicioAsc(formacao.idFormacao)
            .filter { it.situacao == SituacaoEvento.REALIZADO }
        val marcacoes = if (realizados.isEmpty()) emptyMap()
        else presencaRepository.findByIdEventoIn(realizados.map { it.idEvento })
            .filter { it.idCatequista == idCatequista }
            .associateBy { it.idEvento }

        val encontros = realizados.map { evento ->
            val marcacao = marcacoes[evento.idEvento]
            CurriculoEncontroDTO(
                data = evento.dataInicio,
                // Encontro realizado sem marcacao e falta -- mesma regra de
                // FrequenciaFormacaoService, para as duas contas nunca discordarem.
                situacao = (marcacao?.situacao ?: SituacaoPresenca.FALTA).name,
                justificativa = marcacao?.justificativa
            )
        }

        val freq = frequencia.calcular(formacao.idFormacao, idCatequista)

        return CurriculoFormacaoDTO(
            idFormacao = formacao.idFormacao,
            nome = formacao.nome,
            nivel = formacao.nivel,
            nivelRotulo = formacao.nivel.rotulo,
            ano = formacao.ano,
            percentualMinimo = formacao.percentualMinimo,
            percentual = freq?.percentual,
            atingiuMinimo = freq?.atingiuMinimo == true,
            encontros = encontros
        )
    }

    private fun nomeComunidade(idComunidade: Long?): String? =
        idComunidade?.let { comunidadeRepository.findById(it).map { c -> c.nome }.orElse(null) }
}
