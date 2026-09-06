package com.catequese.catequeseapi.dto

/** Uma linha do catálogo -- para a tela de gestão em Configurações (mostra ativos e inativos). */
data class RequisitoConhecimentoDTO(
    val idRequisito: Long,
    val nome: String,
    val ativo: Boolean
)

/** Corpo de POST /api/conhecimentos-exigidos. */
data class RequisitoConhecimentoCriarDTO(val nome: String)

/** Corpo de PUT /api/conhecimentos-exigidos/{id} -- renomear e/ou (re)ativar. */
data class RequisitoConhecimentoAtualizarDTO(val nome: String, val ativo: Boolean)

/** Um item do checklist de um catequista -- só os requisitos ATIVOS entram aqui. */
data class RequisitoChecklistItemDTO(
    val idRequisito: Long,
    val nome: String,
    val possui: Boolean
)

/**
 * O checklist completo de um catequista, com `podeEditar` já resolvido pelo
 * servidor -- a tela não recalcula permissão a partir do tipo de usuário
 * (regra do projeto: permissão duplicada é permissão que diverge). Só o
 * coordenador paroquial marca; o coordenador de comunidade só visualiza,
 * mesma regra 2 de tela-catequistas.md aplicada a esta aba nova.
 */
data class ChecklistConhecimentoDTO(
    val podeEditar: Boolean,
    val itens: List<RequisitoChecklistItemDTO>
)

/** Corpo de PUT /api/catequistas/{id}/conhecimentos/{idRequisito}. */
data class MarcarConhecimentoDTO(val possui: Boolean)
