package com.catequese.catequeseapi.dto.auth

import com.catequese.catequeseapi.enums.RoleType

data class UsuarioDTO(
    val idUsuario: Long? = null,
    val nome: String,
    val email: String,
    val ativo: Boolean = true,
    val roles: List<RoleType> = emptyList(),
    val idComunidade: Long? = null, // Para coordenadores de comunidade
    val idCatequista: Long? = null  // Para catequistas
)

