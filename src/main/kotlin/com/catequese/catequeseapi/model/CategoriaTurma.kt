package com.catequese.catequeseapi.model

/**
 * Em que recorte de tempo a frequencia daquela categoria e apurada.
 */
enum class JanelaApuracao {
    /** Categoria sem controle de frequencia. */
    NENHUMA,

    /** Um unico fechamento por ano civil. */
    ANO,

    /** Dois fechamentos por ano: janeiro a junho e julho a dezembro. */
    SEMESTRE,

    /** Cada etapa do catecumenato tem a propria apuracao, com duracao variavel. */
    ETAPA_CATECUMENATO
}

/**
 * Categoria da turma. E ela que decide a regra de frequencia.
 *
 * Campo proprio, e nao deduzido do nome da turma: se alguem renomeasse
 * "Crisma I" para "Crisma 1", a regra passaria a errar em silencio -- o pior
 * tipo de falha num controle de frequencia.
 *
 * Os rotulos para a tela ficam no frontend; aqui so mora a regra.
 */
enum class CategoriaTurma(
    val janela: JanelaApuracao,
    /** Quantos anos a categoria dura. Depois disso, o catequisando concluiu. */
    val anosPrevistos: Int?
) {
    PRE_CATEQUESE(JanelaApuracao.NENHUMA, null),
    EUCARISTIA(JanelaApuracao.ANO, 2),
    CRISMA(JanelaApuracao.ANO, 2),
    ADULTOS(JanelaApuracao.SEMESTRE, 2),
    CATECUMENATO(JanelaApuracao.ETAPA_CATECUMENATO, null),
    PERSEVERANCA(JanelaApuracao.NENHUMA, null);

    val exigeFrequencia: Boolean
        get() = janela != JanelaApuracao.NENHUMA
}

/**
 * Etapas do catecumenato, na ordem do caminho.
 *
 * O pre-catecumenato e periodo de descoberta: a pessoa ainda esta decidindo se
 * quer seguir, e pode desistir. Por isso nao tem exigencia de frequencia.
 */
enum class EtapaCatecumenato(val exigeFrequencia: Boolean) {
    PRE_CATECUMENATO(false),
    CATECUMENATO(true),
    PURIFICACAO_ILUMINACAO(true),
    MISTAGOGIA(true)
}

/** Situacao do catequisando naquele ano/turma. */
enum class SituacaoMatricula {
    CURSANDO,
    CONCLUIDO,
    NAO_CONCLUIDO,
    TRANSFERIDO,
    DESISTENTE
}

enum class SituacaoEncontro {
    /** A lista esta aberta e aceita marcacao. */
    ABERTO,

    /** Chamada enviada. So o administrador reabre. */
    FECHADO,

    /** Nao houve encontro. Nao entra em conta nenhuma, e exige motivo. */
    CANCELADO
}

enum class SituacaoPresenca {
    PRESENTE,
    FALTA,

    /** Falta com motivo aceito: sai da conta em vez de contar contra. */
    JUSTIFICADA
}
