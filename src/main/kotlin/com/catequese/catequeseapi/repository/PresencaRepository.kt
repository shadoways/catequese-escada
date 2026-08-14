package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.Encontro
import com.catequese.catequeseapi.model.Presenca
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PresencaRepository : JpaRepository<Presenca, Long> {

    fun findByEncontro(encontro: Encontro): List<Presenca>

    fun findByEncontroAndCatequisando(encontro: Encontro, catequisando: Catequisando): Presenca?

    fun findByCatequisando(catequisando: Catequisando): List<Presenca>

    /** Presencas de um catequisando em um conjunto de encontros (um periodo). */
    fun findByCatequisandoAndEncontroIn(
        catequisando: Catequisando,
        encontros: List<Encontro>
    ): List<Presenca>

    fun findByEncontroIn(encontros: List<Encontro>): List<Presenca>

    fun countByEncontro(encontro: Encontro): Long
}
