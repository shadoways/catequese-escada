package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "tb_turma")
data class Turma(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idTurma: Long = 0,

    val nome: String,
    val descricao: String?,
    val ano: Int?,
    val nivel: String?,

    /**
     * Decide a regra de frequencia. Enquanto estiver nulo, a turma nao e
     * avaliada -- o administrador classifica as turmas antigas uma vez.
     */
    @Enumerated(EnumType.STRING)
    val categoria: CategoriaTurma? = null,

    /**
     * Fase do percurso: 1 = primeira fase, 2 = segunda.
     *
     * So Eucaristia e Crisma tem fase; nas demais e sempre nulo. Quem decide
     * isso e `RegrasDeMovimentacao.temFases`, e a classificacao da turma zera
     * este campo ao mudar para uma categoria sem fase.
     */
    val etapa: Int? = null,

    /**
     * Comunidade dona da turma.
     *
     * Antes disso a comunidade da turma era deduzida da comunidade dos
     * catequisandos matriculados -- o que fazia turma vazia nao pertencer a
     * lugar nenhum e turma com gente de duas comunidades aparecer para dois
     * coordenadores. Com o campo, a turma diz de quem ela e.
     *
     * Anulavel porque as turmas que ja existem nao tem como ser classificadas
     * sozinhas: o coordenador paroquial preenche uma vez, e ate la a turma se
     * comporta como antes.
     */
    @Column(name = "id_comunidade")
    val idComunidade: Long? = null,

    @ManyToOne
    @JoinColumn(name = "id_catequista")
    val catequista: Catequista? = null,

    @OneToMany(mappedBy = "turma")
    @JsonIgnore
    val catequisandos: List<Catequisando> = emptyList()
)