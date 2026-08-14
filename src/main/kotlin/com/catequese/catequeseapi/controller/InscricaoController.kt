package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.InscricaoRequestDTO
import com.catequese.catequeseapi.dto.InscricaoRespostaDTO
import com.catequese.catequeseapi.service.InscricaoService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Envio da inscricao publica, em uma unica chamada e uma unica transacao.
 *
 * Substitui a sequencia antiga de POSTs separados para catequisando, ficha e
 * documentos, que podia deixar cadastro pela metade quando falhava no meio.
 */
@RestController
@RequestMapping("/api/inscricoes")
class InscricaoController(private val inscricaoService: InscricaoService) {

    @PostMapping
    fun registrar(
        @RequestBody body: InscricaoRequestDTO,
        @RequestHeader(name = CABECALHO_CHAVE, required = false) codigoChave: String?
    ): ResponseEntity<InscricaoRespostaDTO> {
        // Coordenador e coordenador paroquial cadastram pelo sistema, sem chave:
        // a chave existe para conter o formulario publico, nao para atrapalhar
        // quem ja esta autenticado.
        val resposta = inscricaoService.registrar(
            dto = body,
            codigoChave = codigoChave,
            exigirChave = !podeCadastrarSemChave()
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta)
    }

    private fun podeCadastrarSemChave(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.any { it.authority in PAPEIS_QUE_CADASTRAM }
    }

    companion object {
        const val CABECALHO_CHAVE = "X-Chave-Inscricao"
        private val PAPEIS_QUE_CADASTRAM =
            setOf("ROLE_COORDENADOR", "ROLE_COORDENADOR_PAROQUIAL")
    }
}
