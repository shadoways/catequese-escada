package com.catequese.catequeseapi.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * Um item da lista de conhecimentos que a paróquia exige do catequista --
 * "Kerigma", "Artigos do Credo", "Pai Nosso"... Catálogo único da paróquia,
 * não por catequista: é o coordenador paroquial quem decide o que TODOS
 * precisam saber (tela-catequistas.md, aba Conhecimentos).
 *
 * SEM RELAÇÃO com `ConhecimentoCatequista` (`tb_conhecimento_catequista`):
 * aquela é uma entidade antiga, não usada em nenhuma tela, com área/nível/
 * descrição em texto livre POR catequista (sem catálogo comum). Nomes
 * parecidos de propósito evitado -- ver a armadilha de nome de tipo colidir
 * em CLAUDE.md -- por isso o prefixo "Requisito" aqui, e não apenas
 * "Conhecimento" ou "ConhecimentoCatequista" invertido.
 *
 * `ativo`, não DELETE: "tirar" um conhecimento da exigência (o coordenador
 * quer pedir menos) esconde o item do checklist de todo catequista sem
 * apagar as marcações já feitas -- regra 3 do CLAUDE.md, nada é apagado de
 * verdade. Reativar traz o item (e o histórico de quem já tinha marcado) de
 * volta.
 */
@Entity
@Table(name = "tb_requisito_conhecimento")
data class RequisitoConhecimento(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_requisito")
    val idRequisito: Long = 0,

    val nome: String = "",

    val ativo: Boolean = true,

    @Column(name = "criado_por")
    val criadoPor: String? = null,

    @Column(name = "criado_em")
    val criadoEm: LocalDateTime? = null,

    @Column(name = "atualizado_por")
    val atualizadoPor: String? = null,

    @Column(name = "atualizado_em")
    val atualizadoEm: LocalDateTime? = null
)

/**
 * "Este catequista possui este conhecimento" -- o estado ATUAL de uma marca,
 * não um log de mudanças (mesmo espírito de `Configuracao`: uma linha por
 * chave, sobrescrita a cada alteração). `marcadoPor`/`marcadoEm` guardam
 * quem mexeu por último, que é o que a regra 4 do CLAUDE.md pede ("toda
 * marcação guarda quem e quando"); não é preciso mais que isso porque a
 * pergunta que a tela faz é "ele tem, hoje?", não "quando ele passou a ter".
 *
 * Só existe uma linha por par catequista+requisito (`uk_requisito_catequista`
 * no banco) -- marcar de novo ATUALIZA a linha, nunca duplica.
 */
@Entity
@Table(
    name = "tb_requisito_conhecimento_marcado",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_requisito_catequista",
        columnNames = ["id_requisito", "id_catequista"]
    )]
)
data class RequisitoConhecimentoMarcado(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_requisito_marcado")
    val idRequisitoMarcado: Long = 0,

    @Column(name = "id_requisito")
    val idRequisito: Long = 0,

    @Column(name = "id_catequista")
    val idCatequista: Long = 0,

    val possui: Boolean = false,

    @Column(name = "marcado_por")
    val marcadoPor: String? = null,

    @Column(name = "marcado_em")
    val marcadoEm: LocalDateTime? = null
)
