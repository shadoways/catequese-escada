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

    /**
     * Encontros amarrados a um evento da agenda.
     *
     * E por aqui que a tela de Eventos sabe quem participou: o evento em si nao
     * guarda presenca de catequisando -- quem guarda e o encontro que alguem
     * abriu a partir dele.
     */
    fun findByIdEvento(idEvento: Long): List<Encontro>

    /** Alimenta o fechamento automatico: abertos de dias anteriores. */
    fun findAllBySituacaoAndDataBefore(
        situacao: SituacaoEncontro,
        data: LocalDate
    ): List<Encontro>
}
