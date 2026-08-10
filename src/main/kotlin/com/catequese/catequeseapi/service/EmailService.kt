package com.catequese.catequeseapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/**
 * Envio de e-mail.
 *
 * O JavaMailSender so existe quando spring.mail.host esta configurado, por isso
 * ele e injetado como ObjectProvider: sem SMTP configurado a aplicacao continua
 * subindo normalmente e o envio apenas nao acontece (o admin ainda consegue
 * resetar a senha de qualquer usuario pela tela de usuarios).
 */
@Service
class EmailService(
    private val mailSenderProvider: ObjectProvider<JavaMailSender>,
    @Value("\${app.email.remetente:}") private val remetente: String,
    // Nao basta olhar se o bean existe: como spring.mail.host e declarado com
    // valor vazio, o Spring cria o JavaMailSender assim mesmo. Quem diz de
    // verdade se ha SMTP e o host estar preenchido.
    @Value("\${spring.mail.host:}") private val smtpHost: String
) {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    fun estaConfigurado(): Boolean =
        smtpHost.isNotBlank() && mailSenderProvider.ifAvailable != null

    /**
     * Envia um e-mail simples. Devolve true se conseguiu enviar.
     * Nunca lanca excecao: uma falha de SMTP nao pode derrubar o fluxo de quem
     * pediu a recuperacao nem revelar detalhes para quem esta do outro lado.
     */
    fun enviar(destinatario: String, assunto: String, corpo: String): Boolean {
        val mailSender = mailSenderProvider.ifAvailable
        if (smtpHost.isBlank() || mailSender == null) {
            log.warn(
                "SMTP nao configurado (spring.mail.host vazio): e-mail para '{}' nao enviado. " +
                    "Configure o SMTP ou use o reset de senha pelo administrador.",
                destinatario
            )
            return false
        }

        return try {
            val mensagem = SimpleMailMessage().apply {
                if (remetente.isNotBlank()) from = remetente
                setTo(destinatario)
                subject = assunto
                text = corpo
            }
            mailSender.send(mensagem)
            log.info("E-mail '{}' enviado para '{}'", assunto, destinatario)
            true
        } catch (ex: Exception) {
            log.error("Falha ao enviar e-mail para '{}': {}", destinatario, ex.message)
            false
        }
    }
}
