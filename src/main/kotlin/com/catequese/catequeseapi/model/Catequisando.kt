package com.catequese.catequeseapi.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "tb_catequisando")
data class Catequisando(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idCatequisando: Long = 0,

    val nome: String,
    val telefone: String?,
    val email: String?,
    val dataNascimento: LocalDate?,
    val nomeResponsavel: String?,
    val telefoneResponsavel: String?,
    val endereco: String?,
    val numeroDocumento: String?,
    val tipoDocumento: String?,
    val intoleranteGluten: Boolean = false,
    val foiBatizado: Boolean = false,
    val fezPrimeiraEucaristia: Boolean = false,
    val estadoConjugal: String?,
    val ativo: Boolean = true,

    @ManyToOne
    @JoinColumn(name = "id_turma")
    val turma: Turma? = null,

    @ManyToOne
    @JoinColumn(name = "id_comunidade")
    val comunidade: Comunidade? = null,

    @OneToMany(mappedBy = "catequisando")
    @JsonIgnore
    val presencas: List<Presenca> = emptyList(),

    @OneToMany(mappedBy = "catequisando")
    @JsonIgnore
    val fichasInscricao: List<FichaInscricao> = emptyList(),

    @OneToMany(mappedBy = "catequisando")
    @JsonIgnore
    val documentos: List<Documento> = emptyList()
)