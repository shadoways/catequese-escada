package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Documento
import com.catequese.catequeseapi.model.Catequisando
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DocumentoRepository : JpaRepository<Documento, Long> {
    fun findByCatequisando(catequisando: Catequisando): List<Documento>
}
