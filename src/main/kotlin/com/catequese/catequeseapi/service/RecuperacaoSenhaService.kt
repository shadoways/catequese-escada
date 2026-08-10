package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.TokenRecuperacao
import com.catequese.catequeseapi.repository.TokenRecuperacaoRepository
import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.security.PoliticaSenha
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.LocalDateTime
import java.util.Base64

/**
 * Fluxo de "esqueci minha senha".
 *
 * Decisoes de seguranca:
 * - O token vai por e-mail em texto, mas no banco fica so o SHA-256 dele.
 * - Uso unico e validade curta (30 min).
 * - Cada novo pedido invalida os pedidos anteriores daquele usuario.
 * - Ha um intervalo minimo entre pedidos, para o endpoint nao virar ferramenta
 *   de spam contra o e-mail de alguem.
 * - Quem chama nunca descobre se o e-mail existe: o controller responde sempre
 *   a mesma coisa, aconteca o que acontecer aqui dentro.
 * - Redefinir a senha tambem destrava a conta bloqueada por tentativas erradas,
 *   senao quem foi bloqueado ficaria sem saida ate o tempo passar.
 */
@Service
class RecuperacaoSenhaService(
    private val usuarioRepository: UsuarioRepository,
    private val tokenRepository: TokenRecuperacaoRepository,
    private val passwordEncoder: PasswordEncoder,
    private val emailService: EmailService,
    @Value("\${app.url.base:http://localhost:8080}") private val urlBase: String,
    @Value("\${app.recuperacao.logar-link:false}") private val logarLink: Boolean
) {
    private val log = LoggerFactory.getLogger(RecuperacaoSenhaService::class.java)

    /** Resultado da redefinicao, para o controller traduzir em HTTP. */
    sealed class ResultadoRedefinicao {
        object Sucesso : ResultadoRedefinicao()
        object TokenInvalido : ResultadoRedefinicao()
        data class SenhaFraca(val motivo: String) : ResultadoRedefinicao()
    }

    /**
     * Gera e envia o link. Silencioso de proposito: qualquer motivo de nao envio
     * (e-mail inexistente, conta inativa, pedido repetido) termina sem erro.
     */
    @Transactional
    fun solicitar(email: String, ip: String?) {
        val usuario = usuarioRepository.findFirstByEmailIgnoreCaseAndAtivoTrue(email.trim())
        if (usuario == null) {
            log.info("Pedido de recuperacao para e-mail sem conta ativa. Nada enviado.")
            return
        }

        val agora = LocalDateTime.now()
        val ultimo = tokenRepository.findFirstByIdUsuarioOrderByCriadoEmDesc(usuario.idUsuario)
        if (ultimo != null &&
            Duration.between(ultimo.criadoEm, agora) < Duration.ofMinutes(INTERVALO_MINIMO_MINUTOS)
        ) {
            log.info("Pedido de recuperacao ignorado para '{}': muito seguido", usuario.username)
            return
        }

        // Pedido novo invalida os anteriores.
        val abertos = tokenRepository.findAllByIdUsuarioAndUsadoEmIsNull(usuario.idUsuario)
        if (abertos.isNotEmpty()) {
            tokenRepository.saveAll(abertos.map { it.copy(usadoEm = agora) })
        }

        val token = gerarToken()
        tokenRepository.save(
            TokenRecuperacao(
                idUsuario = usuario.idUsuario,
                tokenHash = hash(token),
                expiraEm = agora.plusMinutes(VALIDADE_MINUTOS),
                criadoEm = agora,
                ipSolicitante = ip
            )
        )

        val destino = usuario.email ?: return
        val link = "${urlBase.trimEnd('/')}/redefinir-senha.html?token=$token"
        val enviado = emailService.enviar(
            destinatario = destino,
            assunto = "Catequese Admin - redefinicao de senha",
            corpo = """
                Ola, ${usuario.nome}.

                Recebemos um pedido para redefinir a senha do usuario "${usuario.username}".

                Para escolher uma nova senha, acesse o link abaixo:
                $link

                O link vale por $VALIDADE_MINUTOS minutos e so pode ser usado uma vez.

                Se voce nao pediu isso, ignore esta mensagem: sua senha continua a mesma.
            """.trimIndent()
        )

        // Valvula de escape para testar antes de existir SMTP. Fora de teste,
        // deixe app.recuperacao.logar-link=false: o link no log equivale a senha.
        if (!enviado && logarLink) {
            log.warn("[APENAS TESTE] Link de recuperacao de '{}': {}", usuario.username, link)
        }
    }

    @Transactional
    fun redefinir(token: String, novaSenha: String): ResultadoRedefinicao {
        val registro = tokenRepository.findByTokenHash(hash(token))
            ?: return ResultadoRedefinicao.TokenInvalido

        val agora = LocalDateTime.now()
        if (!registro.estaValido(agora)) return ResultadoRedefinicao.TokenInvalido

        val usuario = usuarioRepository.findById(registro.idUsuario).orElse(null)
            ?: return ResultadoRedefinicao.TokenInvalido

        if (!usuario.ativo) return ResultadoRedefinicao.TokenInvalido

        PoliticaSenha.validar(novaSenha, usuario.username, usuario.email)?.let {
            return ResultadoRedefinicao.SenhaFraca(it)
        }

        usuarioRepository.save(
            usuario.copy(
                passwordHash = passwordEncoder.encode(novaSenha),
                senhaProvisoria = false,
                dataTrocaSenha = agora,
                // Quem redefiniu a senha provou ter acesso ao e-mail: destrava a conta.
                tentativasFalhas = 0,
                bloqueadoAte = null
            )
        )
        tokenRepository.save(registro.copy(usadoEm = agora))

        log.info("Senha redefinida por link de recuperacao: '{}'", usuario.username)
        return ResultadoRedefinicao.Sucesso
    }

    private fun gerarToken(): String {
        val bytes = ByteArray(32).also { RANDOM.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val RANDOM = SecureRandom()
        const val VALIDADE_MINUTOS = 30L
        const val INTERVALO_MINIMO_MINUTOS = 2L
    }
}
