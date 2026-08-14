package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.FichaCatequisandoDTO
import com.catequese.catequeseapi.service.FichaCatequisandoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Ficha do catequisando para a area do catequista.
 *
 * Rota separada de /api/fichas de proposito: aquela devolve a inscricao
 * inteira, com os anexos; esta devolve apenas o STATUS de entrega de cada
 * documento. Sao publicos diferentes com necessidades diferentes.
 *
 * Somente leitura. GET /api/** ja exige usuario logado na SecurityConfig; o
 * recorte por turma e por comunidade fica no FichaCatequisandoService.
 */
@RestController
@RequestMapping("/api/ficha-catequisando")
class FichaCatequisandoController(
    private val fichaCatequisandoService: FichaCatequisandoService
) {

    @GetMapping("/{idCatequisando}")
    fun ficha(@PathVariable idCatequisando: Long): ResponseEntity<FichaCatequisandoDTO> =
        ResponseEntity.ok(fichaCatequisandoService.ficha(idCatequisando))
}
