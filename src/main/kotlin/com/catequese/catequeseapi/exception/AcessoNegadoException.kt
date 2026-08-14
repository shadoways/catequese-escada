package com.catequese.catequeseapi.exception

/**
 * O usuario esta logado, mas aquele dado nao e dele.
 *
 * Separada de IllegalArgumentException para virar 403 e nao 400: o front
 * precisa distinguir "voce errou o pedido" de "isso nao e seu". Tratada no
 * RestExceptionHandler.
 */
class AcessoNegadoException(message: String) : RuntimeException(message)
