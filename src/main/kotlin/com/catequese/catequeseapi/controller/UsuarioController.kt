package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.AtualizarUsuarioDTO
import com.catequese.catequeseapi.dto.CriarUsuarioDTO
import com.catequese.catequeseapi.dto.SenhaProvisoriaDTO
import com.catequese.catequeseapi.dto.UsuarioDTO
import com.catequese.catequeseapi.service.UsuarioAdminService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/**
 * Administracao de usuarios.
 *
 * Todo este controller e restrito ao COORDENADOR_PAROQUIAL pela regra de
 * "/api/usuarios" na SecurityConfig -- por isso nao ha checagem de papel aqui.
 * (Cuidado ao editar este comentario: em Kotlin comentarios de bloco sao
 * aninhados, entao escrever a barra seguida de dois asteriscos abriria um
 * comentario dentro deste e o arquivo inteiro deixaria de compilar.)
 *
 * Nao existe exclusao: usuario sai de circulacao sendo desativado (ativo=false),
 * senao o historico de quem fez o que se perderia.
 */
@RestController
@RequestMapping("/api/usuarios")
class UsuarioController(private val service: UsuarioAdminService) {

    @GetMapping
    fun listar(): ResponseEntity<List<UsuarioDTO>> = ResponseEntity.ok(service.listar())

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Long): ResponseEntity<UsuarioDTO> =
        ResponseEntity.ok(service.buscar(id))

    @PostMapping
    fun criar(@RequestBody dto: CriarUsuarioDTO): ResponseEntity<SenhaProvisoriaDTO> {
        val criado = service.criar(dto)
        return ResponseEntity
            .created(URI("/api/usuarios/${criado.usuario.idUsuario}"))
            .body(criado)
    }

    @PutMapping("/{id}")
    fun atualizar(
        @PathVariable id: Long,
        @RequestBody dto: AtualizarUsuarioDTO
    ): ResponseEntity<UsuarioDTO> =
        ResponseEntity.ok(service.atualizar(id, dto, usuarioLogado()))

    /** Gera nova senha provisoria. A senha volta uma unica vez na resposta. */
    @PostMapping("/{id}/resetar-senha")
    fun resetarSenha(@PathVariable id: Long): ResponseEntity<SenhaProvisoriaDTO> =
        ResponseEntity.ok(service.resetarSenha(id))

    /** Libera antes da hora quem foi bloqueado por errar a senha varias vezes. */
    @PostMapping("/{id}/desbloquear")
    fun desbloquear(@PathVariable id: Long): ResponseEntity<UsuarioDTO> =
        ResponseEntity.ok(service.desbloquear(id))

    private fun usuarioLogado(): String? =
        SecurityContextHolder.getContext().authentication?.name
}
