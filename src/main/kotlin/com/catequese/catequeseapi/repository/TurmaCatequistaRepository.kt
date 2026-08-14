package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.TurmaCatequista
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TurmaCatequistaRepository : JpaRepository<TurmaCatequista, Long> {

    fun findByIdTurma(idTurma: Long): List<TurmaCatequista>

    fun findByIdCatequista(idCatequista: Long): List<TurmaCatequista>

    fun existsByIdTurmaAndIdCatequista(idTurma: Long, idCatequista: Long): Boolean

    fun deleteByIdTurmaAndIdCatequista(idTurma: Long, idCatequista: Long)
}
