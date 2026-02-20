package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.repository.EventoRepository
import org.springframework.stereotype.Service

@Service
class EventoService(private val repo: EventoRepository) {

    fun findAll(): List<Evento> = repo.findAll()

    fun findById(id: Long): Evento = repo.findById(id).orElseThrow { ResourceNotFoundException("Evento não encontrado") }

    fun create(e: Evento): Evento = repo.save(e)

    fun update(id: Long, e: Evento): Evento {
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
        return repo.save(updated)
    }

    fun remove(id: Long) = repo.deleteById(id)
}