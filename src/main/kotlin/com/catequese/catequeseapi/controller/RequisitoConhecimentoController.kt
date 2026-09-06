package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.RequisitoConhecimentoAtualizarDTO
import com.catequese.catequeseapi.dto.RequisitoConhecimentoCriarDTO
import com.catequese.catequeseapi.dto.RequisitoConhecimentoDTO
import com.catequese.catequeseapi.service.RequisitoConhecimentoService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Catálogo de conhecimentos exigidos do catequista -- tela-catequistas.md,
 * aba Conhecimentos. Rota própria, e não `/api/conhecimentos`: aquela já é
 * de `ConhecimentoCatequistaController`, uma entidade antiga e sem relação
 * com este catálogo (ver a KDoc de `RequisitoConhecimento`) -- reusar o
 * caminho colidiria os dois `@RequestMapping` no mesmo prefixo.
 *
 * Leitura: qualquer logado (regra geral de GET em SecurityConfig) -- a tela
 * do catequista precisa da lista para desenhar o checklist. Escrita: só
 * COORDENADOR_PAROQUIAL, restrito em SecurityConfig (mesmo padrão já usado
 * para alterar a configuração de formação), não checado de novo aqui.
 */
@RestController
@RequestMapping("/api/conhecimentos-exigidos")
class RequisitoConhecimentoController(private val service: RequisitoConhecimentoService) {

    @GetMapping
    fun listar(): List<RequisitoConhecimentoDTO> = service.listarTodos()

    @PostMapping
    fun criar(@RequestBody body: RequisitoConhecimentoCriarDTO): RequisitoConhecimentoDTO {
        val quem = SecurityContextHolder.getContext().authentication?.name
        return service.criar(body.nome, quem)
    }

    @PutMapping("/{id}")
    fun atualizar(
        @PathVariable id: Long,
        @RequestBody body: RequisitoConhecimentoAtualizarDTO
    ): RequisitoConhecimentoDTO {
        val quem = SecurityContextHolder.getContext().authentication?.name
        return service.atualizar(id, body.nome, body.ativo, quem)
    }
}
