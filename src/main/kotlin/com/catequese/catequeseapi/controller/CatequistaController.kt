package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.model.Catequista
import com.catequese.catequeseapi.service.CatequistaService
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
@RequestMapping("/api/catequistas")
class CatequistaController(private val service: CatequistaService) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Catequista>> = ResponseEntity.ok(service.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Catequista> = ResponseEntity.ok(service.findById(id))

    @PostMapping
    fun create(@RequestBody catequista: Catequista): ResponseEntity<Catequista> {
        val saved = service.create(catequista)
        return ResponseEntity.created(URI("/api/catequistas/${saved.idCatequista}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody catequista: Catequista): ResponseEntity<Catequista> =
        ResponseEntity.ok(service.update(id, catequista))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        service.remove(id)
        return ResponseEntity.noContent().build()
    }
}
