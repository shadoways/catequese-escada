package com.catequese.catequeseapi.repository


import com.catequese.catequeseapi.model.Evento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface EventoRepository : JpaRepository<Evento, Long>