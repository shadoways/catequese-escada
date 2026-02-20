package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Permissao
import com.catequese.catequeseapi.repository.PermissaoRepository
import org.springframework.stereotype.Service

@Service
class PermissaoService(private val repo: PermissaoRepository) {

    fun findAll(): List<Permissao> = repo.findAll()

    fun findById(id: Long): Permissao = repo.findById(id).orElseThrow { ResourceNotFoundException("Permissão não encontrada") }

    fun create(p: Permissao): Permissao = repo.save(p)

    fun update(id: Long, p: Permissao): Permissao {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Permissão não encontrada") }
        val updated = existing.copy(
            permissao = p.permissao,
            login = p.login
        )
        return repo.save(updated)
    }

    fun remove(id: Long) = repo.deleteById(id)
}