package com.catequese.catequeseapi.dto.auth

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class PasswordResetConfirmDTO(
    @field:NotBlank(message = "Token é obrigatório")
    val token: String,

    @field:NotBlank(message = "Nova senha é obrigatória")
    @field:Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    val newPassword: String
)

