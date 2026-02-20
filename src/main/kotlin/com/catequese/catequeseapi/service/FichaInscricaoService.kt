package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.FichaInscricao
import com.catequese.catequeseapi.repository.FichaInscricaoRepository
import org.springframework.stereotype.Service

@Service
class FichaInscricaoService(private val repo: FichaInscricaoRepository) {

    fun findAll(): List<FichaInscricao> = repo.findAll()

    fun findById(id: Long): FichaInscricao = repo.findById(id).orElseThrow { ResourceNotFoundException("Ficha não encontrada") }

    fun create(f: FichaInscricao): FichaInscricao = repo.save(f)

    fun update(id: Long, f: FichaInscricao): FichaInscricao {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Ficha não encontrada") }
        val updated = existing.copy(
            dataInscricao = f.dataInscricao,
            observacoes = f.observacoes,
            catequisando = f.catequisando
        )
        return repo.save(updated)
    }

    fun remove(id: Long) = repo.deleteById(id)
}