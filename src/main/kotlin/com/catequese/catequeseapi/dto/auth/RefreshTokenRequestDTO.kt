package com.catequese.catequeseapi.dto.auth

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequestDTO(
    @field:NotBlank(message = "Refresh token é obrigatório")
    val refreshToken: String
)

