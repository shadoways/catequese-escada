package com.catequese.catequeseapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class EmailService {
    companion object {
        private val logger = LoggerFactory.getLogger(EmailService::class.java)
    }

    /**
     * Envia email de reset de senha
     * TODO: Implementar integração com serviço de email (SendGrid, AWS SES, etc)
     */
    fun sendPasswordResetEmail(email: String, nome: String, token: String) {
        logger.info("📧 Enviando email de reset de senha para: $email")

        // Mantém referência ao token até integrar envio real de email externo.
        @Suppress("UNUSED_VARIABLE")
        val resetToken = token

        logger.info("""
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            📧 EMAIL DE RESET DE SENHA
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Para: $email
            Nome: $nome
            
            Olá $nome,
            
            Você solicitou a recuperação de senha.
            Clique no link abaixo para criar uma nova senha:
            
            [LINK DE RESET GERADO - NÃO LOGAR TOKEN EM PRODUÇÃO]
            
            Este link é válido por 24 horas.
            
            Se você não solicitou esta recuperação, ignore este email.
            
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        """.trimIndent())


        // TODO: Implementar envio real de email
        // Exemplo com SendGrid:
        // val mail = Mail()
        // mail.setFrom(Email("noreply@catequese.com"))
        // mail.setSubject("Recuperação de Senha")
        // mail.addTo(Email(email))
        // mail.addContent(Content("text/html", htmlBody))
        // sendGridClient.send(mail)
    }

    /**
     * Envia email de boas-vindas
     */
    fun sendWelcomeEmail(email: String, nome: String, temporaryPassword: String) {
        logger.info("📧 Enviando email de boas-vindas para: $email")

        @Suppress("UNUSED_VARIABLE")
        val hiddenTemporaryPassword = temporaryPassword

        logger.info("""
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            📧 EMAIL DE BOAS-VINDAS
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Para: $email
            Nome: $nome
            
            Olá $nome,
            
            Bem-vindo ao sistema de Catequese!
            
            Seus dados de acesso:
            Email: $email
            Senha temporária: [NÃO EXIBIDA]
            
            Por favor, altere sua senha no primeiro acesso.
            
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        """.trimIndent())
    }
}

