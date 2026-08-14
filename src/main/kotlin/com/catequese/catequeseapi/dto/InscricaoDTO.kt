package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.ChaveInscricao
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Envio completo da inscricao publica, numa unica requisicao.
 *
 * Antes o front criava catequisando, ficha e documentos em chamadas separadas e
 * tentava apagar tudo na mao quando algo falhava no meio. Agora o servidor faz
 * o conjunto dentro de uma transacao: ou grava tudo, ou nao grava nada, e nao
 * existe mais um cadastro pela metade para ninguem ir limpar depois.
 *
 * Os arquivos ja subiram antes por /api/files. Um arquivo orfao no bucket, se a
 * transacao falhar, e inofensivo perto de um cadastro incompleto no banco.
 */
data class InscricaoRequestDTO(
    val catequisando: CatequisandoInscricaoDTO = CatequisandoInscricaoDTO(),
    val ficha: FichaInscricaoDadosDTO = FichaInscricaoDadosDTO(),
    val documentos: List<DocumentoInscricaoDTO> = emptyList()
)

/**
 * Recebe apenas ids de turma e comunidade, e nao os objetos inteiros: e um
 * endpoint publico, entao ele nao deve aceitar entidades montadas por quem
 * chama.
 */
data class CatequisandoInscricaoDTO(
    val nome: String = "",
    val telefone: String? = null,
    val email: String? = null,
    val dataNascimento: LocalDate? = null,
    val nomeResponsavel: String? = null,
    val telefoneResponsavel: String? = null,
    val endereco: String? = null,
    val numeroDocumento: String? = null,
    val tipoDocumento: String? = null,
    val intoleranteGluten: Boolean = false,
    val foiBatizado: Boolean = false,
    val fezPrimeiraEucaristia: Boolean = false,
    val estadoConjugal: String? = null,
    val idTurma: Long? = null,
    val idComunidade: Long? = null
)

data class FichaInscricaoDadosDTO(
    val dataInscricao: LocalDate? = null,
    val observacoes: String? = null
)

data class DocumentoInscricaoDTO(
    val tipoDocumento: String = "",
    val caminhoArquivo: String = "",
    val dataEnvio: LocalDate? = null
)

data class InscricaoRespostaDTO(
    val idCatequisando: Long,
    val nome: String,
    val documentosGravados: Int,
    val mensagem: String
)

// ---- Chave de inscricao ----

data class CriarChaveDTO(
    val descricao: String? = null,
    /** Quantos dias a chave deve valer a partir de agora. */
    val validadeDias: Long = 30,
    /** Nulo ou zero = sem limite. */
    val limiteUsos: Int? = null
)

data class ChaveInscricaoDTO(
    val idChave: Long,
    val codigo: String,
    val descricao: String?,
    val expiraEm: LocalDateTime,
    val limiteUsos: Int?,
    val usos: Int,
    val ativo: Boolean,
    val criadoPor: String?,
    val criadoEm: LocalDateTime?,
    val revogadaEm: LocalDateTime?,
    /** Pronto para o admin so copiar e divulgar. */
    val link: String,
    /** Se esta valendo agora, considerando prazo, limite e revogacao. */
    val utilizavel: Boolean,
    val situacao: String
) {
    companion object {
        fun de(chave: ChaveInscricao, urlBase: String): ChaveInscricaoDTO {
            val recusa = chave.motivoRecusa()
            return ChaveInscricaoDTO(
                idChave = chave.idChave,
                codigo = chave.codigo,
                descricao = chave.descricao,
                expiraEm = chave.expiraEm,
                limiteUsos = chave.limiteUsos,
                usos = chave.usos,
                ativo = chave.ativo,
                criadoPor = chave.criadoPor,
                criadoEm = chave.criadoEm,
                revogadaEm = chave.revogadaEm,
                link = "${urlBase.trimEnd('/')}/index.html?chave=${chave.codigo}",
                utilizavel = recusa == null,
                situacao = recusa ?: "Valida"
            )
        }
    }
}

/** Resposta da conferencia publica do codigo, feita pela tela de cadastro. */
data class ValidacaoChaveDTO(
    val valida: Boolean,
    val descricao: String? = null,
    val motivo: String? = null
)
