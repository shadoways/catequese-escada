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
}
