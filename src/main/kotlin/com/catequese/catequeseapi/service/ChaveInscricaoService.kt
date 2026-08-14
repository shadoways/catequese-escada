package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.ChaveInscricaoDTO
import com.catequese.catequeseapi.dto.CriarChaveDTO
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.ChaveInscricao
import com.catequese.catequeseapi.repository.ChaveInscricaoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDateTime

@Service
class ChaveInscricaoService(
    private val repo: ChaveInscricaoRepository,
    @Value("\${app.url.base:http://localhost:8080}") private val urlBase: String
) {
    private val log = LoggerFactory.getLogger(ChaveInscricaoService::class.java)

    fun listar(): List<ChaveInscricaoDTO> =
        repo.findAllByOrderByCriadoEmDesc().map { ChaveInscricaoDTO.de(it, urlBase) }

    @Transactional
    fun criar(dto: CriarChaveDTO, quem: String?): ChaveInscricaoDTO {
        val dias = if (dto.validadeDias in 1..VALIDADE_MAXIMA_DIAS) dto.validadeDias else 30L
        val limite = dto.limiteUsos?.takeIf { it > 0 }

        val chave = repo.save(
            ChaveInscricao(
                codigo = gerarCodigoInedito(),
                descricao = dto.descricao?.trim()?.ifBlank { null },
                expiraEm = LocalDateTime.now().withNano(0).plusDays(dias),
                limiteUsos = limite,
                usos = 0,
                ativo = true,
                criadoPor = quem,
                criadoEm = LocalDateTime.now().withNano(0)
            )
        )

        log.info(
            "Chave de inscricao {} criada por '{}' (validade {} dias, limite {})",
            chave.codigo, quem ?: "?", dias, limite ?: "sem limite"
        )
        return ChaveInscricaoDTO.de(chave, urlBase)
    }

    @Transactional
    fun revogar(id: Long, quem: String?): ChaveInscricaoDTO {
        val chave = repo.findById(id)
            .orElseThrow { ResourceNotFoundException("Chave de inscricao nao encontrada") }

        val revogada = repo.save(
            chave.copy(ativo = false, revogadaEm = LocalDateTime.now().withNano(0))
        )
        log.info("Chave de inscricao {} revogada por '{}'", revogada.codigo, quem ?: "?")
        return ChaveInscricaoDTO.de(revogada, urlBase)
    }

    /** Consulta sem consumir. Usada pela tela publica antes de liberar o formulario. */
    fun conferir(codigo: String): ChaveInscricao? = repo.findByCodigo(codigo.trim().uppercase())

    /**
     * Valida e marca um uso. Feito dentro da transacao da inscricao: se a
     * gravacao falhar depois, o uso volta atras junto.
     *
     * Devolve a mensagem do problema, ou null se a chave foi aceita.
     */
    @Transactional
    fun validarEConsumir(codigo: String?): String? {
        if (codigo.isNullOrBlank()) {
            return "Informe a chave de inscricao para enviar o cadastro."
        }

        val chave = repo.findByCodigo(codigo.trim().uppercase())
            ?: return "Chave de inscricao nao encontrada."

        chave.motivoRecusa()?.let { return it }

        repo.save(chave.copy(usos = chave.usos + 1))
        return null
    }

    private fun gerarCodigoInedito(): String {
        repeat(10) {
            val codigo = gerarCodigo()
            if (!repo.existsByCodigo(codigo)) return codigo
        }
        // Praticamente impossivel com este alfabeto, mas nao vamos entregar
        // um codigo repetido em silencio.
        throw IllegalStateException("Nao foi possivel gerar um codigo de inscricao inedito.")
    }

    /**
     * Formato CAT-XXXX-XXXX. Sem I, O, 0 e 1, que se confundem quando alguem
     * le o codigo de um cartaz ou dita no telefone.
     */
    private fun gerarCodigo(): String {
        val bloco = { (1..4).map { ALFABETO[RANDOM.nextInt(ALFABETO.length)] }.joinToString("") }
        return "CAT-${bloco()}-${bloco()}"
    }

    private companion object {
        val RANDOM = SecureRandom()
        const val ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        const val VALIDADE_MAXIMA_DIAS = 365L
    }
}
