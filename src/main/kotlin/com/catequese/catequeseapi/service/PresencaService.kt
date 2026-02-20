package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Presenca
import com.catequese.catequeseapi.repository.PresencaRepository
import org.springframework.stereotype.Service

@Service
class PresencaService(private val repo: PresencaRepository) {

    fun findAll(): List<Presenca> = repo.findAll()

    fun findById(id: Long): Presenca = repo.findById(id).orElseThrow { ResourceNotFoundException("Presença não encontrada") }

    fun create(p: Presenca): Presenca = repo.save(p)

    fun update(id: Long, p: Presenca): Presenca {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Presença não encontrada") }
        val updated = existing.copy(
            data = p.data,
            presente = p.presente,
            catequisando = p.catequisando
        )
        return repo.save(updated)
    }

    fun remove(id: Long) = repo.deleteById(id)
}