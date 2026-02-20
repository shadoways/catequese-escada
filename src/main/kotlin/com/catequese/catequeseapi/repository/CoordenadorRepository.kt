package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Coordenador
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CoordenadorRepository : JpaRepository<Coordenador, Long>