package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.IndicadoresDTO
import com.catequese.catequeseapi.dto.OpcoesIndicadoresDTO
import com.catequese.catequeseapi.service.IndicadoresService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * O relatorio da catequese. Exclusivo do coordenador paroquial -- a regra esta
 * no SecurityConfig, no matcher de `/api/indicadores`, e de novo no servico.
 *
 * (Sem o curinga escrito aqui de proposito: comentario de bloco em Kotlin
 * ANINHA, entao a sequencia barra-asterisco-asterisco dentro de um KDoc abre
 * um comentario novo e o arquivo inteiro deixa de fechar.)
 *
 * Uma rota so devolve o relatorio inteiro, de proposito. Um endpoint por bloco
 * faria os numeros chegarem em ordem aleatoria, cada um com o seu "carregando",
 * e -- pior -- cada bloco falaria de um instante diferente do banco. Num
 * relatorio que vai impresso, numeros de instantes diferentes sao defeito.
 */
@RestController
@RequestMapping("/api/indicadores")
class IndicadoresController(private val service: IndicadoresService) {

    /** Os dois controles da barra: anos com dado e comunidades. */
    @GetMapping("/opcoes")
    fun opcoes(): ResponseEntity<OpcoesIndicadoresDTO> = ResponseEntity.ok(service.opcoes())

    @GetMapping
    fun relatorio(
        @RequestParam(required = false) ano: Int?,
        @RequestParam(required = false) idComunidade: Long?
    ): ResponseEntity<IndicadoresDTO> =
        ResponseEntity.ok(service.relatorio(ano, idComunidade))
}
