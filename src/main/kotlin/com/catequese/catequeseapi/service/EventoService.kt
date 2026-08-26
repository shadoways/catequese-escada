package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.repository.EventoRepository
import org.springframework.stereotype.Service

/**
 * Leitura de eventos.
 *
 * As operacoes de escrita sairam daqui junto com as rotas de /api/eventos:
 * gravavam sem checar a permissao por nivel. Quem cria e altera evento agora e
 * o AgendaService, que consulta o AgendaPermissaoService antes de salvar.
 */
@Service
class EventoService(private val repo: EventoRepository) {

    fun findAll(): List<Evento> = repo.findAll()

    fun findById(id: Long): Evento = repo.findById(id)
        .orElseThrow { ResourceNotFoundException("Evento não encontrado") }
}
