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
    /** Fase: 1 = primeira, 2 = segunda. Nulo fora de Eucaristia e Crisma. */
    val etapa: Int?,
    val nomeCatequista: String?,
    val matriculadosNoAno: Int,

    /**
     * Comunidade dona da turma. Decide qual coordenador pode mexer nos eventos
     * dela na agenda -- antes deste campo a comunidade era deduzida dos
     * catequisandos matriculados, e turma vazia nao pertencia a ninguem.
     */
    val idComunidade: Long?,
    val nomeComunidade: String?,
    /**
     * Turma sem categoria nao tem frequencia apurada. E o aviso que faz o
     * administrador entender por que a tela de frequencia esta vazia.
     */
    val pendenteDeClassificacao: Boolean
)

/** Define a regra de frequencia da turma. `categoria` nula volta a nao apurar. */
data class ClassificacaoTurmaDTO(
    val categoria: CategoriaTurma? = null,
    val etapa: Int? = null,
    val idComunidade: Long? = null
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
    val atualizadoPor: String?,
    /**
     * Para onde a pessoa foi quando saiu da paroquia. Nulo em todo o resto.
     *
     * Sem este campo a tela mostra "Transferido" e para ali -- e a pergunta que
     * sempre vem depois e "para onde?". Era ela que fazia a secretaria manter
     * um caderno paralelo.
     */
    val paroquiaDestino: String? = null
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
/**
 * Uma movimentacao do catequisando.
 *
 * Dois destinos possiveis, e so um deles tem turma:
 *   - outra turma da paroquia -> `idTurmaDestino`
 *   - outra PAROQUIA          -> `paroquiaDestino` com o nome
 *
 * Sao mutuamente exclusivos: quem sai da paroquia nao ganha inscricao nova
 * aqui, porque a inscricao dele passa a ser de outro lugar. Guardar o nome e o
 * que permite responder "para onde ele foi?" um ano depois -- sem isso, sai
 * como transferido e ninguem sabe para onde.
 */
data class TransferenciaDTO(
    val idTurmaDestino: Long? = null,
    val paroquiaDestino: String? = null,
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
