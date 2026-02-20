package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.FichaInscricao
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FichaInscricaoRepository : JpaRepository<FichaInscricao, Long>