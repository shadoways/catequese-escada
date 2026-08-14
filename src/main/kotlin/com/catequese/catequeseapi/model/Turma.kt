package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
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

    /** 1 = primeiro ano (Crisma I), 2 = segundo ano (Crisma II). */
    val etapa: Int? = null,

    @ManyToOne
    @JoinColumn(name = "id_catequista")
    val catequista: Catequista? = null,

    @OneToMany(mappedBy = "turma")
    @JsonIgnore
    val catequisandos: List<Catequisando> = emptyList()
)