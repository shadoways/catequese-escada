package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.Coordenador
import com.catequese.catequeseapi.repository.CoordenadorRepository
import org.springframework.stereotype.Service

@Service
class CoordenadorService(private val repo: CoordenadorRepository) {
    fun findAll(): List<Coordenador>{
        return repo.findAll()
    }

    fun findById(id: Long): Coordenador{
        return repo.findById(id).orElseThrow { RuntimeException("Coordenador não encontrado") }
    }
}