package com.catequese.catequeseapi.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Configuracoes do sistema, no formato chave/valor.
 *
 * Chave/valor em vez de uma coluna por opcao: cada ajuste novo vira uma linha,
 * sem migracao de banco. Hoje so existe o interruptor do cadastro publico.
 */
@Entity
@Table(name = "tb_configuracao")
data class Configuracao(
    @Id
    val chave: String = "",

    val valor: String = "",

    val descricao: String? = null,

    @Column(name = "atualizado_em")
    val atualizadoEm: LocalDateTime? = null,

    /** Username de quem mexeu por ultimo, para saber a quem perguntar. */
    @Column(name = "atualizado_por")
    val atualizadoPor: String? = null
) {
    companion object {
        const val CADASTRO_ABERTO = "cadastro.aberto"
    }
}
