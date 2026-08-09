package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.TipoUsuario

/** Corpo do POST /api/auth/login. */
data class LoginRequestDTO(
    val username: String = "",
    val password: String = ""
)

/**
 * Resposta do login e do /api/auth/me.
 * O front usa `tipo`, `podeEditar` e `admin` para decidir o que mostrar --
 * mas quem realmente bloqueia a acao e o backend, nao a tela.
 */
data class UsuarioLogadoDTO(
    val idUsuario: Long,
    val nome: String,
    val username: String,
    val tipo: TipoUsuario,
    val podeEditar: Boolean,
    val admin: Boolean,
    val token: String? = null
)
