package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.repository.EventoRepository
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
@RequestMapping("/api/eventos")
class EventoController(private val repo: EventoRepository) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Evento>> = ResponseEntity.ok(repo.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Evento> = ResponseEntity.ok(
        repo.findById(id).orElseThrow { ResourceNotFoundException("Evento não encontrado") }
    )

    @PostMapping
    fun create(@RequestBody e: Evento): ResponseEntity<Evento> {
        val saved = repo.save(e)
        return ResponseEntity.created(URI("/api/eventos/${saved.idEvento}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody e: Evento): ResponseEntity<Evento> {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Evento não encontrado") }
        val updated = existing.copy(
            titulo = e.titulo,
            nivel = e.nivel,
            publicoAlvo = e.publicoAlvo,
            descricao = e.descricao,
            dataInicio = e.dataInicio,
            dataFim = e.dataFim,
            local = e.local
        )
        return ResponseEntity.ok(repo.save(updated))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        repo.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
