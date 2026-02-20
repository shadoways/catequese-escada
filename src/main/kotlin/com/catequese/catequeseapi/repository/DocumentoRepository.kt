package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Documento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DocumentoRepository : JpaRepository<Documento, Long>