package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.advice.RestExceptionHandler
import com.catequese.catequeseapi.config.JwtAuthenticationFilter
import com.catequese.catequeseapi.dto.auth.LoginResponseDTO
import com.catequese.catequeseapi.enums.RoleType
import com.catequese.catequeseapi.exception.UnauthorizedException
import com.catequese.catequeseapi.service.AuthService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.mockito.Mockito.`when`

@WebMvcTest(controllers = [AuthController::class])
@AutoConfigureMockMvc(addFilters = false)
@Import(RestExceptionHandler::class)
class AuthControllerContractTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockitoBean
    lateinit var authService: AuthService

    @MockitoBean
    lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @Test
    fun `POST login deve manter contrato de sucesso`() {
        val response = LoginResponseDTO(
            token = "jwt.token.value",
            email = "admin@catequese.com",
            nome = "Administrador",
            roles = listOf(RoleType.COORDENADOR_PAROQUIAL),
            expiresIn = 172800000,
            refreshToken = "refresh.token.value",
            refreshExpiresIn = 604800000
        )
        `when`(authService.login(com.catequese.catequeseapi.dto.auth.LoginRequestDTO("admin@catequese.com", "admin123")))
            .thenReturn(response)

        val payload = mapOf("email" to "admin@catequese.com", "password" to "admin123")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt.token.value"))
            .andExpect(jsonPath("$.email").value("admin@catequese.com"))
            .andExpect(jsonPath("$.nome").value("Administrador"))
            .andExpect(jsonPath("$.roles[0]").value("COORDENADOR_PAROQUIAL"))
            .andExpect(jsonPath("$.expiresIn").value(172800000))
            .andExpect(jsonPath("$.refreshToken").value("refresh.token.value"))
            .andExpect(jsonPath("$.refreshExpiresIn").value(604800000))
    }

    @Test
    fun `POST refresh deve manter contrato de sucesso`() {
        val response = LoginResponseDTO(
            token = "novo.access.token",
            email = "admin@catequese.com",
            nome = "Administrador",
            roles = listOf(RoleType.COORDENADOR_PAROQUIAL),
            expiresIn = 172800000,
            refreshToken = "novo.refresh.token",
            refreshExpiresIn = 604800000
        )
        `when`(authService.refresh(com.catequese.catequeseapi.dto.auth.RefreshTokenRequestDTO("refresh-token")))
            .thenReturn(response)

        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("refreshToken" to "refresh-token")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("novo.access.token"))
            .andExpect(jsonPath("$.refreshToken").value("novo.refresh.token"))
    }

    @Test
    fun `POST logout deve manter contrato de sucesso`() {
        mockMvc.perform(
            post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("refreshToken" to "refresh-token")))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.message").value("Logout realizado com sucesso"))
    }

    @Test
    fun `POST login deve retornar 400 quando payload invalido`() {
        val payload = mapOf("email" to "invalido", "password" to "123")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.erro").value("Validação falhou"))
            .andExpect(jsonPath("$.detalhes.email").exists())
            .andExpect(jsonPath("$.detalhes.password").exists())
    }

    @Test
    fun `POST login deve retornar 401 com mensagem padrao de credenciais`() {
        `when`(authService.login(com.catequese.catequeseapi.dto.auth.LoginRequestDTO("admin@catequese.com", "senhaErrada")))
            .thenThrow(UnauthorizedException("Credenciais inválidas"))

        val payload = mapOf("email" to "admin@catequese.com", "password" to "senhaErrada")

        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.erro").value("Credenciais inválidas"))
    }

    @Test
    fun `GET validate deve retornar 400 quando header nao esta no formato Bearer`() {
        mockMvc.perform(
            get("/api/auth/validate")
                .header("Authorization", "token-sem-bearer")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.valid").value(false))
    }

    @Test
    fun `GET validate deve manter contrato quando token valido`() {
        `when`(authService.validateToken("token.valido")).thenReturn(true)

        mockMvc.perform(
            get("/api/auth/validate")
                .header("Authorization", "Bearer token.valido")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(true))
    }

    @Test
    fun `GET health deve manter contrato de disponibilidade`() {
        mockMvc.perform(get("/api/auth/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.module").value("authentication"))
    }
}

