package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.auth.*
import com.catequese.catequeseapi.exception.UnauthorizedException
import com.catequese.catequeseapi.model.PasswordResetToken
import com.catequese.catequeseapi.model.RefreshToken
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.PasswordResetTokenRepository
import com.catequese.catequeseapi.repository.RefreshTokenRepository
import com.catequese.catequeseapi.repository.UsuarioRepository
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*
import javax.crypto.SecretKey

@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val emailService: EmailService,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${jwt.secret}") private val jwtSecret: String,
    @Value("\${jwt.expirationMs}") private val jwtExpirationMs: Long,
    @Value("\${jwt.refreshExpirationMs:604800000}") private val refreshExpirationMs: Long
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AuthService::class.java)
    }

    private val signingKey: SecretKey by lazy {
        val bytes = jwtSecret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 64) {
            "JWT secret inválido: use pelo menos 64 caracteres para HS512"
        }
        Keys.hmacShaKeyFor(bytes)
    }

    /**
     * Autentica usuário e retorna JWT token
     */
    fun login(request: LoginRequestDTO): LoginResponseDTO {
        val normalizedEmail = request.email.trim().lowercase()
        logger.info("🔐 Tentativa de login")

        val usuario = usuarioRepository.findByEmail(normalizedEmail)
            .orElseThrow {
                logger.warn("❌ Falha de login")
                throw UnauthorizedException("Credenciais inválidas")
            }

        if (!usuario.ativo) {
            logger.warn("❌ Falha de login")
            throw UnauthorizedException("Credenciais inválidas")
        }

        val storedHash = usuario.passwordHash.trim()
        if (!passwordEncoder.matches(request.password, storedHash)) {
            logger.warn("❌ Falha de login")
            throw UnauthorizedException("Credenciais inválidas")
        }

        // Gerar access token e refresh token persistido
        val token = generateToken(usuario)
        val refreshToken = generateRefreshTokenRaw()
        saveRefreshToken(usuario, refreshToken)
        val roles = usuario.roles.map { it.role }

        // Atualizar último login
        val usuarioAtualizado = usuario.copy(ultimoLogin = LocalDateTime.now())
        usuarioRepository.save(usuarioAtualizado)

        logger.info("✅ Login bem-sucedido")

        return LoginResponseDTO(
            token = token,
            email = usuario.email,
            nome = usuario.nome,
            roles = roles,
            expiresIn = jwtExpirationMs,
            refreshToken = refreshToken,
            refreshExpiresIn = refreshExpirationMs
        )
    }

    /**
     * Renova sessão com rotação de refresh token (token antigo é revogado).
     */
    fun refresh(request: RefreshTokenRequestDTO): LoginResponseDTO {
        val now = LocalDateTime.now()
        val tokenHash = hashToken(request.refreshToken)
        val stored = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow {
                logger.warn("❌ Refresh token inválido")
                throw UnauthorizedException("Refresh token inválido ou expirado")
            }

        if (stored.revogado || stored.dataExpiracao.isBefore(now)) {
            logger.warn("❌ Refresh token expirado/revogado")
            if (!stored.revogado) {
                refreshTokenRepository.save(stored.copy(revogado = true, dataRevogacao = now))
            }
            throw UnauthorizedException("Refresh token inválido ou expirado")
        }

        val usuario = stored.usuario
        if (!usuario.ativo) {
            refreshTokenRepository.save(stored.copy(revogado = true, dataRevogacao = now))
            throw UnauthorizedException("Credenciais inválidas")
        }

        // Rotaciona refresh token para bloquear reutilização.
        refreshTokenRepository.save(stored.copy(revogado = true, dataRevogacao = now))

        val newAccessToken = generateToken(usuario)
        val newRefreshToken = generateRefreshTokenRaw()
        saveRefreshToken(usuario, newRefreshToken)

        return LoginResponseDTO(
            token = newAccessToken,
            email = usuario.email,
            nome = usuario.nome,
            roles = usuario.roles.map { it.role },
            expiresIn = jwtExpirationMs,
            refreshToken = newRefreshToken,
            refreshExpiresIn = refreshExpirationMs
        )
    }

    /**
     * Revoga refresh token da sessão atual (logout da sessão).
     */
    fun logout(request: RefreshTokenRequestDTO) {
        val now = LocalDateTime.now()
        val tokenHash = hashToken(request.refreshToken)
        val existing = refreshTokenRepository.findByTokenHash(tokenHash).orElse(null)

        if (existing != null && !existing.revogado) {
            refreshTokenRepository.save(existing.copy(revogado = true, dataRevogacao = now))
        }
    }

    /**
     * Solicita reset de senha (envia email com token)
     */
    fun requestPasswordReset(request: PasswordResetRequestDTO) {
        val normalizedEmail = request.email.trim().lowercase()
        logger.info("🔑 Solicitação de reset de senha")

        val usuario = usuarioRepository.findByEmail(normalizedEmail).orElse(null)

        if (usuario == null) {
            logger.warn("❌ Solicitação de reset ignorada")
            // Por segurança, não informar que o email não existe
            return
        }

        if (!usuario.ativo) {
            logger.warn("❌ Solicitação de reset ignorada")
            return
        }

        passwordResetTokenRepository.findByUsuario(usuario)
            .filter { !it.usado }
            .forEach { antigo -> passwordResetTokenRepository.save(antigo.copy(usado = true)) }

        // Gerar token único
        val token = UUID.randomUUID().toString()
        val expiracao = LocalDateTime.now().plusHours(24) // Token válido por 24h

        val resetToken = PasswordResetToken(
            token = token,
            usuario = usuario,
            dataExpiracao = expiracao
        )

        passwordResetTokenRepository.save(resetToken)

        // Enviar email com token
        emailService.sendPasswordResetEmail(usuario.email, usuario.nome, token)

        logger.info("✅ Token de reset gerado")
    }

    /**
     * Confirma reset de senha com token
     */
    fun resetPassword(request: PasswordResetConfirmDTO) {
        logger.info("🔑 Confirmação de reset de senha")

        val resetToken = passwordResetTokenRepository.findByToken(request.token)
            .orElseThrow {
                logger.warn("❌ Token de reset inválido")
                throw IllegalArgumentException("Token inválido ou expirado")
            }

        if (resetToken.usado) {
            logger.warn("❌ Token já foi usado")
            throw IllegalArgumentException("Token já foi utilizado")
        }

        if (resetToken.dataExpiracao.isBefore(LocalDateTime.now())) {
            logger.warn("❌ Token expirado")
            throw IllegalArgumentException("Token expirado")
        }

        // Validar senha
        if (request.newPassword.length < 6) {
            throw IllegalArgumentException("Senha deve ter no mínimo 6 caracteres")
        }

        // Atualizar senha
        val usuario = resetToken.usuario
        val usuarioAtualizado = usuario.copy(
            passwordHash = passwordEncoder.encode(request.newPassword)
        )
        usuarioRepository.save(usuarioAtualizado)

        // Marcar token como usado
        val tokenUsado = resetToken.copy(usado = true)
        passwordResetTokenRepository.save(tokenUsado)

        passwordResetTokenRepository.findByUsuario(usuario)
            .filter { !it.usado && it.idToken != tokenUsado.idToken }
            .forEach { antigo -> passwordResetTokenRepository.save(antigo.copy(usado = true)) }

        logger.info("✅ Senha resetada com sucesso")
    }

    /**
     * Gera JWT token para usuário
     */
    private fun generateToken(usuario: Usuario): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        val roles = usuario.roles.map { it.role.name }

        return Jwts.builder()
            .setSubject(usuario.email)
            .claim("email", usuario.email)
            .claim("nome", usuario.nome)
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(signingKey, SignatureAlgorithm.HS512)
            .compact()
    }

    /**
     * Valida JWT token
     */
    fun validateToken(token: String): Boolean {
        return try {
            parseClaims(token)
            true
        } catch (e: Exception) {
            logger.warn("❌ Token inválido")
            false
        }
    }

    /**
     * Extrai ID do usuário do token
     */
    fun getEmailFromToken(token: String): String {
        return parseClaims(token).subject
    }

    private fun parseClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(signingKey)
            .build()
            .parseClaimsJws(token)
            .body
    }

    private fun saveRefreshToken(usuario: Usuario, refreshTokenRaw: String) {
        val now = LocalDateTime.now()
        val refreshToken = RefreshToken(
            usuario = usuario,
            tokenHash = hashToken(refreshTokenRaw),
            dataExpiracao = now.plusSeconds(refreshExpirationMs / 1000)
        )
        refreshTokenRepository.save(refreshToken)
    }

    private fun generateRefreshTokenRaw(): String {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString()
    }

    private fun hashToken(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

