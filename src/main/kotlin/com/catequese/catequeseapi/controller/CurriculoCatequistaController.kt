package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.CurriculoCatequistaDTO
import com.catequese.catequeseapi.dto.CurriculoResumoDTO
import com.catequese.catequeseapi.service.CurriculoCatequistaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Consultar Catequistas -- o currículo de formação, separado de propósito do
 * CRUD antigo em CatequistaController (que não tem nenhum recorte de
 * EscopoAcessoService -- ver tela-catequistas.md, regra 1). Autenticado já
 * basta na rota (regra geral de GET em SecurityConfig); quem vê o quê é
 * decidido aqui dentro, por EscopoAcessoService, não pelo caminho da URL.
 */
@RestController
@RequestMapping("/api/catequistas")
class CurriculoCatequistaController(private val service: CurriculoCatequistaService) {

    @GetMapping("/curriculo")
    fun listar(@RequestParam(required = false) ano: Int?): List<CurriculoResumoDTO> = service.listar(ano)

    @GetMapping("/{id}/curriculo")
    fun detalhe(
        @PathVariable id: Long,
        @RequestParam(required = false) ano: Int?
    ): CurriculoCatequistaDTO = service.detalhe(id, ano)
}
