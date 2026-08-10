package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.TokenRecuperacao
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TokenRecuperacaoRepository : JpaRepository<TokenRecuperacao, Long> {

    fun findByTokenHash(tokenHash: String): TokenRecuperacao?

    /** Tokens ainda abertos de um usuario -- usados para invalidar os anteriores. */
    fun findAllByIdUsuarioAndUsadoEmIsNull(idUsuario: Long): List<TokenRecuperacao>

    /** Ultimo pedido feito, para limitar a frequencia de envio de e-mail. */
    fun findFirstByIdUsuarioOrderByCriadoEmDesc(idUsuario: Long): TokenRecuperacao?
}
