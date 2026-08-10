package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.TipoUsuario

/** Corpo do POST /api/auth/login. */
data class LoginRequestDTO(
    val username: String = "",
    val password: String = ""
)

/** Corpo do POST /api/auth/trocar-senha. */
data class TrocarSenhaDTO(
    val senhaAtual: String = "",
    val novaSenha: String = ""
)

/** Corpo do POST /api/auth/esqueci-senha. */
data class EsqueciSenhaDTO(
    val email: String = ""
)

/** Corpo do POST /api/auth/redefinir-senha (token vem do link do e-mail). */
data class RedefinirSenhaDTO(
    val token: String = "",
    val novaSenha: String = ""
)

/**
 * Resposta do login, do /api/auth/me e da troca de senha.
 *
 * O front usa `tipo`, `podeEditar`, `admin` e `senhaProvisoria` para decidir o
 * que mostrar -- mas quem realmente bloqueia a acao e o backend, nao a tela.
 */
data class UsuarioLogadoDTO(
    val idUsuario: Long,
    val nome: String,
    val username: String,
    val email: String?,
    val tipo: TipoUsuario,
    val podeEditar: Boolean,
    val admin: Boolean,
    val senhaProvisoria: Boolean,
    val token: String? = null
)
