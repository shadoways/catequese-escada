package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.ChaveInscricao
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChaveInscricaoRepository : JpaRepository<ChaveInscricao, Long> {

    fun findByCodigo(codigo: String): ChaveInscricao?

    fun existsByCodigo(codigo: String): Boolean

    fun findAllByOrderByCriadoEmDesc(): List<ChaveInscricao>
}
