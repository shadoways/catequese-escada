package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Presenca
import com.catequese.catequeseapi.repository.PresencaRepository
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
@RequestMapping("/api/presencas")
class PresencaController(private val repo: PresencaRepository) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Presenca>> = ResponseEntity.ok(repo.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Presenca> = ResponseEntity.ok(
        repo.findById(id).orElseThrow { ResourceNotFoundException("Presença não encontrada") }
    )

    @PostMapping
    fun create(@RequestBody presenca: Presenca): ResponseEntity<Presenca> {
        val saved = repo.save(presenca)
        return ResponseEntity.created(URI("/api/presencas/${saved.idPresenca}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody presenca: Presenca): ResponseEntity<Presenca> {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Presença não encontrada") }
        val updated = existing.copy(
            data = presenca.data,
            presente = presenca.presente,
            catequisando = presenca.catequisando
        )
        return ResponseEntity.ok(repo.save(updated))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        repo.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
