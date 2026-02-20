package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.ConhecimentoCatequista
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConhecimentoCatequistaRepository : JpaRepository<ConhecimentoCatequista, Long>