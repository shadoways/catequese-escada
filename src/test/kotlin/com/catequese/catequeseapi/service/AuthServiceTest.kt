package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.auth.LoginRequestDTO
import com.catequese.catequeseapi.dto.auth.PasswordResetConfirmDTO
import com.catequese.catequeseapi.dto.auth.PasswordResetRequestDTO
import com.catequese.catequeseapi.enums.RoleType
import com.catequese.catequeseapi.exception.UnauthorizedException
import com.catequese.catequeseapi.model.PasswordResetToken
import com.catequese.catequeseapi.model.RefreshToken
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.model.UsuarioRole
import com.catequese.catequeseapi.repository.PasswordResetTokenRepository
import com.catequese.catequeseapi.repository.RefreshTokenRepository
import com.catequese.catequeseapi.repository.UsuarioRepository
import io.mockk.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.LocalDateTime
import java.util.Optional

class AuthServiceTest {

    private lateinit var usuarioRepository: UsuarioRepository
    private lateinit var passwordResetTokenRepository: PasswordResetTokenRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var emailService: EmailService
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var authService: AuthService

    private val jwtSecret = "dev-test-secret-key-with-at-least-sixty-four-characters-1234567890"

    @BeforeEach
    fun setUp() {
        usuarioRepository = mockk()
        passwordResetTokenRepository = mockk()
        refreshTokenRepository = mockk()
        emailService = mockk(relaxed = true)
        passwordEncoder = mockk()

        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        authService = AuthService(
            usuarioRepository,
            passwordResetTokenRepository,
            refreshTokenRepository,
            emailService,
            passwordEncoder,
            jwtSecret,
            120_000L,
            600_000L
        )
    }

    @Test
    fun `login deve retornar token quando credenciais sao validas`() {
        val usuario = usuarioComRole()
        every { usuarioRepository.findByEmail("admin@catequese.com") } returns Optional.of(usuario)
        every { passwordEncoder.matches("admin123", any()) } returns true
        every { usuarioRepository.save(any()) } answers { firstArg() }

        val response = authService.login(LoginRequestDTO("admin@catequese.com", "admin123"))

        assertTrue(response.token.isNotBlank())
        assertEquals("admin@catequese.com", response.email)
        assertEquals("Administrador", response.nome)
        assertEquals(listOf(RoleType.COORDENADOR_PAROQUIAL), response.roles)
        assertNotNull(response.refreshToken)
        assertEquals(600_000L, response.refreshExpiresIn)
        verify(exactly = 1) {
            usuarioRepository.save(withArg {
                assertNotNull(it.ultimoLogin)
            })
        }
    }

    @Test
    fun `login deve normalizar email para lowercase e trim`() {
        val usuario = usuarioComRole()
        every { usuarioRepository.findByEmail("admin@catequese.com") } returns Optional.of(usuario)
        every { passwordEncoder.matches("admin123", any()) } returns true
        every { usuarioRepository.save(any()) } answers { firstArg() }

        authService.login(LoginRequestDTO("  ADMIN@CATEQUESE.COM  ", "admin123"))

        verify(exactly = 1) { usuarioRepository.findByEmail("admin@catequese.com") }
    }

    @Test
    fun `login deve falhar quando usuario nao existe`() {
        every { usuarioRepository.findByEmail(any()) } returns Optional.empty()

        assertThrows<UnauthorizedException> {
            authService.login(LoginRequestDTO("inexistente@catequese.com", "123456"))
        }

        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
    }

    @Test
    fun `login deve falhar quando usuario esta inativo`() {
        every { usuarioRepository.findByEmail(any()) } returns Optional.of(usuarioComRole(ativo = false))

        assertThrows<UnauthorizedException> {
            authService.login(LoginRequestDTO("admin@catequese.com", "admin123"))
        }

        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
    }

    @Test
    fun `login deve falhar quando senha nao confere`() {
        every { usuarioRepository.findByEmail(any()) } returns Optional.of(usuarioComRole())
        every { passwordEncoder.matches(any(), any()) } returns false

        assertThrows<UnauthorizedException> {
            authService.login(LoginRequestDTO("admin@catequese.com", "senha-errada"))
        }
    }

    @Test
    fun `requestPasswordReset nao deve vazar existencia de email`() {
        every { usuarioRepository.findByEmail(any()) } returns Optional.empty()

        authService.requestPasswordReset(PasswordResetRequestDTO("naoexiste@catequese.com"))

        verify(exactly = 0) { passwordResetTokenRepository.save(any()) }
        verify(exactly = 0) { emailService.sendPasswordResetEmail(any(), any(), any()) }
    }

    @Test
    fun `requestPasswordReset deve invalidar tokens antigos e enviar email`() {
        val usuario = usuarioComRole()
        val antigo = PasswordResetToken(
            idToken = 10,
            token = "old-token",
            usuario = usuario,
            dataExpiracao = LocalDateTime.now().plusHours(1),
            usado = false
        )
        every { usuarioRepository.findByEmail("admin@catequese.com") } returns Optional.of(usuario)
        every { passwordResetTokenRepository.findByUsuario(usuario) } returns listOf(antigo)
        every { passwordResetTokenRepository.save(any()) } answers { firstArg() }

        authService.requestPasswordReset(PasswordResetRequestDTO("admin@catequese.com"))

        verify(atLeast = 2) { passwordResetTokenRepository.save(any()) }
        verify(exactly = 1) { emailService.sendPasswordResetEmail("admin@catequese.com", "Administrador", any()) }
    }

    @Test
    fun `resetPassword deve falhar com token expirado`() {
        val usuario = usuarioComRole()
        val tokenExpirado = PasswordResetToken(
            idToken = 1,
            token = "token-expirado",
            usuario = usuario,
            dataExpiracao = LocalDateTime.now().minusMinutes(1),
            usado = false
        )
        every { passwordResetTokenRepository.findByToken("token-expirado") } returns Optional.of(tokenExpirado)

        val ex = assertThrows<IllegalArgumentException> {
            authService.resetPassword(PasswordResetConfirmDTO("token-expirado", "novaSenha123"))
        }

        assertTrue(ex.message!!.contains("expirado"))
    }

    @Test
    fun `resetPassword deve atualizar hash e invalidar token`() {
        val usuario = usuarioComRole(passwordHash = "hash-antigo")
        val token = PasswordResetToken(
            idToken = 1,
            token = "token-valido",
            usuario = usuario,
            dataExpiracao = LocalDateTime.now().plusHours(2),
            usado = false
        )
        every { passwordResetTokenRepository.findByToken("token-valido") } returns Optional.of(token)
        every { passwordEncoder.encode("novaSenha123") } returns "novo-hash"
        every { usuarioRepository.save(any()) } answers { firstArg() }
        every { passwordResetTokenRepository.save(any()) } answers { firstArg() }
        every { passwordResetTokenRepository.findByUsuario(usuario) } returns listOf(token)

        authService.resetPassword(PasswordResetConfirmDTO("token-valido", "novaSenha123"))

        verify(exactly = 1) {
            usuarioRepository.save(withArg {
                assertEquals("novo-hash", it.passwordHash)
            })
        }
        verify(atLeast = 1) {
            passwordResetTokenRepository.save(withArg {
                assertTrue(it.usado)
            })
        }
    }

    @Test
    fun `validateToken deve retornar true para token gerado`() {
        val usuario = usuarioComRole()
        every { usuarioRepository.findByEmail("admin@catequese.com") } returns Optional.of(usuario)
        every { passwordEncoder.matches("admin123", any()) } returns true
        every { usuarioRepository.save(any()) } answers { firstArg() }

        val token = authService.login(LoginRequestDTO("admin@catequese.com", "admin123")).token

        assertTrue(authService.validateToken(token))
        assertEquals("admin@catequese.com", authService.getEmailFromToken(token))
    }

    @Test
    fun `refresh deve rotacionar refresh token quando valido`() {
        val usuario = usuarioComRole()
        every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(
            RefreshToken(
                idRefreshToken = 1,
                usuario = usuario,
                tokenHash = "hash",
                dataExpiracao = LocalDateTime.now().plusMinutes(10),
                revogado = false
            )
        )

        val response = authService.refresh(com.catequese.catequeseapi.dto.auth.RefreshTokenRequestDTO("refresh-token-valido"))

        assertTrue(response.token.isNotBlank())
        assertNotNull(response.refreshToken)
        verify(atLeast = 2) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `logout deve revogar refresh token quando encontrado`() {
        val usuario = usuarioComRole()
        every { refreshTokenRepository.findByTokenHash(any()) } returns Optional.of(
            RefreshToken(
                idRefreshToken = 2,
                usuario = usuario,
                tokenHash = "hash",
                dataExpiracao = LocalDateTime.now().plusMinutes(10),
                revogado = false
            )
        )

        authService.logout(com.catequese.catequeseapi.dto.auth.RefreshTokenRequestDTO("refresh-token-valido"))

        verify(atLeast = 1) { refreshTokenRepository.save(withArg { assertTrue(it.revogado) }) }
    }

    private fun usuarioComRole(
        ativo: Boolean = true,
        passwordHash: String = "hash-bcrypt"
    ): Usuario {
        val base = Usuario(
            idUsuario = 1,
            nome = "Administrador",
            email = "admin@catequese.com",
            passwordHash = passwordHash,
            ativo = ativo,
            roles = emptyList()
        )
        val role = UsuarioRole(
            idUsuarioRole = 1,
            role = RoleType.COORDENADOR_PAROQUIAL,
            usuario = base
        )
        return base.copy(roles = listOf(role))
    }
}

