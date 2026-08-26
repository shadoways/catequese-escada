package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.AgendaDTO
import com.catequese.catequeseapi.dto.ChecagemConflitoDTO
import com.catequese.catequeseapi.dto.EventoAgendaDTO
import com.catequese.catequeseapi.dto.EventoFormDTO
import com.catequese.catequeseapi.dto.OpcoesAgendaDTO
import com.catequese.catequeseapi.service.AgendaService
import org.springframework.format.annotation.DateTimeFormat
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
import java.time.LocalDate

/**
 * A agenda da catequese.
 *
 * Separada de /api/eventos, que continua existindo como CRUD cru para o que ja
 * consumia aquela rota. Aqui tudo passa pela permissao por nivel.
 */
@RestController
@RequestMapping("/api/agenda")
class AgendaController(private val service: AgendaService) {

    @GetMapping
    fun agenda(@RequestParam(required = false) ano: Int?): ResponseEntity<AgendaDTO> =
        ResponseEntity.ok(service.agendaDoAno(ano))

    /** Opcoes do formulario, ja filtradas pelo que o usuario pode criar. */
    @GetMapping("/opcoes")
    fun opcoes(): ResponseEntity<OpcoesAgendaDTO> = ResponseEntity.ok(service.opcoes())

    @GetMapping("/eventos/{id}")
    fun porId(@PathVariable id: Long): ResponseEntity<EventoAgendaDTO> =
        ResponseEntity.ok(service.porId(id))

    /**
     * Ja existe evento disputando este publico nesta data?
     *
     * Consultada enquanto a pessoa preenche o formulario, para o aviso
     * aparecer antes do Salvar -- descobrir o conflito so depois de tentar
     * gravar e o que torna a regra irritante.
     */
    @GetMapping("/conflitos")
    fun conflitos(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) data: LocalDate,
        @RequestParam nivel: String,
        @RequestParam(required = false) idComunidade: Long?,
        @RequestParam(required = false) idTurma: Long?,
        @RequestParam(required = false) ignorarId: Long?
    ): ResponseEntity<ChecagemConflitoDTO> = ResponseEntity.ok(
        service.checarConflito(data, nivel, idComunidade, idTurma, ignorarId)
    )

    @PostMapping("/eventos")
    fun criar(@RequestBody form: EventoFormDTO): ResponseEntity<EventoAgendaDTO> {
        val salvo = service.criar(form)
        return ResponseEntity.created(URI("/api/agenda/eventos/${salvo.idEvento}")).body(salvo)
    }

    @PutMapping("/eventos/{id}")
    fun atualizar(
        @PathVariable id: Long,
        @RequestBody form: EventoFormDTO
    ): ResponseEntity<EventoAgendaDTO> = ResponseEntity.ok(service.atualizar(id, form))

    @DeleteMapping("/eventos/{id}")
    fun excluir(@PathVariable id: Long): ResponseEntity<Void> {
        service.excluir(id)
        return ResponseEntity.noContent().build()
    }
}
