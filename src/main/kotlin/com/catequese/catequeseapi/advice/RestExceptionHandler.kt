package com.catequese.catequeseapi.advice

import com.catequese.catequeseapi.exception.AcessoNegadoException
import com.catequese.catequeseapi.exception.ConflitoAgendaException
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleNotFound(ex: ResourceNotFoundException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("erro" to (ex.message ?: "Recurso não encontrado")))
    }

    /**
     * Logado, porem o dado nao e dele. 403 e nao 400: o front trata os dois
     * de formas diferentes.
     */
    @ExceptionHandler(AcessoNegadoException::class)
    fun handleAcessoNegado(ex: AcessoNegadoException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(mapOf("erro" to (ex.message ?: "Sem permissão para esta consulta")))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("erro" to (ex.message ?: "Requisição inválida")))
    }

    /**
     * Ja existe evento no mesmo dia atingindo as mesmas pessoas. 409 e nao
     * 400: o formulario esta certo, quem esta ocupado e a data. Devolve os
     * eventos que bateram para a tela poder mostra-los e oferecer o
     * "marcar assim mesmo".
     */
    @ExceptionHandler(ConflitoAgendaException::class)
    fun handleConflito(ex: ConflitoAgendaException): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            mapOf(
                "erro" to (ex.message ?: "Já existe evento marcado para este dia."),
                "conflitos" to ex.conflitos
            )
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, Any>> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalido") }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("erro" to "Validação falhou", "detalhes" to errors))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<Map<String, String>> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("erro" to (ex.message ?: "Erro interno")))
    }
}
