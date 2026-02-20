package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Catequista
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CatequistaRepository : JpaRepository<Catequista, Long>