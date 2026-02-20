package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Catequisando
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface CatequisandoRepository : JpaRepository<Catequisando, Long>