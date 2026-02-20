package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Presenca
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PresencaRepository : JpaRepository<Presenca, Long>