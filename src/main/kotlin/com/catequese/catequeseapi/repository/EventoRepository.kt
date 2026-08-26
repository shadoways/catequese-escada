package com.catequese.catequeseapi.repository


import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.model.TipoEvento
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EventoRepository : JpaRepository<Evento, Long> {

    fun findByIdFormacaoOrderByDataInicioAsc(idFormacao: Long): List<Evento>

    fun findByTipoOrderByDataInicioAsc(tipo: TipoEvento): List<Evento>

    /**
     * Eventos do periodo. Quem nao tem data cai fora de proposito: um evento
     * sem data nao tem onde aparecer numa agenda ordenada por mes, e a tela de
     * cadastro exige a data de inicio.
     */
    @Query(
        """
        SELECT e FROM Evento e
        WHERE e.dataInicio IS NOT NULL
          AND e.dataInicio BETWEEN :inicio AND :fim
        ORDER BY e.dataInicio ASC, e.titulo ASC
        """
    )
    fun findNoPeriodo(
        @Param("inicio") inicio: LocalDate,
        @Param("fim") fim: LocalDate
    ): List<Evento>
}
