package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Permissao
import com.catequese.catequeseapi.repository.PermissaoRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/permissoes")
class PermissaoController(private val repo: PermissaoRepository) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Permissao>> = ResponseEntity.ok(repo.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Permissao> = ResponseEntity.ok(
        repo.findById(id).orElseThrow { ResourceNotFoundException("Permissão não encontrada") }
    )

    @PostMapping
    fun create(@RequestBody p: Permissao): ResponseEntity<Permissao> {
        val saved = repo.save(p)
        return ResponseEntity.created(URI("/api/permissoes/${saved.idPermissao}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody p: Permissao): ResponseEntity<Permissao> {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Permissão não encontrada") }
        val updated = existing.copy(
            permissao = p.permissao,
            login = p.login
        )
        return ResponseEntity.ok(repo.save(updated))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        repo.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
