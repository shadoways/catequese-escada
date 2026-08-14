package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.EtapaCatecumenato
import com.catequese.catequeseapi.model.SituacaoMatricula
import java.time.LocalDate

/**
 * Um documento na ficha do catequisando.
 *
 * NAO tem caminho de arquivo, e isso e proposital: o pedido era que o
 * catequista visse QUAIS documentos foram entregues, e nao os documentos.
 * Certidao de nascimento e RG de menor de idade nao precisam circular para
 * alguem conferir uma pendencia.
 *
 * Quem precisa abrir o arquivo continua usando a ficha completa, que e
 * restrita a quem pode editar cadastro.
 */
data class DocumentoStatusDTO(
    val tipo: String,
    val entregue: Boolean,
    val status: String,
    val dataEnvio: LocalDate?
)

/** Uma passagem pelo catecumenato. A etapa em aberto tem `fim` nulo. */
data class EtapaHistoricoDTO(
    val etapa: EtapaCatecumenato,
    val rotulo: String,
    val inicio: LocalDate?,
    val fim: LocalDate?,
    val emAndamento: Boolean,
    val exigeFrequencia: Boolean,
    val observacao: String?,
    val registradoPor: String?
)

/** Uma linha do percurso: em que turma esteve, em que ano, e como terminou. */
data class MatriculaHistoricoDTO(
    val idMatricula: Long,
    val ano: Int,
    val idTurma: Long?,
    val nomeTurma: String?,
    val categoria: CategoriaTurma?,
    val etapaTurma: Int?,
    val dataMatricula: LocalDate?,
    val situacao: SituacaoMatricula,
    val observacao: String?
)

/**
 * A ficha que o catequista ve.
 *
 * A frequencia NAO vem aqui: ela tem endpoint proprio
 * (/api/frequencia/catequisando/{id}/historico), que ja resolve as regras de
 * cada categoria. Duplicar a apuracao em dois lugares seria pedir para os
 * dois discordarem um dia.
 */
data class FichaCatequisandoDTO(
    val idCatequisando: Long,
    val nome: String,
    val dataNascimento: LocalDate?,
    /** Calculada no servidor: a tela nao precisa saber fazer conta de data. */
    val idade: Int?,
    val telefone: String?,
    val email: String?,
    val endereco: String?,
    /** Pedido explicito: o catequista precisa conseguir falar com a familia. */
    val nomeResponsavel: String?,
    val telefoneResponsavel: String?,
    val nomeComunidade: String?,
    val idTurmaAtual: Long?,
    val nomeTurmaAtual: String?,
    val foiBatizado: Boolean,
    val fezPrimeiraEucaristia: Boolean,
    /** Importa em retiro e confraternizacao, quando ha comida envolvida. */
    val intoleranteGluten: Boolean,
    val ativo: Boolean,
    val dataInscricao: LocalDate?,
    val documentos: List<DocumentoStatusDTO> = emptyList(),
    val etapaAtual: EtapaCatecumenato? = null,
    val historicoEtapas: List<EtapaHistoricoDTO> = emptyList(),
    val historicoMatriculas: List<MatriculaHistoricoDTO> = emptyList(),
    /**
     * Se quem esta olhando pode alterar os dados cadastrais. A tela usa para
     * decidir o que mostrar; quem barra de verdade continua sendo o backend.
     */
    val podeEditar: Boolean = false
)
