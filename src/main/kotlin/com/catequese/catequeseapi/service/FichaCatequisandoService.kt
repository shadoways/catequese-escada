package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.DocumentoStatusDTO
import com.catequese.catequeseapi.dto.EtapaHistoricoDTO
import com.catequese.catequeseapi.dto.FichaCatequisandoDTO
import com.catequese.catequeseapi.dto.MatriculaHistoricoDTO
import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.EtapaCatecumenato
import com.catequese.catequeseapi.repository.CatequisandoRepository
import com.catequese.catequeseapi.repository.DocumentoRepository
import com.catequese.catequeseapi.repository.EtapaCatecumenoRepository
import com.catequese.catequeseapi.repository.FichaInscricaoRepository
import com.catequese.catequeseapi.repository.MatriculaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period

/**
 * A ficha do catequisando como o catequista precisa ver.
 *
 * Existe separada da ficha completa (FichaInscricaoController) por um motivo
 * de privacidade, nao de organizacao: aqui o documento aparece como STATUS --
 * entregue ou pendente -- e nunca como arquivo. Certidao de nascimento e
 * documento de menor de idade nao precisam circular para alguem conferir uma
 * pendencia de entrega.
 *
 * O recorte de quem ve quem e o mesmo do resto do modulo: catequista alcanca
 * quem esta matriculado nas turmas dele, coordenador alcanca a propria
 * comunidade, administrador alcanca todos.
 */
@Service
class FichaCatequisandoService(
    private val catequisandoRepository: CatequisandoRepository,
    private val documentoRepository: DocumentoRepository,
    private val matriculaRepository: MatriculaRepository,
    private val etapaCatecumenoRepository: EtapaCatecumenoRepository,
    private val fichaInscricaoRepository: FichaInscricaoRepository,
    private val escopo: EscopoAcessoService
) {

    @Transactional(readOnly = true)
    fun ficha(idCatequisando: Long): FichaCatequisandoDTO {
        val catequisando = catequisandoRepository.findById(idCatequisando)
            .orElseThrow { ResourceNotFoundException("Catequisando nao encontrado") }

        val matriculas = matriculaRepository.findByCatequisandoOrderByAnoDesc(catequisando)
        exigirAcesso(catequisando, matriculas.mapNotNull { it.turma?.idTurma })

        val etapas = etapaCatecumenoRepository
            .findByCatequisandoOrderByDataInicioAsc(catequisando)
        val etapaAberta = etapas.firstOrNull { it.emAndamento() }

        // A ficha de inscricao mais recente e a que vale para "quando entrou".
        val dataInscricao = fichaInscricaoRepository.findByCatequisando(catequisando)
            .mapNotNull { it.dataInscricao }
            .maxOrNull()

        val turmaAtual = matriculas.firstOrNull()?.turma ?: catequisando.turma

        return FichaCatequisandoDTO(
            idCatequisando = catequisando.idCatequisando,
            nome = catequisando.nome,
            dataNascimento = catequisando.dataNascimento,
            idade = idadeDe(catequisando.dataNascimento),
            telefone = catequisando.telefone,
            email = catequisando.email,
            endereco = catequisando.endereco,
            nomeResponsavel = catequisando.nomeResponsavel,
            telefoneResponsavel = catequisando.telefoneResponsavel,
            nomeComunidade = catequisando.comunidade?.nome,
            idTurmaAtual = turmaAtual?.idTurma,
            nomeTurmaAtual = turmaAtual?.nome,
            foiBatizado = catequisando.foiBatizado,
            fezPrimeiraEucaristia = catequisando.fezPrimeiraEucaristia,
            intoleranteGluten = catequisando.intoleranteGluten,
            ativo = catequisando.ativo,
            dataInscricao = dataInscricao,
            documentos = documentosDe(catequisando),
            etapaAtual = etapaAberta?.etapa,
            historicoEtapas = etapas.map { registro ->
                val etapa = registro.etapa
                EtapaHistoricoDTO(
                    etapa = etapa,
                    rotulo = rotuloEtapa(etapa),
                    inicio = registro.dataInicio,
                    fim = registro.dataFim,
                    emAndamento = registro.emAndamento(),
                    exigeFrequencia = etapa.exigeFrequencia,
                    observacao = registro.observacao,
                    registradoPor = registro.registradoPor
                )
            },
            historicoMatriculas = matriculas.map { matricula ->
                val turma = matricula.turma
                MatriculaHistoricoDTO(
                    idMatricula = matricula.idMatricula,
                    ano = matricula.ano,
                    idTurma = turma?.idTurma,
                    nomeTurma = turma?.nome,
                    categoria = turma?.categoria,
                    etapaTurma = turma?.etapa,
                    dataMatricula = matricula.dataMatricula,
                    situacao = matricula.situacao,
                    observacao = matricula.observacao
                )
            },
            podeEditar = escopo.podeEditarCadastro()
        )
    }

    // ---- Apoio ---------------------------------------------------------------

    /**
     * Converte os documentos em status de entrega, DESCARTANDO o caminho do
     * arquivo. O descarte acontece aqui, no servidor: mandar o caminho e
     * esconder na tela nao esconderia nada de quem abrisse a resposta da API.
     */
    private fun documentosDe(catequisando: Catequisando): List<DocumentoStatusDTO> =
        documentoRepository.findByCatequisando(catequisando)
            .map { documento ->
                val status = documento.tipoStatus
                DocumentoStatusDTO(
                    tipo = documento.tipoDocumento?.trim()?.ifBlank { null } ?: "Documento",
                    entregue = status.equals("ENTREGUE", ignoreCase = true) ||
                        documento.caminhoArquivo?.isNotBlank() == true,
                    status = status,
                    dataEnvio = documento.dataEnvio
                )
            }
            .sortedBy { it.tipo.lowercase() }

    private fun idadeDe(nascimento: LocalDate?): Int? {
        if (nascimento == null) return null
        val hoje = LocalDate.now()
        if (nascimento.isAfter(hoje)) return null
        return Period.between(nascimento, hoje).years
    }

    private fun rotuloEtapa(etapa: EtapaCatecumenato): String = when (etapa) {
        EtapaCatecumenato.PRE_CATECUMENATO -> "Pre-catecumenato"
        EtapaCatecumenato.CATECUMENATO -> "Catecumenato"
        EtapaCatecumenato.PURIFICACAO_ILUMINACAO -> "Purificacao e iluminacao"
        EtapaCatecumenato.MISTAGOGIA -> "Mistagogia"
    }

    /**
     * Catequista alcanca quem passou por alguma turma dele -- inclusive em
     * anos anteriores, senao o historico ficaria inacessivel justamente para
     * quem acompanha a pessoa. Coordenador alcanca a propria comunidade.
     */
    private fun exigirAcesso(catequisando: Catequisando, turmasDoCatequisando: List<Long>) {
        val turmasDoCatequista = escopo.turmasDoCatequista()

        if (turmasDoCatequista != null) {
            val turmaAtual = catequisando.turma?.idTurma
            val alcanca = turmasDoCatequisando.any { it in turmasDoCatequista } ||
                (turmaAtual != null && turmaAtual in turmasDoCatequista)
            if (!alcanca) {
                throw AcessoNegadoException("Este catequisando nao esta em nenhuma turma sua.")
            }
            return
        }

        if (!escopo.podeVerComunidade(catequisando.comunidade?.idComunidade)) {
            throw AcessoNegadoException("Este catequisando nao e da sua comunidade.")
        }
    }
}
