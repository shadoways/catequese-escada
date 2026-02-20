package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Turma
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TurmaRepository : JpaRepository<Turma, Long>