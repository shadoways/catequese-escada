package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.CategoriaTurma
import com.catequese.catequeseapi.model.JanelaApuracao
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.SituacaoPresenca
import java.time.LocalDate

/** Como o administrador ve uma turma na tela de classificacao. */
data class TurmaAdminDTO(
    val idTurma: Long,
    val nome: String,
    val descricao: String?,
    val ano: Int?,
    val nivel: String?,
    val categoria: CategoriaTurma?,
    val janela: JanelaApuracao,
    val exigeFrequencia: Boolean,
    /** 1 = primeiro ano, 2 = segundo. */
    val etapa: Int?,
    val nomeCatequista: String?,
    val matriculadosNoAno: Int,
    /**
     * Turma sem categoria nao tem frequencia apurada. E o aviso que faz o
     * administrador entender por que a tela de frequencia esta vazia.
     */
    val pendenteDeClassificacao: Boolean
)

/** Define a regra de frequencia da turma. `categoria` nula volta a nao apurar. */
data class ClassificacaoTurmaDTO(
    val categoria: CategoriaTurma? = null,
    val etapa: Int? = null
)

data class MatriculaAdminDTO(
    val idMatricula: Long,
    val idCatequisando: Long,
    val nomeCatequisando: String,
    val idTurma: Long?,
    val nomeTurma: String?,
    val ano: Int,
    val dataMatricula: LocalDate?,
    val situacao: SituacaoMatricula,
    val observacao: String?,
    val atualizadoPor: String?
)

/**
 * Matricula avulsa. `dataMatricula` importa mais do que parece: e o marco
 * zero da frequencia, entao quem entra fora do prazo nao responde pelos
 * encontros anteriores.
 */
data class NovaMatriculaDTO(
    val idCatequisando: Long = 0,
    val idTurma: Long = 0,
    val ano: Int? = null,
    val dataMatricula: LocalDate? = null,
    val observacao: String? = null
)

data class SituacaoMatriculaDTO(
    val situacao: SituacaoMatricula = SituacaoMatricula.CURSANDO,
    val observacao: String? = null
)

/**
 * Transferencia de turma no meio do ano.
 * A matricula de origem vira TRANSFERIDO e uma nova e criada no destino, com
 * a data da mudanca -- assim cada turma cobra so o periodo em que a pessoa
 * esteve nela.
 */
data class TransferenciaDTO(
    val idTurmaDestino: Long = 0,
    val data: LocalDate? = null,
    val motivo: String? = null
)

/** Uma correcao de presenca em encontro ja encerrado. */
data class CorrecaoPresencaDTO(
    val idCatequisando: Long = 0,
    val situacao: SituacaoPresenca = SituacaoPresenca.PRESENTE,
    val justificativa: String? = null
)

/**
 * Correcao de chamada fechada. O motivo e obrigatorio: mexer em lista
 * encerrada muda a frequencia de alguem, e daqui a seis meses ninguem vai
 * lembrar por que aquele numero mudou.
 */
data class CorrecaoChamadaDTO(
    val motivo: String? = null,
    val correcoes: List<CorrecaoPresencaDTO> = emptyList()
)
