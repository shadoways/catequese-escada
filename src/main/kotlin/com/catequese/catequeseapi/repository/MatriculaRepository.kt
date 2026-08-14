package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Catequisando
import com.catequese.catequeseapi.model.Matricula
import com.catequese.catequeseapi.model.Turma
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MatriculaRepository : JpaRepository<Matricula, Long> {

    fun findByCatequisandoOrderByAnoDesc(catequisando: Catequisando): List<Matricula>

    fun findByTurmaAndAno(turma: Turma, ano: Int): List<Matricula>

    fun findByTurma(turma: Turma): List<Matricula>

    fun findByCatequisandoAndTurmaAndAno(
        catequisando: Catequisando,
        turma: Turma,
        ano: Int
    ): Matricula?

    fun existsByCatequisandoAndTurmaAndAno(
        catequisando: Catequisando,
        turma: Turma,
        ano: Int
    ): Boolean

    fun countByCatequisando(catequisando: Catequisando): Long
}
