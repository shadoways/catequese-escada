package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.RefreshToken
import com.catequese.catequeseapi.model.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.Optional

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>
    fun findByUsuarioAndRevogadoFalse(usuario: Usuario): List<RefreshToken>
    fun deleteByDataExpiracaoBefore(data: LocalDateTime)
}

