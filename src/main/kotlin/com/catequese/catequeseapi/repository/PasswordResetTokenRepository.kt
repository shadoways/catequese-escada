package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.PasswordResetToken
import com.catequese.catequeseapi.model.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, Long> {
    fun findByToken(token: String): Optional<PasswordResetToken>
    fun findByUsuario(usuario: Usuario): List<PasswordResetToken>
    fun deleteByDataExpiracaoBefore(date: LocalDateTime)
}

