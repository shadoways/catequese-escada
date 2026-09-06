package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.service.ConfiguracaoService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Corpo do PUT /api/config/cadastro. */
data class CadastroConfigDTO(val aberto: Boolean = true)

/** Corpo (e resposta) de GET/PUT /api/config/formacao. */
data class FormacaoConfigDTO(
    val minimoAgregado: Int,
    val fechamentoMes: Int,
    val alertaMesesAntes: Int
)

@RestController
@RequestMapping("/api/config")
class ConfiguracaoController(private val service: ConfiguracaoService) {

    /**
     * Publico de proposito: a tela de cadastro precisa saber se deve se mostrar
     * antes de qualquer login.
     */
    @GetMapping("/cadastro")
    fun consultar(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("cadastroAberto" to service.cadastroAberto()))

    /** Restrito ao COORDENADOR_PAROQUIAL pela regra em SecurityConfig. */
    @PutMapping("/cadastro")
    fun definir(@RequestBody body: CadastroConfigDTO): ResponseEntity<Map<String, Any>> {
        val quem = SecurityContextHolder.getContext().authentication?.name
        val aberto = service.definirCadastroAberto(body.aberto, quem)
        return ResponseEntity.ok(mapOf("cadastroAberto" to aberto))
    }

    /**
     * So leitura -- qualquer logado, inclusive coordenador de comunidade, ve
     * o prazo e o minimo (Consultar Catequistas usa isso para colorir). Quem
     * ALTERA e so o coordenador paroquial, via o PUT abaixo (regra 6 da
     * especificacao: coordenador de comunidade so visualiza).
     */
    @GetMapping("/formacao")
    fun consultarFormacao(): ResponseEntity<FormacaoConfigDTO> = ResponseEntity.ok(
        FormacaoConfigDTO(
            minimoAgregado = service.minimoAgregadoFormacao(),
            fechamentoMes = service.fechamentoMesFormacao(),
            alertaMesesAntes = service.alertaMesesAntesFormacao()
        )
    )

    // Restrito ao COORDENADOR_PAROQUIAL pela regra em SecurityConfig (PUT /api/config/ + curinga).
    @PutMapping("/formacao")
    fun definirFormacao(@RequestBody body: FormacaoConfigDTO): ResponseEntity<FormacaoConfigDTO> {
        val quem = SecurityContextHolder.getContext().authentication?.name
        service.definirConfigFormacao(body.minimoAgregado, body.fechamentoMes, body.alertaMesesAntes, quem)
        return consultarFormacao()
    }
}
