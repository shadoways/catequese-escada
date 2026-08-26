package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.repository.EventoRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Leitura crua dos eventos.
 *
 * As rotas de escrita foram REMOVIDAS daqui de proposito. Elas gravavam o
 * evento direto pelo repositorio, sem passar pela permissao por nivel -- ou
 * seja, um coordenador de comunidade podia criar um evento DIOCESANO por esta
 * rota e contornar inteiramente a regra da agenda. Como nenhuma tela chamava
 * /api/eventos (o frontend usa /api/agenda e /api/chamada/eventos), tirar foi
 * mais seguro do que duplicar a checagem em dois lugares.
 *
 * Para criar, alterar ou excluir evento: /api/agenda/eventos.
 */
@RestController
@RequestMapping("/api/eventos")
class EventoController(private val repo: EventoRepository) {

    @GetMapping
    fun getAll(): ResponseEntity<List<Evento>> = ResponseEntity.ok(repo.findAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<Evento> = ResponseEntity.ok(
        repo.findById(id).orElseThrow { ResourceNotFoundException("Evento não encontrado") }
    )
}
