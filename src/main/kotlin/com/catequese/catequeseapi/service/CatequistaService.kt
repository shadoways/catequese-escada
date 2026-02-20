package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Catequista
import com.catequese.catequeseapi.repository.CatequistaRepository
import org.springframework.stereotype.Service

@Service
class CatequistaService(private val repo: CatequistaRepository) {
    fun findAll(): List<Catequista>{
        return repo.findAll()
    }

    fun findById(id: Long): Catequista{
        return repo.findById(id).orElseThrow { ResourceNotFoundException("Catequista não encontrado") }
    }

    fun create(catequista: Catequista): Catequista{
       return repo.save(catequista)
    }

    fun update(id: Long, catequista: Catequista): Catequista{
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Catequista não encontrado") }
        val updated = existing.copy(
            nome = catequista.nome,
            telefone = catequista.telefone,
            email = catequista.email,
            endereco = catequista.endereco,
            dataNascimento = catequista.dataNascimento,
            dataInicio = catequista.dataInicio,
            ativo = catequista.ativo
        )
        return repo.save(updated)
    }

    fun remove(id: Long){
        repo.deleteById(id)
    }
}