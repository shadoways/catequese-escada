package com.catequese.catequeseapi.dto.auth

import com.catequese.catequeseapi.enums.RoleType

data class LoginResponseDTO(
    val token: String,
    val email: String,
    val nome: String,
    val roles: List<RoleType>,
    val expiresIn: Long // milliseconds
)

