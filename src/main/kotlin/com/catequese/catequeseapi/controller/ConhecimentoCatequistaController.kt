package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.model.ConhecimentoCatequista
import com.catequese.catequeseapi.service.ConhecimentoCatequistaService
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
@RequestMapping("/api/conhecimentos")
class ConhecimentoCatequistaController(private val service: ConhecimentoCatequistaService) {

    @GetMapping
    fun getAll(): ResponseEntity<List<ConhecimentoCatequista>> = ResponseEntity.ok(service.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<ConhecimentoCatequista> = ResponseEntity.ok(service.findById(id))

    @PostMapping
    fun create(@RequestBody conhecimento: ConhecimentoCatequista): ResponseEntity<ConhecimentoCatequista> {
        val saved = service.create(conhecimento)
        return ResponseEntity.created(URI("/api/conhecimentos/${saved.idConhecimento}")).body(saved)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody conhecimento: ConhecimentoCatequista): ResponseEntity<ConhecimentoCatequista> =
        ResponseEntity.ok(service.update(id, conhecimento))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        service.remove(id)
        return ResponseEntity.noContent().build()
    }
}
