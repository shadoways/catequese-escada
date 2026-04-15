package com.catequese.catequeseapi.dto.auth

data class CreateUsuarioDTO(
    val nome: String,
    val email: String,
    val password: String,
    val roles: List<String>, // ["COORDENADOR_PAROQUIAL", "CATEQUISTA"]
    val idComunidade: Long? = null,
    val idCatequista: Long? = null
)

