package com.catequese.catequeseapi.model

/**
 * Como esta a frequencia de alguem num periodo apurado.
 *
 * A `gravidade` existe para responder uma pergunta pratica: quando alguem tem
 * varios periodos (dois semestres, ou as etapas do catecumenato), qual deles
 * aparece na lista da turma? A resposta e sempre o pior -- se o catequisando
 * ficou abaixo do minimo no 1o semestre, a lista precisa mostrar isso mesmo
 * que ele esteja indo bem no 2o.
 */
enum class SituacaoFrequencia(val gravidade: Int) {

    /** Categoria sem controle de frequencia (pre-catequese, perseveranca, pre-catecumenato). */
    NAO_SE_APLICA(0),

    /** Ainda nao houve encontro fechado no periodo. Nao ha o que apurar. */
    SEM_APURACAO(1),

    /** Dentro do esperado. */
    REGULAR(2),

    /** Ainda acima do minimo, mas perto demais dele -- aviso preventivo. */
    EM_RISCO(3),

    /** Abaixo do minimo exigido. */
    ABAIXO_DO_MINIMO(4);

    /**
     * True quando a frequencia esta cumprida. `SEM_APURACAO` fica de fora de
     * proposito: sem encontro apurado nao da para afirmar que cumpriu.
     */
    val atingiuMinimo: Boolean
        get() = this == REGULAR || this == EM_RISCO

    companion object {
        /** A pior situacao do conjunto; e ela que vale para o catequisando. */
        fun pior(situacoes: Collection<SituacaoFrequencia>): SituacaoFrequencia =
            situacoes.maxByOrNull { it.gravidade } ?: NAO_SE_APLICA
    }
}
