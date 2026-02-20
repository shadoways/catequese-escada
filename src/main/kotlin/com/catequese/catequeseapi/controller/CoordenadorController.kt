package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Coordenador
import com.catequese.catequeseapi.repository.CoordenadorRepository
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
@RequestMapping("/api/coordenadores")
class CoordenadorController(private val repo: CoordenadorRepository) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Coordenador>> = ResponseEntity.ok(repo.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Coordenador> = ResponseEntity.ok(
        repo.findById(id).orElseThrow { ResourceNotFoundException("Coordenador não encontrado") }
    )

    @PostMapping
    fun create(@RequestBody c: Coordenador): ResponseEntity<Coordenador> {
        val saved = repo.save(c)
        return ResponseEntity.created(URI("/api/coordenadores/${saved.idCoordenador}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody c: Coordenador): ResponseEntity<Coordenador> {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Coordenador não encontrado") }
        val updated = existing.copy(
            nome = c.nome,
            telefone = c.telefone,
            email = c.email,
            nivelOrganizacional = c.nivelOrganizacional,
            dataNascimento = c.dataNascimento,
            dataInicio = c.dataInicio,
            ativo = c.ativo
        )
        return ResponseEntity.ok(repo.save(updated))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        repo.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
