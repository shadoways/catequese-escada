package com.catequese.catequeseapi.controller

import com.catequese.catequeseapi.dto.IndicadoresEventosDTO
import com.catequese.catequeseapi.dto.IndicadoresFormacaoDTO
import com.catequese.catequeseapi.dto.IndicadoresFrequenciaDTO
import com.catequese.catequeseapi.dto.IndicadoresDTO
import com.catequese.catequeseapi.dto.IndicadoresMatriculasDTO
import com.catequese.catequeseapi.dto.OpcoesIndicadoresCompletasDTO
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoMatricula
import com.catequese.catequeseapi.model.TipoEvento
import com.catequese.catequeseapi.service.IndicadoresDetalheService
import com.catequese.catequeseapi.service.IndicadoresService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * O relatorio da catequese. Exclusivo do coordenador paroquial -- a regra esta
 * no SecurityConfig, no matcher de `/api/indicadores`, e de novo nos servicos.
 *
 * (Sem o curinga escrito aqui de proposito: comentario de bloco em Kotlin
 * ANINHA, entao a sequencia barra-asterisco-asterisco dentro de um KDoc abre
 * um comentario novo e o arquivo inteiro deixa de fechar. Ja aconteceu.)
 *
 * Uma rota por TELA, e nao uma rota por bloco nem uma rota so para tudo.
 *
 * Uma rota so para tudo obrigaria a apurar frequencia de turma, ranking de
 * catequista e lista de evento a cada troca de filtro, mesmo com a pessoa
 * olhando outra coisa. Uma rota por bloco faria os numeros de uma mesma tela
 * chegarem em ordem aleatoria e -- pior -- falarem de instantes diferentes do
 * banco. A tela e a unidade certa: e o recorte que a pessoa esta lendo junto.
 */
@RestController
@RequestMapping("/api/indicadores")
class IndicadoresController(
    private val service: IndicadoresService,
    private val detalhe: IndicadoresDetalheService
) {

    /** Tudo que as barras de filtro das cinco telas precisam, numa chamada so. */
    @GetMapping("/opcoes")
    fun opcoes(): ResponseEntity<OpcoesIndicadoresCompletasDTO> =
        ResponseEntity.ok(detalhe.opcoes())

    /** Resumo geral: a pergunta "como o ano esta indo". */
    @GetMapping
    fun relatorio(
        @RequestParam(required = false) ano: Int?,
        @RequestParam(required = false) idComunidade: Long?
    ): ResponseEntity<IndicadoresDTO> =
        ResponseEntity.ok(service.relatorio(ano, idComunidade))

    @GetMapping("/matriculas")
    fun matriculas(
        @RequestParam(required = false) ano: Int?,
        @RequestParam(required = false) idComunidade: Long?,
        @RequestParam(required = false) idTurma: Long?,
        @RequestParam(required = false) situacao: SituacaoMatricula?
    ): ResponseEntity<IndicadoresMatriculasDTO> =
        ResponseEntity.ok(detalhe.matriculas(ano, idComunidade, idTurma, situacao))

    @GetMapping("/frequencia")
    fun frequencia(
        @RequestParam(required = false) ano: Int?,
        @RequestParam(required = false) idComunidade: Long?,
        @RequestParam(required = false) idTurma: Long?
    ): ResponseEntity<IndicadoresFrequenciaDTO> =
        ResponseEntity.ok(detalhe.frequencia(ano, idComunidade, idTurma))

    @GetMapping("/formacao")
    fun formacao(
        @RequestParam(required = false) ano: Int?,
        @RequestParam(required = false) nivel: NivelEvento?,
        @RequestParam(required = false) idComunidade: Long?,
        @RequestParam(required = false) idCatequista: Long?
    ): ResponseEntity<IndicadoresFormacaoDTO> =
        ResponseEntity.ok(detalhe.formacao(ano, nivel, idComunidade, idCatequista))

    @GetMapping("/eventos")
    fun eventos(
        @RequestParam(required = false) ano: Int?,
        @RequestParam(required = false) tipo: TipoEvento?,
        @RequestParam(required = false) nivel: NivelEvento?,
        @RequestParam(required = false) idComunidade: Long?
    ): ResponseEntity<IndicadoresEventosDTO> =
        ResponseEntity.ok(detalhe.eventos(ano, tipo, nivel, idComunidade))
}
