package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.model.Comunidade
import com.catequese.catequeseapi.repository.ComunidadeRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/comunidades")
class ComunidadeController(
    @Autowired
    val comunidadeRepository: ComunidadeRepository
) {
    @GetMapping
    fun listarTodas(): List<Comunidade> = comunidadeRepository.findAll()

    @GetMapping("/{id}")
    fun buscarPorId(@PathVariable id: Long): ResponseEntity<Comunidade> {
        val comunidade = comunidadeRepository.findById(id)
        return if (comunidade.isPresent) ResponseEntity.ok(comunidade.get())
        else ResponseEntity.notFound().build()
    }

    @PostMapping
    fun criar(@RequestBody comunidade: Comunidade): Comunidade = comunidadeRepository.save(comunidade)

    @PutMapping("/{id}")
    fun atualizar(
        @PathVariable id: Long,
        @RequestBody comunidadeAtualizada: Comunidade
    ): ResponseEntity<Comunidade> {
        return if (comunidadeRepository.existsById(id)) {
            val updated = comunidadeAtualizada.copy(idComunidade = id)
            ResponseEntity.ok(comunidadeRepository.save(updated))
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{id}")
    fun deletar(@PathVariable id: Long): ResponseEntity<Void> {
        return if (comunidadeRepository.existsById(id)) {
            comunidadeRepository.deleteById(id)
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}

