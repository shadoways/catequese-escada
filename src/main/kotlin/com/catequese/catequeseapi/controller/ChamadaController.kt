package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.AbrirEncontroDTO
import com.catequese.catequeseapi.dto.ChamadaDTO
import com.catequese.catequeseapi.dto.CorrecaoChamadaDTO
import com.catequese.catequeseapi.dto.EncontroDTO
import com.catequese.catequeseapi.dto.FinalizarEncontroDTO
import com.catequese.catequeseapi.dto.MarcarLoteDTO
import com.catequese.catequeseapi.dto.TurmaChamadaDTO
import com.catequese.catequeseapi.service.ChamadaService
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
 * Chamada dos encontros.
 *
 * Marcar presenca e a unica escrita liberada ao catequista, por isso estas
 * rotas ficam fora da regra geral de escrita da SecurityConfig, que exige
 * coordenador. Quem limita o catequista as turmas dele e o ChamadaService.
 */
@RestController
@RequestMapping("/api/chamada")
class ChamadaController(private val chamadaService: ChamadaService) {

    /** Tela inicial do catequista: as turmas dele e em que pe esta cada uma. */
    @GetMapping("/minhas-turmas")
    fun minhasTurmas(
        @RequestParam(required = false) ano: Int?
    ): ResponseEntity<List<TurmaChamadaDTO>> =
        ResponseEntity.ok(chamadaService.minhasTurmas(ano))

    @GetMapping("/turma/{idTurma}/encontros")
    fun encontros(@PathVariable idTurma: Long): ResponseEntity<List<EncontroDTO>> =
        ResponseEntity.ok(chamadaService.encontrosDaTurma(idTurma))

    @GetMapping("/encontro/{idEncontro}")
    fun chamada(@PathVariable idEncontro: Long): ResponseEntity<ChamadaDTO> =
        ResponseEntity.ok(chamadaService.chamada(idEncontro))

    @PostMapping("/abrir")
    fun abrir(@RequestBody body: AbrirEncontroDTO): ResponseEntity<EncontroDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(chamadaService.abrir(body, quem()))

    @PostMapping("/encontro/{idEncontro}/marcar")
    fun marcar(
        @PathVariable idEncontro: Long,
        @RequestBody body: MarcarLoteDTO
    ): ResponseEntity<ChamadaDTO> =
        ResponseEntity.ok(chamadaService.marcar(idEncontro, body, quem()))

    @PostMapping("/encontro/{idEncontro}/fechar")
    fun fechar(
        @PathVariable idEncontro: Long,
        @RequestBody body: FinalizarEncontroDTO
    ): ResponseEntity<EncontroDTO> =
        ResponseEntity.ok(chamadaService.fechar(idEncontro, body, quem()))

    @PostMapping("/encontro/{idEncontro}/cancelar")
    fun cancelar(
        @PathVariable idEncontro: Long,
        @RequestBody body: FinalizarEncontroDTO
    ): ResponseEntity<EncontroDTO> =
        ResponseEntity.ok(chamadaService.cancelar(idEncontro, body, quem()))

    /**
     * Corrige uma chamada JA ENCERRADA, numa transacao so, exigindo motivo.
     * Somente coordenador paroquial; a checagem esta no servico.
     */
    @PostMapping("/encontro/{idEncontro}/corrigir")
    fun corrigir(
        @PathVariable idEncontro: Long,
        @RequestBody body: CorrecaoChamadaDTO
    ): ResponseEntity<ChamadaDTO> =
        ResponseEntity.ok(chamadaService.corrigir(idEncontro, body, quem()))

    /** Somente coordenador paroquial; a checagem esta no servico. */
    @PostMapping("/encontro/{idEncontro}/reabrir")
    fun reabrir(@PathVariable idEncontro: Long): ResponseEntity<EncontroDTO> =
        ResponseEntity.ok(chamadaService.reabrir(idEncontro, quem()))

    /**
     * Dispara o fechamento dos encontros esquecidos sem esperar a rotina
     * noturna. So administrador -- a checagem esta em fecharEsquecidosPeloAdmin.
     */
    @PostMapping("/fechar-esquecidos")
    fun fecharEsquecidos(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(
            mapOf("encontrosEncerrados" to chamadaService.fecharEsquecidosPeloAdmin())
        )

    private fun quem(): String? = SecurityContextHolder.getContext().authentication?.name
}
