package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Documento
import com.catequese.catequeseapi.repository.DocumentoRepository
import org.springframework.stereotype.Service

@Service
class DocumentoService(private val repo: DocumentoRepository) {

    fun findAll(): List<Documento> = repo.findAll()

    fun findById(id: Long): Documento = repo.findById(id).orElseThrow { ResourceNotFoundException("Documento não encontrado") }

    fun create(d: Documento): Documento = repo.save(d)

    fun update(id: Long, d: Documento): Documento {
        val existing = repo.findById(id).orElseThrow { ResourceNotFoundException("Documento não encontrado") }
        val updated = existing.copy(
            tipoDocumento = d.tipoDocumento,
            caminhoArquivo = d.caminhoArquivo,
            dataEnvio = d.dataEnvio,
            catequisando = d.catequisando
        )
        return repo.save(updated)
    }

    fun remove(id: Long) = repo.deleteById(id)
}