package com.catequese.catequeseapi.dto.auth

data class PasswordResetConfirmDTO(
    val token: String,
    val newPassword: String
)

