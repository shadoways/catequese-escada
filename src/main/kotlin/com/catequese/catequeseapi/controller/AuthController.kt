package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.LoginRequestDTO
import com.catequese.catequeseapi.dto.TrocarSenhaDTO
import com.catequese.catequeseapi.dto.UsuarioLogadoDTO
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.security.JwtService
import com.catequese.catequeseapi.security.PoliticaSenha
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
import java.time.Duration
import java.time.LocalDateTime

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
        val agora = LocalDateTime.now()

        // Bloqueio ativo: nem tenta conferir a senha.
        val bloqueadoAte = usuario?.bloqueadoAte
        if (bloqueadoAte != null && bloqueadoAte.isAfter(agora)) {
            val minutos = Duration.between(agora, bloqueadoAte).toMinutes() + 1
            log.warn("Login bloqueado para '{}' por mais {} minuto(s)", username, minutos)
            return ResponseEntity.status(HttpStatus.LOCKED).body(
                mapOf(
                    "erro" to "Conta temporariamente bloqueada por excesso de tentativas. " +
                        "Tente novamente em $minutos minuto(s).",
                    "codigo" to "CONTA_BLOQUEADA"
                )
            )
        }

        val senhaConfere = usuario != null &&
            usuario.ativo &&
            passwordEncoder.matches(body.password, usuario.passwordHash)

        if (usuario == null || !senhaConfere) {
            // Conta o erro so quando o usuario existe -- nao ha onde contar para
            // um username inexistente, e criar registro para isso viraria vetor de spam.
            if (usuario != null && usuario.ativo) registrarFalha(usuario, agora)

            log.info("Login recusado para '{}'", username)
            // Mesma resposta para usuario inexistente, inativo ou senha errada:
            // nao entregamos ao atacante a informacao de qual usuario existe.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("erro" to "Usuario ou senha invalidos"))
        }

        val logado = usuarioRepository.save(
            usuario.copy(tentativasFalhas = 0, bloqueadoAte = null, ultimoLogin = agora)
        )

        log.info("Login efetuado: '{}' ({})", logado.username, logado.tipo)
        return ResponseEntity.ok(paraDTO(logado, jwtService.gerarToken(logado)))
    }

    /** Usado pelo front para saber quem esta logado ao recarregar a pagina. */
    @GetMapping("/me")
    fun me(): ResponseEntity<Any> {
        val usuario = usuarioAutenticado() ?: return naoAutenticado()
        return ResponseEntity.ok(paraDTO(usuario, null))
    }

    /**
     * Troca da propria senha. Serve tanto para a troca obrigatoria do primeiro
     * acesso quanto para uma troca voluntaria depois.
     */
    @PostMapping("/trocar-senha")
    fun trocarSenha(@RequestBody body: TrocarSenhaDTO): ResponseEntity<Any> {
        val usuario = usuarioAutenticado() ?: return naoAutenticado()

        if (!passwordEncoder.matches(body.senhaAtual, usuario.passwordHash)) {
            log.info("Troca de senha recusada para '{}': senha atual incorreta", usuario.username)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(mapOf("erro" to "Senha atual incorreta"))
        }

        if (passwordEncoder.matches(body.novaSenha, usuario.passwordHash)) {
            return ResponseEntity.badRequest()
                .body(mapOf("erro" to "A nova senha deve ser diferente da atual."))
        }

        PoliticaSenha.validar(body.novaSenha, usuario.username, usuario.email)?.let { problema ->
            return ResponseEntity.badRequest().body(mapOf("erro" to problema))
        }

        val atualizado = usuarioRepository.save(
            usuario.copy(
                passwordHash = passwordEncoder.encode(body.novaSenha),
                senhaProvisoria = false,
                dataTrocaSenha = LocalDateTime.now()
            )
        )

        log.info("Senha trocada por '{}'", atualizado.username)
        // Token novo: a troca de senha invalida os tokens emitidos antes dela,
        // inclusive o que o usuario esta usando agora.
        return ResponseEntity.ok(paraDTO(atualizado, jwtService.gerarToken(atualizado)))
    }

    private fun registrarFalha(usuario: Usuario, agora: LocalDateTime) {
        val tentativas = usuario.tentativasFalhas + 1
        val estourou = tentativas >= MAX_TENTATIVAS

        usuarioRepository.save(
            usuario.copy(
                tentativasFalhas = if (estourou) 0 else tentativas,
                bloqueadoAte = if (estourou) agora.plusMinutes(BLOQUEIO_MINUTOS) else usuario.bloqueadoAte
            )
        )

        if (estourou) {
            log.warn(
                "Usuario '{}' bloqueado por {} minutos apos {} tentativas erradas",
                usuario.username, BLOQUEIO_MINUTOS, MAX_TENTATIVAS
            )
        }
    }

    private fun usuarioAutenticado(): Usuario? {
        val username = SecurityContextHolder.getContext().authentication?.name ?: return null
        return usuarioRepository.findByUsername(username)?.takeIf { it.ativo }
    }

    private fun naoAutenticado(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(mapOf("erro" to "Nao autenticado"))

    private fun paraDTO(usuario: Usuario, token: String?) = UsuarioLogadoDTO(
        idUsuario = usuario.idUsuario,
        nome = usuario.nome,
        username = usuario.username,
        email = usuario.email,
        tipo = usuario.tipo,
        podeEditar = usuario.tipo.podeEditar,
        admin = usuario.tipo.isAdmin,
        senhaProvisoria = usuario.senhaProvisoria,
        token = token
    )

    private companion object {
        const val MAX_TENTATIVAS = 5
        const val BLOQUEIO_MINUTOS = 15L
    }
}
