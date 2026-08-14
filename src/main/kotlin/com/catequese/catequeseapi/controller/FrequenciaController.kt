package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.FrequenciaCatequisandoDTO
import com.catequese.catequeseapi.dto.FrequenciaTurmaDTO
import com.catequese.catequeseapi.service.FrequenciaService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Consulta de frequencia.
 *
 * Somente leitura -- nao ha nada para alterar aqui: a frequencia e consequencia
 * da chamada, e a chamada tem endpoint proprio. Quem recorta os dados por
 * turma e por comunidade e o FrequenciaService.
 *
 * Como sao todos GET, a SecurityConfig ja libera para qualquer usuario logado,
 * inclusive o catequista.
 */
@RestController
@RequestMapping("/api/frequencia")
class FrequenciaController(private val frequenciaService: FrequenciaService) {

    /** A tela da turma: um por linha, com a situacao de cada um. */
    @GetMapping("/turma/{idTurma}")
    fun daTurma(
        @PathVariable idTurma: Long,
        @RequestParam(required = false) ano: Int?,
        @RequestParam(required = false, defaultValue = "false") incluirInativos: Boolean
    ): ResponseEntity<FrequenciaTurmaDTO> =
        ResponseEntity.ok(frequenciaService.daTurma(idTurma, ano, incluirInativos))

    /** A frequencia da pessoa no ano; mais de uma linha se houve transferencia. */
    @GetMapping("/catequisando/{idCatequisando}")
    fun doCatequisando(
        @PathVariable idCatequisando: Long,
        @RequestParam(required = false) ano: Int?
    ): ResponseEntity<List<FrequenciaCatequisandoDTO>> =
        ResponseEntity.ok(frequenciaService.doCatequisando(idCatequisando, ano))

    /** Percurso completo, para a ficha do catequisando. */
    @GetMapping("/catequisando/{idCatequisando}/historico")
    fun historico(
        @PathVariable idCatequisando: Long
    ): ResponseEntity<List<FrequenciaCatequisandoDTO>> =
        ResponseEntity.ok(frequenciaService.historico(idCatequisando))
}
