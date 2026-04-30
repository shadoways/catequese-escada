package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.auth.LoginRequestDTO
import com.catequese.catequeseapi.dto.auth.LoginResponseDTO
import com.catequese.catequeseapi.dto.auth.PasswordResetConfirmDTO
import com.catequese.catequeseapi.dto.auth.PasswordResetRequestDTO
import com.catequese.catequeseapi.dto.auth.RefreshTokenRequestDTO
import com.catequese.catequeseapi.service.AuthService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    companion object {
        private val logger = LoggerFactory.getLogger(AuthController::class.java)
    }

    /**
     * POST /api/auth/login
     * Autentica usuário e retorna JWT token
     */
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequestDTO): ResponseEntity<LoginResponseDTO> {
        logger.info("📥 POST /api/auth/login")
        val response = authService.login(request)
        logger.info("✅ Login bem-sucedido")
        return ResponseEntity.ok(response)
    }

    /**
     * POST /api/auth/refresh
     * Renova access token via refresh token com rotação.
     */
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequestDTO): ResponseEntity<LoginResponseDTO> {
        logger.info("📥 POST /api/auth/refresh")
        return ResponseEntity.ok(authService.refresh(request))
    }

    /**
     * POST /api/auth/logout
     * Revoga refresh token da sessão atual.
     */
    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: RefreshTokenRequestDTO): ResponseEntity<Map<String, String>> {
        logger.info("📥 POST /api/auth/logout")
        authService.logout(request)
        return ResponseEntity.ok(mapOf("message" to "Logout realizado com sucesso"))
    }

    /**
     * POST /api/auth/password-reset/request
     * Solicita reset de senha (envia email com token)
     */
    @PostMapping("/password-reset/request")
    fun requestPasswordReset(@Valid @RequestBody request: PasswordResetRequestDTO): ResponseEntity<Map<String, String>> {
        logger.info("📥 POST /api/auth/password-reset/request")

        authService.requestPasswordReset(request)

        logger.info("✅ Email de reset enviado (se usuário existe)")

        return ResponseEntity.ok(
            mapOf("message" to "Se o email estiver cadastrado, você receberá instruções para redefinir sua senha.")
        )
    }

    /**
     * POST /api/auth/password-reset/confirm
     * Confirma reset de senha com token
     */
    @PostMapping("/password-reset/confirm")
    fun confirmPasswordReset(@Valid @RequestBody request: PasswordResetConfirmDTO): ResponseEntity<Map<String, String>> {
        logger.info("📥 POST /api/auth/password-reset/confirm")
        authService.resetPassword(request)
        logger.info("✅ Senha resetada com sucesso")
        return ResponseEntity.ok(mapOf("message" to "Senha alterada com sucesso"))
    }

    /**
     * GET /api/auth/validate
     * Valida se token JWT é válido
     */
    @GetMapping("/validate")
    fun validateToken(@RequestHeader("Authorization") authHeader: String): ResponseEntity<Map<String, Boolean>> {
        logger.info("📥 GET /api/auth/validate")

        if (!authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(mapOf("valid" to false))
        }

        val token = authHeader.replace("Bearer ", "")
        val isValid = authService.validateToken(token)

        return ResponseEntity.ok(mapOf("valid" to isValid))
    }

    /**
     * GET /api/auth/health
     * Health check do módulo de autenticação
     */
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(
            mapOf(
                "status" to "UP",
                "module" to "authentication"
            )
        )
    }
}

