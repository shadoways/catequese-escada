package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.ChamadaFormacaoDTO
import com.catequese.catequeseapi.dto.FormacaoDetalheDTO
import com.catequese.catequeseapi.dto.FormacaoFormDTO
import com.catequese.catequeseapi.dto.FormacaoResumoDTO
import com.catequese.catequeseapi.model.PresencaFormacao
import com.catequese.catequeseapi.service.FormacaoService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/formacoes")
class FormacaoController(private val service: FormacaoService) {

    @GetMapping
    fun listar(@RequestParam(required = false) ano: Int?): ResponseEntity<List<FormacaoResumoDTO>> =
        ResponseEntity.ok(service.listar(ano))

    @GetMapping("/{id}")
    fun detalhe(@PathVariable id: Long): ResponseEntity<FormacaoDetalheDTO> =
        ResponseEntity.ok(service.detalhe(id))

    @PostMapping
    fun criar(@RequestBody form: FormacaoFormDTO): ResponseEntity<FormacaoResumoDTO> {
        val salva = service.criar(form)
        return ResponseEntity.created(URI("/api/formacoes/${salva.idFormacao}")).body(salva)
    }

    @PutMapping("/{id}")
    fun atualizar(
        @PathVariable id: Long,
        @RequestBody form: FormacaoFormDTO
    ): ResponseEntity<FormacaoResumoDTO> = ResponseEntity.ok(service.atualizar(id, form))

    @DeleteMapping("/{id}")
    fun excluir(@PathVariable id: Long): ResponseEntity<Void> {
        service.excluir(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/inscritos/{idCatequista}")
    fun inscrever(
        @PathVariable id: Long,
        @PathVariable idCatequista: Long
    ): ResponseEntity<Void> {
        service.inscrever(id, idCatequista)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/inscritos/{idCatequista}")
    fun desinscrever(
        @PathVariable id: Long,
        @PathVariable idCatequista: Long
    ): ResponseEntity<Void> {
        service.desinscrever(id, idCatequista)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/encontros/{idEvento}/chamada")
    fun marcacoes(@PathVariable idEvento: Long): ResponseEntity<List<PresencaFormacao>> =
        ResponseEntity.ok(service.marcacoesDoEncontro(idEvento))

    @PostMapping("/encontros/{idEvento}/chamada")
    fun registrarChamada(
        @PathVariable idEvento: Long,
        @RequestBody chamada: ChamadaFormacaoDTO
    ): ResponseEntity<Void> {
        service.registrarChamada(idEvento, chamada)
        return ResponseEntity.noContent().build()
    }
}
