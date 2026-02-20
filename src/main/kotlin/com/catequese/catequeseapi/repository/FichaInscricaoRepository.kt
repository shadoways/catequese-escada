package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.FichaInscricao
import com.catequese.catequeseapi.model.Catequisando
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface FichaInscricaoRepository : JpaRepository<FichaInscricao, Long> {
    @Transactional
    fun deleteByCatequisando(catequisando: Catequisando)
}
