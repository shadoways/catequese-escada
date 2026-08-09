package com.catequese.catequeseapi.config

import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.UsuarioRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Cria o primeiro usuario administrador (coordenador paroquial) na subida do app.
 *
 * Existe porque a senha precisa ser gravada como hash BCrypt, e nao da para
 * escrever um hash direto no SQL sem gerar por fora. Aqui o proprio Spring gera.
 *
 * So roda quando a tabela tb_usuario esta VAZIA e as duas variaveis de ambiente
 * abaixo estao preenchidas -- depois disso nunca mais mexe em nada:
 *
 *   export ADMIN_INICIAL_USERNAME=coordenador
 *   export ADMIN_INICIAL_PASSWORD='uma senha forte aqui'
 *   export ADMIN_INICIAL_NOME='Nome do Coordenador Paroquial'   # opcional
 *
 * Depois de criado, remova as variaveis do ambiente.
 */
@Component
class AdminBootstrap(
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${admin.inicial.username:}") private val username: String,
    @Value("\${admin.inicial.password:}") private val password: String,
    @Value("\${admin.inicial.nome:Coordenador Paroquial}") private val nome: String
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(AdminBootstrap::class.java)

    override fun run(vararg args: String?) {
        if (usuarioRepository.count() > 0L) return

        if (username.isBlank() || password.isBlank()) {
            log.warn(
                "Nenhum usuario cadastrado em tb_usuario e ADMIN_INICIAL_USERNAME/" +
                    "ADMIN_INICIAL_PASSWORD nao foram definidos. Ninguem conseguira entrar " +
                    "quando app.security.enabled=true."
            )
            return
        }

        val admin = Usuario(
            nome = nome,
            username = username.trim(),
            passwordHash = passwordEncoder.encode(password),
            tipo = TipoUsuario.COORDENADOR_PAROQUIAL,
            ativo = true,
            dataCriacao = LocalDateTime.now()
        )
        usuarioRepository.save(admin)

        log.info(
            "Usuario administrador inicial '{}' criado como COORDENADOR_PAROQUIAL. " +
                "Remova ADMIN_INICIAL_USERNAME/ADMIN_INICIAL_PASSWORD do ambiente.",
            admin.username
        )
    }
}
