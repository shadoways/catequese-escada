package com.catequese.catequeseapi.dto.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class CreateUsuarioDTO(
    @field:NotBlank(message = "Nome é obrigatório")
    val nome: String,

    @field:NotBlank(message = "Email é obrigatório")
    @field:Email(message = "Email inválido")
    val email: String,

    @field:NotBlank(message = "Senha é obrigatória")
    @field:Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    val password: String,

    @field:NotEmpty(message = "Pelo menos uma role deve ser informada")
    val roles: List<String>, // ["COORDENADOR_PAROQUIAL", "CATEQUISTA"]
    val idComunidade: Long? = null,
    val idCatequista: Long? = null
)

