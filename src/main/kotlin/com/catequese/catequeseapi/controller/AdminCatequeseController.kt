package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.AplicarEncerramentoDTO
import com.catequese.catequeseapi.dto.ClassificacaoTurmaDTO
import com.catequese.catequeseapi.dto.MatriculaAdminDTO
import com.catequese.catequeseapi.dto.NovaMatriculaDTO
import com.catequese.catequeseapi.dto.PreviaAnoDTO
import com.catequese.catequeseapi.dto.ResultadoEncerramentoDTO
import com.catequese.catequeseapi.dto.SituacaoMatriculaDTO
import com.catequese.catequeseapi.dto.TransferenciaDTO
import com.catequese.catequeseapi.dto.TurmaAdminDTO
import com.catequese.catequeseapi.service.AdminCatequeseService
import com.catequese.catequeseapi.service.EncerramentoAnoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Area do coordenador paroquial: classificar turmas e cuidar de matriculas.
 *
 * Esta e a etapa que destrava as outras. Sem categoria na turma a frequencia
 * nao e apurada; sem matricula nao existe lista de chamada.
 *
 * O prefixo de rota inteiro e restrito ao administrador na SecurityConfig, e
 * o AdminCatequeseService confere de novo em cada metodo. A duplicidade e
 * proposital: uma protege a rota, a outra protege o metodo.
 */
@RestController
@RequestMapping("/api/admin")
class AdminCatequeseController(
    private val adminService: AdminCatequeseService,
    private val encerramentoService: EncerramentoAnoService
) {

    @GetMapping("/turmas")
    fun turmas(@RequestParam(required = false) ano: Int?): ResponseEntity<List<TurmaAdminDTO>> =
        ResponseEntity.ok(adminService.turmas(ano))

    /** Define a regra de frequencia da turma. Categoria nula volta a nao apurar. */
    @PutMapping("/turmas/{idTurma}/classificacao")
    fun classificar(
        @PathVariable idTurma: Long,
        @RequestBody body: ClassificacaoTurmaDTO
    ): ResponseEntity<TurmaAdminDTO> =
        ResponseEntity.ok(adminService.classificar(idTurma, body))

    @GetMapping("/turmas/{idTurma}/matriculas")
    fun matriculas(
        @PathVariable idTurma: Long,
        @RequestParam(required = false) ano: Int?
    ): ResponseEntity<List<MatriculaAdminDTO>> =
        ResponseEntity.ok(adminService.matriculasDaTurma(idTurma, ano))

    @PostMapping("/matriculas")
    fun matricular(@RequestBody body: NovaMatriculaDTO): ResponseEntity<MatriculaAdminDTO> =
        ResponseEntity.status(HttpStatus.CREATED).body(adminService.matricular(body))

    @PutMapping("/matriculas/{idMatricula}/situacao")
    fun situacao(
        @PathVariable idMatricula: Long,
        @RequestBody body: SituacaoMatriculaDTO
    ): ResponseEntity<MatriculaAdminDTO> =
        ResponseEntity.ok(adminService.alterarSituacao(idMatricula, body))

    // ---- Encerramento de ano ----------------------------------------------

    /**
     * Previa: o que aconteceria se o ano fosse encerrado agora.
     * Nao altera nada. Encerrar o ano decide quem concluiu a catequese, e
     * ninguem deveria descobrir o resultado depois de aplicado.
     */
    @GetMapping("/encerramento/previa")
    fun previaEncerramento(
        @RequestParam(required = false) ano: Int?
    ): ResponseEntity<PreviaAnoDTO> =
        ResponseEntity.ok(encerramentoService.previa(ano))

    /** Aplica somente as matriculas escolhidas na previa. */
    @PostMapping("/encerramento/aplicar")
    fun aplicarEncerramento(
        @RequestBody body: AplicarEncerramentoDTO
    ): ResponseEntity<ResultadoEncerramentoDTO> =
        ResponseEntity.ok(encerramentoService.aplicar(body))

    /** Devolve as duas matriculas: a encerrada na origem e a nova no destino. */
    @PostMapping("/matriculas/{idMatricula}/transferir")
    fun transferir(
        @PathVariable idMatricula: Long,
        @RequestBody body: TransferenciaDTO
    ): ResponseEntity<List<MatriculaAdminDTO>> =
        ResponseEntity.ok(adminService.transferir(idMatricula, body))
}
