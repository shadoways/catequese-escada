package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.ChecklistConhecimentoDTO
import com.catequese.catequeseapi.dto.CurriculoCatequistaDTO
import com.catequese.catequeseapi.dto.CurriculoHistoricoEncontroDTO
import com.catequese.catequeseapi.dto.CurriculoResumoDTO
import com.catequese.catequeseapi.dto.MarcarConhecimentoDTO
import com.catequese.catequeseapi.service.CurriculoCatequistaService
import com.catequese.catequeseapi.service.RequisitoConhecimentoService
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Consultar Catequistas -- o currículo de formação, separado de propósito do
 * CRUD antigo em CatequistaController (que não tem nenhum recorte de
 * EscopoAcessoService -- ver tela-catequistas.md, regra 1). Autenticado já
 * basta na rota (regra geral de GET em SecurityConfig); quem vê o quê é
 * decidido aqui dentro, por EscopoAcessoService, não pelo caminho da URL.
 *
 * As rotas de conhecimentos (`/conhecimentos`) moram aqui, e não num
 * controller próprio: são sempre "de UM catequista", igual à rota de
 * currículo -- o catálogo em si (criar/inativar um conhecimento exigido)
 * é que fica em `RequisitoConhecimentoController`.
 */
@RestController
@RequestMapping("/api/catequistas")
class CurriculoCatequistaController(
    private val service: CurriculoCatequistaService,
    private val conhecimentos: RequisitoConhecimentoService
) {

    @GetMapping("/curriculo")
    fun listar(@RequestParam(required = false) ano: Int?): List<CurriculoResumoDTO> = service.listar(ano)

    @GetMapping("/{id}/curriculo")
    fun detalhe(
        @PathVariable id: Long,
        @RequestParam(required = false) ano: Int?
    ): CurriculoCatequistaDTO = service.detalhe(id, ano)

    /** Histórico completo (todos os anos) para a aba "Formações" e seus filtros. */
    @GetMapping("/{id}/formacoes")
    fun formacoes(@PathVariable id: Long): List<CurriculoHistoricoEncontroDTO> = service.historico(id)

    @GetMapping("/{id}/conhecimentos")
    fun checklistConhecimentos(@PathVariable id: Long): ChecklistConhecimentoDTO =
        conhecimentos.checklistDoCatequista(id)

    // Restrito ao COORDENADOR_PAROQUIAL pela regra em SecurityConfig
    // (PUT /api/catequistas/*/conhecimentos/**) -- mesmo padrão de
    // PUT /api/config/**, não checado de novo aqui.
    @PutMapping("/{id}/conhecimentos/{idRequisito}")
    fun marcarConhecimento(
        @PathVariable id: Long,
        @PathVariable idRequisito: Long,
        @RequestBody body: MarcarConhecimentoDTO
    ) {
        val quem = SecurityContextHolder.getContext().authentication?.name
        conhecimentos.marcar(id, idRequisito, body.possui, quem)
    }
}
