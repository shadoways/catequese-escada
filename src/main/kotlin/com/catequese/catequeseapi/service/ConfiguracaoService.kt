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
