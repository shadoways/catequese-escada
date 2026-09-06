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

        /**
         * A partir de que percentual o sistema comeca a avisar que a frequencia
         * esta perto do limite. Fica configuravel porque e aviso preventivo, e
         * cada paroquia pode querer ser mais ou menos cedo.
         *
         * O MINIMO de 80% nao e configuravel: e regra da catequese, nao ajuste.
         */
        const val FREQUENCIA_AVISO = "frequencia.aviso.percentual"
        const val FREQUENCIA_AVISO_PADRAO = 85

        /**
         * As tres chaves do "conhecimento minimo" da tela de Consultar
         * Catequistas. Ficam configuraveis porque o Gabriel pediu -- ao
         * contrario do minimo de frequencia de turma (que e regra da
         * catequese), aqui e o coordenador paroquial quem decide o patamar e
         * o prazo da propria paroquia.
         *
         * Chave/valor em vez de tabela nova: mesmo raciocinio de
         * FREQUENCIA_AVISO, e evita migracao para um ajuste que e so numero.
         */
        const val FORMACAO_MINIMO_AGREGADO = "formacao.minimo.agregado"
        const val FORMACAO_MINIMO_AGREGADO_PADRAO = 80

        /** Mes (1-12) em que o ano de formacao do catequista "fecha". */
        const val FORMACAO_FECHAMENTO_MES = "formacao.fechamento.mes"
        const val FORMACAO_FECHAMENTO_MES_PADRAO = 11 // novembro, sugestao do Gabriel

        /** Quantos meses antes do fechamento o alerta amarelo comeca a aparecer. */
        const val FORMACAO_ALERTA_MESES_ANTES = "formacao.alerta.meses_antes"
        const val FORMACAO_ALERTA_MESES_ANTES_PADRAO = 2
    }
}
