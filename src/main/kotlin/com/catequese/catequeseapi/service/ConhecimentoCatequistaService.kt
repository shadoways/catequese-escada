package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.ConhecimentoCatequista
import com.catequese.catequeseapi.repository.ConhecimentoCatequistaRepository
import org.springframework.stereotype.Service

@Service
class ConhecimentoCatequistaService(private val repo: ConhecimentoCatequistaRepository) {
    fun findAll(): List<ConhecimentoCatequista>{
        return repo.findAll()
    }

    fun findById(id: Long): ConhecimentoCatequista{
        return repo.findById(id).orElseThrow { ResourceNotFoundException("Conhecimento não encontrado") }
    }

    fun create(conhecimentoCatequista: ConhecimentoCatequista): ConhecimentoCatequista{
        return repo.save(conhecimentoCatequista)
    }

    fun update(id: Long, conhecimentoCatequista: ConhecimentoCatequista): ConhecimentoCatequista{
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Conhecimento não encontrado") }
        val updated = existing.copy(
            areaConhecimento = conhecimentoCatequista.areaConhecimento,
            nivel = conhecimentoCatequista.nivel,
            descricao = conhecimentoCatequista.descricao,
            catequista = conhecimentoCatequista.catequista
        )
        return repo.save(updated)
    }

    fun remove(id: Long){
        repo.deleteById(id)
    }
}