package com.catequese.catequeseapi.model

/**
 * De quem e o evento.
 *
 * Este enum responde "quem pode mexer", e nao "que assunto e". Um evento de
 * formacao pode ser DIOCESANO ou PAROQUIAL; um rito do RICA pode ser
 * PAROQUIAL ou de COMUNIDADE. Por isso nivel e tipo sao campos separados --
 * misturar os dois num campo so foi o que deixou `tb_evento.nivel` como texto
 * livre sem nenhum vinculo real.
 *
 * A ordem importa: vai do mais distante da pessoa (diocese) ao mais proximo
 * (a turma dela). `alcance` e usado para ordenar e para decidir a cor na tela.
 */
enum class NivelEvento(val rotulo: String, val alcance: Int) {

    /** Vem da diocese. Todos veem; so o coordenador paroquial cadastra. */
    DIOCESANO("Diocesano", 1),

    /** Vem do Regional da CNBB. Todos veem; so o coordenador paroquial cadastra. */
    REGIONAL("Regional", 2),

    /** Da paroquia inteira. So o coordenador paroquial cadastra. */
    PAROQUIAL("Paroquial", 3),

    /** De uma comunidade. O coordenador dela cadastra; nas outras, so ve. */
    COMUNIDADE("Comunidade", 4),

    /** De uma turma. O catequista cadastra nas turmas em que atua. */
    TURMA("Turma", 5);

    /** Niveis acima da paroquia: nenhum deles se prende a comunidade ou turma. */
    val ehParoquialOuAcima: Boolean
        get() = alcance <= PAROQUIAL.alcance

    /** Exige `idComunidade` preenchido. */
    val exigeComunidade: Boolean
        get() = this == COMUNIDADE

    /** Exige `idTurma` preenchido. */
    val exigeTurma: Boolean
        get() = this == TURMA
}

/**
 * O que o evento e.
 *
 * Separado do nivel de proposito (ver NivelEvento). O tipo decide como o
 * evento aparece na agenda e se ele tem chamada -- e de quem.
 */
enum class TipoEvento(val rotulo: String) {

    /**
     * Encontro de uma trilha de formacao. Quem tem presenca aqui e o
     * CATEQUISTA, nao o catequisando -- e a unica presenca do sistema que
     * conta percentual fora da frequencia da turma.
     */
    FORMACAO("Formação"),

    /** Batismo, crisma, eucaristia, Vigilia Pascal. Sem apuracao de frequencia. */
    SACRAMENTO("Sacramento"),

    /**
     * Rito do itinerario catecumenal: eleicao, escrutinios, entregas, Efata.
     * As datas sao moveis (dependem da Pascoa) e ficam a cargo da paroquia --
     * o sistema nao as calcula, so as guarda.
     */
    RITO_RICA("Rito do RICA"),

    /** Retiro, missa, festa, visita, reuniao. O caso geral. */
    ENCONTRO("Encontro");

    /** So evento de formacao tem chamada de catequista. */
    val temPresencaDeCatequista: Boolean
        get() = this == FORMACAO
}

/**
 * Em que pe esta o evento.
 *
 * CANCELADO nao some da agenda: some do calculo. Quem olha o mes precisa
 * enxergar que aquilo estava marcado e nao aconteceu -- mesma decisao ja
 * tomada em SituacaoEncontro.
 */
enum class SituacaoEvento(val rotulo: String) {
    PREVISTO("Previsto"),
    REALIZADO("Realizado"),
    CANCELADO("Cancelado")
}

/** Se a trilha de formacao ainda aceita encontro e inscricao. */
enum class SituacaoFormacao(val rotulo: String) {
    ABERTA("Aberta"),
    ENCERRADA("Encerrada")
}
