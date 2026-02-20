package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Permissao
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PermissaoRepository : JpaRepository<Permissao, Long>