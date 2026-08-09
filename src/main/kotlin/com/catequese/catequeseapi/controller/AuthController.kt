package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.LoginRequestDTO
import com.catequese.catequeseapi.dto.UsuarioLogadoDTO
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    fun login(@RequestBody body: LoginRequestDTO): ResponseEntity<Any> {
        val username = body.username.trim()
        val usuario = usuarioRepository.findByUsername(username)

        // Mesma resposta para usuario inexistente, inativo ou senha errada:
        // nao entregamos ao atacante a informacao de qual usuario existe.
        val senhaConfere = usuario != null &&
            usuario.ativo &&
            passwordEncoder.matches(body.password, usuario.passwordHash)

        if (usuario == null || !senhaConfere) {
            log.info("Login recusado para '{}'", username)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("erro" to "Usuario ou senha invalidos"))
        }

        log.info("Login efetuado: '{}' ({})", usuario.username, usuario.tipo)
        return ResponseEntity.ok(paraDTO(usuario, jwtService.gerarToken(usuario)))
    }

    /** Usado pelo front para saber quem esta logado ao recarregar a pagina. */
    @GetMapping("/me")
    fun me(): ResponseEntity<Any> {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: return naoAutenticado()

        val usuario = usuarioRepository.findByUsername(username)
            ?: return naoAutenticado()

        if (!usuario.ativo) return naoAutenticado()

        return ResponseEntity.ok(paraDTO(usuario, null))
    }

    private fun naoAutenticado(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("erro" to "Nao autenticado"))

    private fun paraDTO(usuario: Usuario, token: String?) = UsuarioLogadoDTO(
        idUsuario = usuario.idUsuario,
        nome = usuario.nome,
        username = usuario.username,
        tipo = usuario.tipo,
        podeEditar = usuario.tipo.podeEditar,
        admin = usuario.tipo.isAdmin,
        token = token
    )
}
