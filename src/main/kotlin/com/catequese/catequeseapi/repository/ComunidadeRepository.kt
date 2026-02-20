package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Comunidade
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ComunidadeRepository : JpaRepository<Comunidade, Long>

