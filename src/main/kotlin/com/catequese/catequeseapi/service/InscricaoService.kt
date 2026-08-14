package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.InscricaoRequestDTO
import com.catequese.catequeseapi.dto.InscricaoRespostaDTO
import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.Documento
import com.catequese.catequeseapi.model.FichaInscricao
import com.catequese.catequeseapi.repository.CatequisandoRepository
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.DocumentoRepository
import com.catequese.catequeseapi.repository.FichaInscricaoRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Grava a inscricao inteira -- catequisando, ficha e documentos -- numa unica
 * transacao.
 *
 * Existe para acabar com o cadastro pela metade: antes o navegador criava cada
 * parte numa chamada e, quando algo falhava no meio, tentava apagar o que ja
 * tinha criado. Esse conserto pelo cliente falha justamente nos casos em que
 * mais importa (a rede caiu, a aba fechou) e ainda exigia dar permissao de
 * apagar para a tela publica. Aqui, se qualquer passo falhar, o banco desfaz
 * tudo sozinho e ninguem precisa de permissao para apagar nada.
 */
@Service
class InscricaoService(
    private val catequisandoRepository: CatequisandoRepository,
    private val fichaRepository: FichaInscricaoRepository,
    private val documentoRepository: DocumentoRepository,
    private val turmaRepository: TurmaRepository,
    private val comunidadeRepository: ComunidadeRepository,
    private val chaveInscricaoService: ChaveInscricaoService
) {
    private val log = LoggerFactory.getLogger(InscricaoService::class.java)

    /** Erro de regra da inscricao, traduzido para 400 pelo RestExceptionHandler. */
    class InscricaoInvalidaException(mensagem: String) : IllegalArgumentException(mensagem)

    /**
     * @param codigoChave chave de inscricao; ignorada quando quem envia ja tem
     *        permissao de cadastrar pelo sistema.
     */
    @Transactional
    fun registrar(
        dto: InscricaoRequestDTO,
        codigoChave: String?,
        exigirChave: Boolean
    ): InscricaoRespostaDTO {
        val dados = dto.catequisando

        if (dados.nome.isBlank()) {
            throw InscricaoInvalidaException("Informe o nome do catequisando.")
        }

        // A chave e consumida dentro desta transacao: se a gravacao falhar
        // adiante, o uso volta atras junto e a vaga nao e desperdicada.
        if (exigirChave) {
            chaveInscricaoService.validarEConsumir(codigoChave)?.let {
                throw InscricaoInvalidaException(it)
            }
        }

        val turma = dados.idTurma?.let {
            turmaRepository.findById(it).orElseThrow {
                InscricaoInvalidaException("Turma nao encontrada.")
            }
        }
        val comunidade = dados.idComunidade?.let {
            comunidadeRepository.findById(it).orElseThrow {
                InscricaoInvalidaException("Comunidade nao encontrada.")
            }
        }

        val catequisando = catequisandoRepository.save(
            Catequisando(
                nome = dados.nome.trim(),
                telefone = dados.telefone?.trim()?.ifBlank { null },
                email = dados.email?.trim()?.ifBlank { null },
                dataNascimento = dados.dataNascimento,
                nomeResponsavel = dados.nomeResponsavel?.trim()?.ifBlank { null },
                telefoneResponsavel = dados.telefoneResponsavel?.trim()?.ifBlank { null },
                endereco = dados.endereco?.trim()?.ifBlank { null },
                numeroDocumento = dados.numeroDocumento?.trim()?.ifBlank { null },
                tipoDocumento = dados.tipoDocumento?.trim()?.ifBlank { null },
                intoleranteGluten = dados.intoleranteGluten,
                foiBatizado = dados.foiBatizado,
                fezPrimeiraEucaristia = dados.fezPrimeiraEucaristia,
                estadoConjugal = dados.estadoConjugal?.trim()?.ifBlank { null },
                ativo = true,
                turma = turma,
                comunidade = comunidade
            )
        )

        fichaRepository.save(
            FichaInscricao(
                dataInscricao = dto.ficha.dataInscricao ?: LocalDate.now(),
                observacoes = dto.ficha.observacoes?.trim()?.ifBlank { null },
                catequisando = catequisando
            )
        )

        val hoje = LocalDate.now()
        val documentos = dto.documentos
            .filter { it.tipoDocumento.isNotBlank() && it.caminhoArquivo.isNotBlank() }
            .map {
                Documento(
                    tipoDocumento = it.tipoDocumento.trim(),
                    caminhoArquivo = it.caminhoArquivo.trim(),
                    dataEnvio = it.dataEnvio ?: hoje,
                    catequisando = catequisando,
                    tipoStatus = "ENVIADO"
                )
            }
        if (documentos.isNotEmpty()) documentoRepository.saveAll(documentos)

        log.info(
            "Inscricao gravada: catequisando {} ({}) com {} documento(s)",
            catequisando.idCatequisando, catequisando.nome, documentos.size
        )

        return InscricaoRespostaDTO(
            idCatequisando = catequisando.idCatequisando,
            nome = catequisando.nome,
            documentosGravados = documentos.size,
            mensagem = "Cadastro realizado com sucesso."
        )
    }
}
