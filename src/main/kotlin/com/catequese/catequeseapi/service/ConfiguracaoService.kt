package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.Configuracao
import com.catequese.catequeseapi.repository.ConfiguracaoRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class ConfiguracaoService(private val repo: ConfiguracaoRepository) {

    private val log = LoggerFactory.getLogger(ConfiguracaoService::class.java)

    /**
     * Se a linha ainda nao existe, o cadastro esta ABERTO.
     * O padrao repete o comportamento que o sistema sempre teve, entao subir
     * esta versao sem inserir nada no banco nao fecha as inscricoes sem aviso.
     */
    fun cadastroAberto(): Boolean = repo.findById(Configuracao.CADASTRO_ABERTO)
        .map { it.valor.equals("true", ignoreCase = true) }
        .orElse(true)

    /**
     * A partir de que percentual o sistema avisa que a frequencia esta perto do
     * limite. Valor invalido no banco nao pode derrubar a consulta de
     * frequencia, entao cai no padrao em silencio -- so registra no log.
     */
    fun percentualAlerta(): Int {
        val bruto = repo.findById(Configuracao.FREQUENCIA_AVISO)
            .map { it.valor.trim() }
            .orElse(null) ?: return Configuracao.FREQUENCIA_AVISO_PADRAO

        val valor = bruto.toIntOrNull()
        if (valor == null || valor !in 0..100) {
            log.warn(
                "Configuracao '{}' com valor invalido ('{}'). Usando o padrao de {}%.",
                Configuracao.FREQUENCIA_AVISO, bruto, Configuracao.FREQUENCIA_AVISO_PADRAO
            )
            return Configuracao.FREQUENCIA_AVISO_PADRAO
        }
        return valor
    }

    @Transactional
    fun definirPercentualAlerta(percentual: Int, quem: String?): Int {
        require(percentual in 0..100) { "O percentual de aviso deve ficar entre 0 e 100." }
        repo.save(
            Configuracao(
                chave = Configuracao.FREQUENCIA_AVISO,
                valor = percentual.toString(),
                descricao = "Percentual a partir do qual a frequencia recebe aviso preventivo.",
                atualizadoEm = LocalDateTime.now().withNano(0),
                atualizadoPor = quem
            )
        )
        log.info("Percentual de aviso de frequencia definido em {}% por '{}'", percentual, quem ?: "?")
        return percentual
    }

    @Transactional
    fun definirCadastroAberto(aberto: Boolean, quem: String?): Boolean {
        repo.save(
            Configuracao(
                chave = Configuracao.CADASTRO_ABERTO,
                valor = aberto.toString(),
                descricao = "Quando false, a tela publica de cadastro fica indisponivel.",
                atualizadoEm = LocalDateTime.now(),
                atualizadoPor = quem
            )
        )
        log.info("Cadastro publico {} por '{}'", if (aberto) "ABERTO" else "FECHADO", quem ?: "?")
        return aberto
    }

    // ---- Consultar Catequistas: minimo agregado e prazo do ano de formacao ----

    private fun lerInt(chave: String, padrao: Int, min: Int, max: Int): Int {
        val bruto = repo.findById(chave).map { it.valor.trim() }.orElse(null) ?: return padrao
        val valor = bruto.toIntOrNull()
        if (valor == null || valor !in min..max) {
            log.warn("Configuracao '{}' com valor invalido ('{}'). Usando o padrao de {}.", chave, bruto, padrao)
            return padrao
        }
        return valor
    }

    /** Percentual agregado (todas as formacoes do ano somadas) exigido do catequista. */
    fun minimoAgregadoFormacao(): Int =
        lerInt(Configuracao.FORMACAO_MINIMO_AGREGADO, Configuracao.FORMACAO_MINIMO_AGREGADO_PADRAO, 0, 100)

    /** Mes (1-12) em que o ano de formacao fecha -- abaixo do minimo vira vermelho a partir dele. */
    fun fechamentoMesFormacao(): Int =
        lerInt(Configuracao.FORMACAO_FECHAMENTO_MES, Configuracao.FORMACAO_FECHAMENTO_MES_PADRAO, 1, 12)

    /** Quantos meses antes do fechamento o alerta amarelo comeca. */
    fun alertaMesesAntesFormacao(): Int =
        lerInt(
            Configuracao.FORMACAO_ALERTA_MESES_ANTES,
            Configuracao.FORMACAO_ALERTA_MESES_ANTES_PADRAO, 0, 11
        )

    @Transactional
    fun definirConfigFormacao(minimoAgregado: Int, fechamentoMes: Int, alertaMesesAntes: Int, quem: String?) {
        require(minimoAgregado in 0..100) { "O minimo agregado deve ficar entre 0 e 100." }
        require(fechamentoMes in 1..12) { "O mes de fechamento deve ser de 1 a 12." }
        require(alertaMesesAntes in 0..11) { "Os meses de alerta antes devem ficar entre 0 e 11." }

        fun salvar(chave: String, valor: Int, descricao: String) = repo.save(
            Configuracao(
                chave = chave, valor = valor.toString(), descricao = descricao,
                atualizadoEm = LocalDateTime.now(), atualizadoPor = quem
            )
        )
        salvar(
            Configuracao.FORMACAO_MINIMO_AGREGADO, minimoAgregado,
            "Percentual agregado (todas as formacoes do ano) exigido do catequista."
        )
        salvar(
            Configuracao.FORMACAO_FECHAMENTO_MES, fechamentoMes,
            "Mes em que o ano de formacao do catequista fecha."
        )
        salvar(
            Configuracao.FORMACAO_ALERTA_MESES_ANTES, alertaMesesAntes,
            "Quantos meses antes do fechamento o alerta amarelo comeca."
        )
        log.info(
            "Configuracao de formacao de catequista alterada por '{}': minimo={}%, fechamento mes={}, alerta {} mes(es) antes",
            quem ?: "?", minimoAgregado, fechamentoMes, alertaMesesAntes
        )
    }
}
