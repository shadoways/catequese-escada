package com.catequese.catequeseapi.config

import com.catequese.catequeseapi.service.ChamadaService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Encerra sozinho os encontros que o catequista esqueceu de enviar.
 *
 * O encontro do dia D fica aberto durante D e D+1, dando margem para corrigir
 * um esquecimento no mesmo dia ou no seguinte. Na virada para D+2 esta rotina
 * encerra: sem isso, listas antigas ficariam abertas para sempre e a frequencia
 * nunca fecharia.
 *
 * Roda de madrugada, quando ninguem esta marcando presenca.
 */
@Component
class RotinaFechamentoEncontros(private val chamadaService: ChamadaService) {

    private val log = LoggerFactory.getLogger(RotinaFechamentoEncontros::class.java)

    @Scheduled(cron = "0 10 3 * * *")
    fun fecharEsquecidos() {
        try {
            val total = chamadaService.fecharEsquecidos()
            if (total > 0) {
                log.info("Rotina noturna encerrou {} encontro(s) esquecidos.", total)
            }
        } catch (ex: Exception) {
            // Uma falha aqui nao pode derrubar a aplicacao: no dia seguinte a
            // rotina tenta de novo, e o admin ainda pode fechar pela tela.
            log.error("Falha no fechamento automatico de encontros: {}", ex.message, ex)
        }
    }
}
