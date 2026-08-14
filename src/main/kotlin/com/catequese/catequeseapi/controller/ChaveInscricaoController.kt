package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.ChaveInscricaoDTO
import com.catequese.catequeseapi.dto.CriarChaveDTO
import com.catequese.catequeseapi.dto.ValidacaoChaveDTO
import com.catequese.catequeseapi.service.ChaveInscricaoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Chaves que liberam o cadastro publico.
 *
 * Tudo aqui e restrito ao coordenador paroquial pela SecurityConfig, com uma
 * excecao proposital: /validar e publico, porque a tela de inscricao precisa
 * conferir o codigo antes de mostrar o formulario.
 */
@RestController
@RequestMapping("/api/chaves")
class ChaveInscricaoController(private val service: ChaveInscricaoService) {

    @GetMapping
    fun listar(): ResponseEntity<List<ChaveInscricaoDTO>> = ResponseEntity.ok(service.listar())

    @PostMapping
    fun criar(@RequestBody body: CriarChaveDTO): ResponseEntity<ChaveInscricaoDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.criar(body, quem()))

    @PostMapping("/{id}/revogar")
    fun revogar(@PathVariable id: Long): ResponseEntity<ChaveInscricaoDTO> =
        ResponseEntity.ok(service.revogar(id, quem()))

    /**
     * Publico. Diz apenas se o codigo serve e para que periodo, nunca a lista
     * de chaves nem quantos usos restam.
     */
    @GetMapping("/validar")
    fun validar(@RequestParam codigo: String): ResponseEntity<ValidacaoChaveDTO> {
        val chave = service.conferir(codigo)
            ?: return ResponseEntity.ok(
                ValidacaoChaveDTO(valida = false, motivo = "Chave de inscricao nao encontrada.")
            )

        val recusa = chave.motivoRecusa()
        return ResponseEntity.ok(
            if (recusa == null) {
                ValidacaoChaveDTO(valida = true, descricao = chave.descricao)
            } else {
                ValidacaoChaveDTO(valida = false, motivo = recusa)
            }
        )
    }

    private fun quem(): String? = SecurityContextHolder.getContext().authentication?.name
}
