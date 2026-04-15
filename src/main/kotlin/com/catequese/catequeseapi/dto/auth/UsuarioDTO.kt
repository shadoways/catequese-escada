package com.catequese.catequeseapi.dto.auth

import com.catequese.catequeseapi.enums.RoleType
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class UsuarioDTO(
    val idUsuario: Long? = null,

    @field:NotBlank(message = "Nome é obrigatório")
    val nome: String,

    @field:NotBlank(message = "Email é obrigatório")
    @field:Email(message = "Email inválido")
    val email: String,
    val ativo: Boolean = true,
    val roles: List<RoleType> = emptyList(),
    val idComunidade: Long? = null, // Para coordenadores de comunidade
    val idCatequista: Long? = null  // Para catequistas
)

