package com.catequese.catequeseapi.config

import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.security.PoliticaSenha
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Cria o primeiro administrador (coordenador paroquial) na subida do app,
 * e SOMENTE quando a tabela tb_usuario esta vazia.
 *
 * A senha e sempre marcada como provisoria: no primeiro login o sistema exige a
 * troca antes de liberar qualquer outra tela.
 *
 * Se ADMIN_INICIAL_PASSWORD nao for informada, o proprio sistema gera uma senha
 * aleatoria e imprime no log da subida -- e a unica vez que ela aparece.
 *
 *   export ADMIN_INICIAL_USERNAME=coordenador          # opcional (padrao: admin)
 *   export ADMIN_INICIAL_PASSWORD='senha forte'        # opcional (padrao: gerada)
 *   export ADMIN_INICIAL_NOME='Nome do Coordenador'    # opcional
 *   export ADMIN_INICIAL_EMAIL=coordenador@paroquia.org # opcional, para recuperar senha
 */
@Component
class AdminBootstrap(
    private val usuarioRepository: UsuarioRepository,
    private val passwordEncoder: PasswordEncoder,
    @Value("\${admin.inicial.username:admin}") private val username: String,
    @Value("\${admin.inicial.password:}") private val password: String,
    @Value("\${admin.inicial.nome:Coordenador Paroquial}") private val nome: String,
    @Value("\${admin.inicial.email:}") private val email: String
) : CommandLineRunner {

    private val log = LoggerFactory.getLogger(AdminBootstrap::class.java)

    override fun run(vararg args: String?) {
        if (usuarioRepository.count() > 0L) return

        val usuarioFinal = username.trim().ifBlank { "admin" }
        val senhaGerada = password.isBlank()
        val senhaFinal = if (senhaGerada) PoliticaSenha.gerarSenhaProvisoria() else password

        usuarioRepository.save(
            Usuario(
                nome = nome,
                username = usuarioFinal,
                passwordHash = passwordEncoder.encode(senhaFinal),
                email = email.trim().ifBlank { null },
                tipo = TipoUsuario.COORDENADOR_PAROQUIAL,
                senhaProvisoria = true,
                ativo = true,
                dataCriacao = LocalDateTime.now()
            )
        )

        if (senhaGerada) {
            log.warn(
                "\n" +
                    "=========================================================\n" +
                    " ADMINISTRADOR INICIAL CRIADO\n" +
                    " usuario: {}\n" +
                    " senha provisoria: {}\n" +
                    " Anote agora: esta senha nao sera mostrada de novo.\n" +
                    " O sistema exigira a troca no primeiro login.\n" +
                    "=========================================================",
                usuarioFinal, senhaFinal
            )
        } else {
            log.warn(
                "Administrador inicial '{}' criado com a senha informada em " +
                    "ADMIN_INICIAL_PASSWORD. A troca sera exigida no primeiro login. " +
                    "Remova a variavel do ambiente.",
                usuarioFinal
            )
        }
    }
}
