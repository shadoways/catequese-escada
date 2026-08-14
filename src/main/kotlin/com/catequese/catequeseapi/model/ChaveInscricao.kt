package com.catequese.catequeseapi.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Chave temporaria que libera o cadastro publico.
 *
 * Serve para que a tela de inscricao nao fique aberta a qualquer um que
 * descubra o endereco do sistema: o coordenador paroquial gera uma chave para
 * o periodo ou evento de inscricoes e divulga o link so para quem interessa.
 *
 * A chave circula entre os candidatos, entao e considerada semipublica. Por
 * isso ela expira, tem limite de usos e pode ser revogada a qualquer momento --
 * se vazar, o estrago fica contido pelos tres lados.
 */
@Entity
@Table(name = "tb_chave_inscricao")
data class ChaveInscricao(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_chave")
    val idChave: Long = 0,

    /** O que vai no link divulgado. Legivel, para tambem poder ser ditado. */
    val codigo: String = "",

    /** Para o admin lembrar do que se trata: "Inscricoes 2026 - Matriz". */
    val descricao: String? = null,

    @Column(name = "expira_em")
    val expiraEm: LocalDateTime,

    /** Nulo = sem limite de quantidade. */
    @Column(name = "limite_usos")
    val limiteUsos: Int? = null,

    val usos: Int = 0,

    val ativo: Boolean = true,

    @Column(name = "criado_por")
    val criadoPor: String? = null,

    @Column(name = "criado_em")
    val criadoEm: LocalDateTime? = null,

    @Column(name = "revogada_em")
    val revogadaEm: LocalDateTime? = null
) {
    fun expirada(agora: LocalDateTime = LocalDateTime.now()): Boolean = !expiraEm.isAfter(agora)

    // O limite vai para uma variavel local antes de ser comparado: entidades
    // JPA sao classes abertas (plugin allOpen), e Kotlin nao faz smart cast em
    // propriedade de classe aberta. Escrever "limiteUsos != null && usos >=
    // limiteUsos" nao compila.
    fun esgotada(): Boolean {
        val limite = limiteUsos ?: return false
        return usos >= limite
    }

    /** Motivo da recusa, ou null se a chave pode ser usada agora. */
    fun motivoRecusa(agora: LocalDateTime = LocalDateTime.now()): String? = when {
        !ativo || revogadaEm != null -> "Esta chave de inscricao foi revogada."
        expirada(agora) -> "O prazo desta chave de inscricao terminou."
        esgotada() -> "Esta chave de inscricao ja atingiu o limite de cadastros."
        else -> null
    }
}
