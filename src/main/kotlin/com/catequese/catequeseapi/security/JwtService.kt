package com.catequese.catequeseapi.security

import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date

/**
 * Geracao e leitura do token JWT.
 *
 * O segredo e o tempo de expiracao vem de jwt.secret / jwt.expirationMs, que ja
 * existiam no application.properties.
 */
@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expirationMs}") private val expirationMs: Long
) {
    private val log = LoggerFactory.getLogger(JwtService::class.java)

    private val key by lazy { Keys.hmacShaKeyFor(secret.toByteArray(Charsets.UTF_8)) }

    fun gerarToken(usuario: Usuario): String {
        val agora = Date()
        return Jwts.builder()
            .setSubject(usuario.username)
            .claim("idUsuario", usuario.idUsuario)
            .claim("nome", usuario.nome)
            .claim("tipo", usuario.tipo.name)
            .setIssuedAt(agora)
            .setExpiration(Date(agora.time + expirationMs))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }

    /** Devolve os claims do token, ou null se estiver invalido/expirado. */
    fun lerToken(token: String): Claims? = try {
        Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    } catch (ex: Exception) {
        // Token invalido nao e erro de servidor: so nao autentica.
        log.debug("Token JWT rejeitado: {}", ex.message)
        null
    }

    fun tipoDoToken(claims: Claims): TipoUsuario? =
        runCatching { TipoUsuario.valueOf(claims["tipo"] as String) }.getOrNull()
}
