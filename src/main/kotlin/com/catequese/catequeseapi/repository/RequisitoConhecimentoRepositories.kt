package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.RequisitoConhecimento
import com.catequese.catequeseapi.model.RequisitoConhecimentoMarcado
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RequisitoConhecimentoRepository : JpaRepository<RequisitoConhecimento, Long> {
    fun findByAtivoTrue(): List<RequisitoConhecimento>

    /** Evita dois conhecimentos ativos com o mesmo nome -- duplicidade que se evita ao criar. */
    fun existsByNomeIgnoreCaseAndAtivoTrue(nome: String): Boolean
}

@Repository
interface RequisitoConhecimentoMarcadoRepository : JpaRepository<RequisitoConhecimentoMarcado, Long> {
    fun findByIdCatequista(idCatequista: Long): List<RequisitoConhecimentoMarcado>
    fun findByIdCatequistaAndIdRequisito(idCatequista: Long, idRequisito: Long): RequisitoConhecimentoMarcado?
}
