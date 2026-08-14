package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.EtapaCatecumeno
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EtapaCatecumenoRepository : JpaRepository<EtapaCatecumeno, Long> {

    fun findByCatequisandoOrderByDataInicioAsc(catequisando: Catequisando): List<EtapaCatecumeno>

    /** A etapa em andamento e a que ainda nao tem data de fim. */
    fun findFirstByCatequisandoAndDataFimIsNull(catequisando: Catequisando): EtapaCatecumeno?
}
