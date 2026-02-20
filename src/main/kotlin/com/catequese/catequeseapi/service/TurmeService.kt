package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Turma
import com.catequese.catequeseapi.repository.TurmaRepository
import org.springframework.stereotype.Service

@Service
class TurmaService(private val repo: TurmaRepository) {

    fun findAll(): List<Turma> = repo.findAll()

    fun findById(id: Long): Turma = repo.findById(id).orElseThrow { ResourceNotFoundException("Turma não encontrada") }

    fun create(t: Turma): Turma = repo.save(t)

    fun update(id: Long, t: Turma): Turma {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Turma não encontrada") }
        val updated = existing.copy(
            nome = t.nome,
            descricao = t.descricao,
            ano = t.ano,
            nivel = t.nivel,
            catequista = t.catequista
        )
        return repo.save(updated)
    }

    fun remove(id: Long) = repo.deleteById(id)
}