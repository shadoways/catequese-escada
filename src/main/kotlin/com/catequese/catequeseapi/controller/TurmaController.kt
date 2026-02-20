package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.TurmaRepository
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
@RequestMapping("/api/turmas")
class TurmaController(private val repo: TurmaRepository) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Turma>> = ResponseEntity.ok(repo.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Turma> = ResponseEntity.ok(
        repo.findById(id).orElseThrow { ResourceNotFoundException("Turma não encontrada") }
    )

    @PostMapping
    fun create(@RequestBody turma: Turma): ResponseEntity<Turma> {
        val saved = repo.save(turma)
        return ResponseEntity.created(URI("/api/turmas/${saved.idTurma}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody turma: Turma): ResponseEntity<Turma> {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Turma não encontrada") }
        val updated = existing.copy(
            nome = turma.nome,
            descricao = turma.descricao,
            ano = turma.ano,
            nivel = turma.nivel,
            catequista = turma.catequista
        )
        return ResponseEntity.ok(repo.save(updated))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        repo.deleteById(id)
        return ResponseEntity.noContent().build()
    }
}
