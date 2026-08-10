package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Configuracao
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConfiguracaoRepository : JpaRepository<Configuracao, String>
