package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.auth.*
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.PasswordResetToken
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.PasswordResetTokenRepository
import com.catequese.catequeseapi.repository.UsuarioRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*

@Service
class AuthService(
    private val usuarioRepository: UsuarioRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailService: EmailService,
    @Value("\${jwt.secret}") private val jwtSecret: String,
    @Value("\${jwt.expirationMs}") private val jwtExpirationMs: Long
) {
    companion object {
        private val logger = LoggerFactory.getLogger(AuthService::class.java)
        private val passwordEncoder = BCryptPasswordEncoder()
    }

    /**
     * Autentica usuário e retorna JWT token
     */
    fun login(request: LoginRequestDTO): LoginResponseDTO {
        logger.info("🔐 Tentativa de login: ${request.email}")

        val usuario = usuarioRepository.findByEmail(request.email)
            .orElseThrow {
                logger.warn("❌ Usuário não encontrado: ${request.email}")
                throw ResourceNotFoundException("Credenciais inválidas")
            }

        if (!usuario.ativo) {
            logger.warn("❌ Usuário inativo: ${request.email}")
            throw IllegalStateException("Usuário inativo")
        }

        if (!passwordEncoder.matches(request.password, usuario.passwordHash)) {
            logger.warn("❌ Senha inválida: ${request.email}")
            throw IllegalArgumentException("Credenciais inválidas")
        }

        // Gerar JWT token
        val token = generateToken(usuario)
        val roles = usuario.roles.map { it.role }

        // Atualizar último login
        val usuarioAtualizado = usuario.copy(ultimoLogin = LocalDateTime.now())
        usuarioRepository.save(usuarioAtualizado)

        logger.info("✅ Login bem-sucedido: ${request.email}")

        return LoginResponseDTO(
            token = token,
            email = usuario.email,
            nome = usuario.nome,
            roles = roles,
            expiresIn = jwtExpirationMs
        )
    }

    /**
     * Solicita reset de senha (envia email com token)
     */
    fun requestPasswordReset(request: PasswordResetRequestDTO) {
        logger.info("🔑 Solicitação de reset de senha: ${request.email}")

        val usuario = usuarioRepository.findByEmail(request.email).orElse(null)

        if (usuario == null) {
            logger.warn("❌ Usuário não encontrado para reset: ${request.email}")
            // Por segurança, não informar que o email não existe
            return
        }

        if (!usuario.ativo) {
            logger.warn("❌ Tentativa de reset para usuário inativo: ${request.email}")
            return
        }

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

        logger.info("✅ Token de reset enviado para: ${request.email}")
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

        logger.info("✅ Senha resetada com sucesso para: ${usuario.email}")
    }

    /**
     * Gera JWT token para usuário
     */
    private fun generateToken(usuario: Usuario): String {
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        val roles = usuario.roles.map { it.role.name }

        return Jwts.builder()
            .setSubject(usuario.idUsuario.toString())
            .claim("email", usuario.email)
            .claim("nome", usuario.nome)
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(SignatureAlgorithm.HS512, jwtSecret)
            .compact()
    }

    /**
     * Valida JWT token
     */
    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(token)
            true
        } catch (e: Exception) {
            logger.warn("❌ Token inválido: ${e.message}")
            false
        }
    }

    /**
     * Extrai ID do usuário do token
     */
    fun getUserIdFromToken(token: String): Long {
        val claims = Jwts.parser()
            .setSigningKey(jwtSecret)
            .parseClaimsJws(token)
            .body

        return claims.subject.toLong()
    }
}

