package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.FrequenciaFormacaoDTO
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.model.SituacaoPresenca
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.FormacaoRepository
import com.catequese.catequeseapi.repository.PresencaFormacaoRepository
import org.springframework.stereotype.Service

/**
 * Apura os 80% de uma formacao para um catequista.
 *
 * Duas decisoes que valem estar escritas, porque sao as mesmas ja tomadas na
 * frequencia da turma e precisam continuar batendo:
 *
 * 1. So encontro REALIZADO entra na conta. Encontro previsto do resto do ano
 *    nao pode contar como falta -- senao todo mundo comeca o ano reprovado.
 * 2. Falta JUSTIFICADA sai da conta em vez de contar contra, igual a
 *    SituacaoPresenca ja define para o catequisando.
 *
 * Quando nao ha nenhum encontro realizado o percentual e `null`, e nao 0: sao
 * coisas diferentes, e mostrar 0% para uma formacao que ainda nao comecou
 * assustaria sem motivo.
 */
@Service
class FrequenciaFormacaoService(
    private val eventoRepository: EventoRepository,
    private val formacaoRepository: FormacaoRepository,
    private val presencaRepository: PresencaFormacaoRepository,
    private val escopo: EscopoAcessoService
) {

    /** Frequencia do catequista logado. Null se quem pediu nao e catequista. */
    fun minhaFrequencia(idFormacao: Long): FrequenciaFormacaoDTO? {
        val idCatequista = escopo.usuarioLogado()?.idCatequista ?: return null
        return calcular(idFormacao, idCatequista)
    }

    fun calcular(idFormacao: Long, idCatequista: Long): FrequenciaFormacaoDTO? {
        val formacao = formacaoRepository.findById(idFormacao).orElse(null) ?: return null

        val realizados = eventoRepository
            .findByIdFormacaoOrderByDataInicioAsc(idFormacao)
            .filter { it.situacao == SituacaoEvento.REALIZADO }

        if (realizados.isEmpty()) {
            return FrequenciaFormacaoDTO(
                idFormacao = idFormacao,
                formacaoNome = formacao.nome,
                percentualMinimo = formacao.percentualMinimo,
                encontrosRealizados = 0,
                presencas = 0,
                faltas = 0,
                justificadas = 0,
                percentual = null,
                atingiuMinimo = false
            )
        }

        val marcacoes = presencaRepository
            .findByIdEventoIn(realizados.map { it.idEvento })
            .filter { it.idCatequista == idCatequista }
            .associateBy { it.idEvento }

        var presencas = 0
        var justificadas = 0
        var faltas = 0

        realizados.forEach { encontro ->
            when (marcacoes[encontro.idEvento]?.situacao) {
                SituacaoPresenca.PRESENTE -> presencas++
                SituacaoPresenca.JUSTIFICADA -> justificadas++
                SituacaoPresenca.FALTA -> faltas++

                // Encontro realizado sem marcacao conta como falta: se contasse
                // como "nao apurado", bastaria nao fazer a chamada para todo
                // mundo ficar com 100%.
                null -> faltas++
            }
        }

        val base = presencas + faltas
        val percentual = if (base == 0) null else (presencas * 100) / base

        return FrequenciaFormacaoDTO(
            idFormacao = idFormacao,
            formacaoNome = formacao.nome,
            percentualMinimo = formacao.percentualMinimo,
            encontrosRealizados = realizados.size,
            presencas = presencas,
            faltas = faltas,
            justificadas = justificadas,
            percentual = percentual,
            atingiuMinimo = percentual != null && percentual >= formacao.percentualMinimo
        )
    }
}
