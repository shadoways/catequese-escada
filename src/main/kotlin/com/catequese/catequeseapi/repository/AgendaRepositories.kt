package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Formacao
import com.catequese.catequeseapi.model.FormacaoInscrito
import com.catequese.catequeseapi.model.PresencaFormacao
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FormacaoRepository : JpaRepository<Formacao, Long> {
    fun findByAnoOrderByNomeAsc(ano: Int): List<Formacao>
}

@Repository
interface FormacaoInscritoRepository : JpaRepository<FormacaoInscrito, Long> {
    fun findByIdFormacao(idFormacao: Long): List<FormacaoInscrito>
    fun findByIdCatequista(idCatequista: Long): List<FormacaoInscrito>
    fun existsByIdFormacaoAndIdCatequista(idFormacao: Long, idCatequista: Long): Boolean
    fun deleteByIdFormacaoAndIdCatequista(idFormacao: Long, idCatequista: Long)
}

@Repository
interface PresencaFormacaoRepository : JpaRepository<PresencaFormacao, Long> {
    fun findByIdEvento(idEvento: Long): List<PresencaFormacao>
    fun findByIdEventoIn(idsEvento: Collection<Long>): List<PresencaFormacao>
    fun findByIdCatequista(idCatequista: Long): List<PresencaFormacao>

    // Sem deleteByIdEvento de proposito: refazer a chamada apaga e reinsere na
    // mesma transacao, e o delete derivado nao garante ordem contra os inserts.
    // O FormacaoService faz deleteAll + flush explicito.
}
