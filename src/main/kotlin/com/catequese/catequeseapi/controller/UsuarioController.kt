package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.auth.CreateUsuarioDTO
import com.catequese.catequeseapi.dto.auth.UsuarioDTO
import com.catequese.catequeseapi.service.UsuarioService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/api/usuarios")
class UsuarioController(private val usuarioService: UsuarioService) {

    companion object {
        private val logger = LoggerFactory.getLogger(UsuarioController::class.java)
    }

    /**
     * GET /api/usuarios
     * Lista todos os usuários
     */
    @GetMapping
    fun getAll(): ResponseEntity<List<UsuarioDTO>> {
        logger.info("📥 GET /api/usuarios - Listando todos os usuários")
        val usuarios = usuarioService.findAll()
        logger.info("✅ Encontrados ${usuarios.size} usuários")
        return ResponseEntity.ok(usuarios)
    }

    /**
     * GET /api/usuarios/{id}
     * Busca usuário por ID
     */
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<UsuarioDTO> {
        logger.info("📥 GET /api/usuarios/$id")
        val usuario = usuarioService.findById(id)
        logger.info("✅ Usuário encontrado: ${usuario.email}")
        return ResponseEntity.ok(usuario)
    }

    /**
     * GET /api/usuarios/email/{email}
     * Busca usuário por email
     */
    @GetMapping("/email/{email}")
    fun getByEmail(@PathVariable email: String): ResponseEntity<UsuarioDTO> {
        logger.info("📥 GET /api/usuarios/email/$email")
        val usuario = usuarioService.findByEmail(email)
        logger.info("✅ Usuário encontrado: ${usuario.email}")
        return ResponseEntity.ok(usuario)
    }

    /**
     * POST /api/usuarios
     * Cria novo usuário
     */
    @PostMapping
    fun create(@RequestBody dto: CreateUsuarioDTO): ResponseEntity<UsuarioDTO> {
        logger.info("📥 POST /api/usuarios - Criando usuário: ${dto.email}")
        val usuario = usuarioService.create(dto)
        logger.info("✅ Usuário criado: ID=${usuario.idUsuario}")
        return ResponseEntity.created(URI("/api/usuarios/${usuario.idUsuario}")).body(usuario)
    }

    /**
     * PUT /api/usuarios/{id}
     * Atualiza usuário existente
     */
    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody dto: UsuarioDTO): ResponseEntity<UsuarioDTO> {
        logger.info("📥 PUT /api/usuarios/$id")
        val usuario = usuarioService.update(id, dto)
        logger.info("✅ Usuário atualizado: ${usuario.email}")
        return ResponseEntity.ok(usuario)
    }

    /**
     * PATCH /api/usuarios/{id}/toggle-ativo
     * Ativa ou desativa usuário
     */
    @PatchMapping("/{id}/toggle-ativo")
    fun toggleAtivo(@PathVariable id: Long): ResponseEntity<UsuarioDTO> {
        logger.info("📥 PATCH /api/usuarios/$id/toggle-ativo")
        val usuario = usuarioService.toggleAtivo(id)
        logger.info("✅ Status alterado: ativo=${usuario.ativo}")
        return ResponseEntity.ok(usuario)
    }

    /**
     * DELETE /api/usuarios/{id}
     * Deleta usuário
     */
    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        logger.info("📥 DELETE /api/usuarios/$id")
        usuarioService.delete(id)
        logger.info("✅ Usuário deletado")
        return ResponseEntity.noContent().build()
    }
}

