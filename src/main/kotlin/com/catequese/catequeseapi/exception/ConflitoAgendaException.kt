package com.catequese.catequeseapi.exception

import com.catequese.catequeseapi.dto.ConflitoDTO

/**
 * Ja existe evento marcado para o mesmo dia atingindo as mesmas pessoas.
 *
 * Vira 409 (Conflict), e nao 400: o pedido esta correto: o problema e o
 * estado atual da agenda. O front precisa distinguir os dois, porque num
 * caso ele conserta o formulario e no outro ele mostra os eventos que
 * batem e pergunta se e para marcar assim mesmo.
 *
 * Carrega a lista do que conflitou, e nao so uma mensagem: dizer "ja existe
 * evento nesse dia" sem dizer QUAL obrigaria o usuario a fechar o formulario
 * e ir procurar.
 */
class ConflitoAgendaException(
    message: String,
    val conflitos: List<ConflitoDTO>
) : RuntimeException(message)
