package com.catequese.catequeseapi.model

/**
 * Tipos de usuario do sistema, em ordem crescente de acesso.
 *
 * - CATEQUISTA: apenas visualiza (consultas, fichas, impressao).
 * - COORDENADOR: visualiza e altera os dados da catequese.
 * - COORDENADOR_PAROQUIAL: e o administrador. Faz tudo o que o coordenador faz,
 *   mais a gestao de usuarios e as configuracoes do sistema.
 */
enum class TipoUsuario {
    CATEQUISTA,
    COORDENADOR,
    COORDENADOR_PAROQUIAL;

    /** Somente leitura para catequista. */
    val podeEditar: Boolean
        get() = this != CATEQUISTA

    /** Gestao de usuarios e configuracoes. */
    val isAdmin: Boolean
        get() = this == COORDENADOR_PAROQUIAL

    /** Nome da role usada pelo Spring Security (ROLE_ + nome). */
    val role: String
        get() = "ROLE_$name"
}
