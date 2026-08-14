package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Encontro
import com.catequese.catequeseapi.model.SituacaoEncontro
import com.catequese.catequeseapi.model.Turma
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
interface EncontroRepository : JpaRepository<Encontro, Long> {

    fun findByTurmaOrderByDataDesc(turma: Turma): List<Encontro>

    fun findByTurmaAndData(turma: Turma, data: LocalDate): Encontro?

    /** Usado para impedir abrir um encontro novo com outro ainda aberto. */
    fun findFirstByTurmaAndSituacao(turma: Turma, situacao: SituacaoEncontro): Encontro?

    fun findByTurmaAndSituacaoAndDataBetween(
        turma: Turma,
        situacao: SituacaoEncontro,
        inicio: LocalDate,
        fim: LocalDate
    ): List<Encontro>

    /** Alimenta o fechamento automatico: abertos de dias anteriores. */
    fun findAllBySituacaoAndDataBefore(
        situacao: SituacaoEncontro,
        data: LocalDate
    ): List<Encontro>
}
