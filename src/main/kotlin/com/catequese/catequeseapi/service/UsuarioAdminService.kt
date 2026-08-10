package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.AtualizarUsuarioDTO
import com.catequese.catequeseapi.dto.CriarUsuarioDTO
import com.catequese.catequeseapi.dto.SenhaProvisoriaDTO
import com.catequese.catequeseapi.dto.UsuarioDTO
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.TokenRecuperacaoRepository
import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.security.PoliticaSenha
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Gestao de usuarios pelo coordenador paroquial.
 *
 * O administrador nunca escolhe nem le a senha de ninguem: ele apenas dispara a
 * geracao de uma senha provisoria, que aparece uma unica vez na resposta e tem
 * de ser trocada no primeiro acesso.
 *
 * Ha duas travas para o sistema nao ficar sem dono:
 * - nao da para tirar o acesso do ultimo coordenador paroquial ativo;
 * - ninguem pode se desativar nem se rebaixar sozinho.
 */
@Service
class UsuarioAdminService(
    private val usuarioRepository: UsuarioRepository,
    private val tokenRepository: TokenRecuperacaoRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val log = LoggerFactory.getLogger(UsuarioAdminService::class.java)

    fun listar(): List<UsuarioDTO> = usuarioRepository.findAllByOrderByNomeAsc().map(UsuarioDTO::de)

    fun buscar(id: Long): UsuarioDTO = UsuarioDTO.de(exigir(id))

    @Transactional
    fun criar(dto: CriarUsuarioDTO): SenhaProvisoriaDTO {
        val username = dto.username.trim()
        val nome = dto.nome.trim()

        require(nome.isNotBlank()) { "Informe o nome do usuario." }
        require(username.length >= 3) { "O nome de usuario deve ter pelo menos 3 caracteres." }
        require(!username.contains(" ")) { "O nome de usuario nao pode conter espacos." }
        require(!usuarioRepository.existsByUsername(username)) {
            "Ja existe um usuario com o login \"$username\"."
        }

        val senha = PoliticaSenha.gerarSenhaProvisoria()
        val salvo = usuarioRepository.save(
            Usuario(
                nome = nome,
                username = username,
                passwordHash = passwordEncoder.encode(senha),
                email = dto.email?.trim()?.ifBlank { null },
                telefone = dto.telefone?.trim()?.ifBlank { null },
                tipo = dto.tipo,
                idCatequista = dto.idCatequista,
                idCoordenador = dto.idCoordenador,
                senhaProvisoria = true,
                ativo = true,
                dataCriacao = LocalDateTime.now()
            )
        )

        log.info("Usuario '{}' criado como {}", salvo.username, salvo.tipo)
        return SenhaProvisoriaDTO(UsuarioDTO.de(salvo), senha)
    }

    @Transactional
    fun atualizar(id: Long, dto: AtualizarUsuarioDTO, usernameLogado: String?): UsuarioDTO {
        val atual = exigir(id)

        require(dto.nome.isNotBlank()) { "Informe o nome do usuario." }
        validarNaoSeAutoDestruir(atual, dto, usernameLogado)
        validarSobraAdmin(atual, dto.tipo, dto.ativo)

        val atualizado = usuarioRepository.save(
            atual.copy(
                nome = dto.nome.trim(),
                email = dto.email?.trim()?.ifBlank { null },
                telefone = dto.telefone?.trim()?.ifBlank { null },
                tipo = dto.tipo,
                ativo = dto.ativo,
                idCatequista = dto.idCatequista,
                idCoordenador = dto.idCoordenador
            )
        )

        log.info("Usuario '{}' atualizado (tipo={}, ativo={})",
            atualizado.username, atualizado.tipo, atualizado.ativo)
        return UsuarioDTO.de(atualizado)
    }

    /** Gera uma nova senha provisoria. Derruba as sessoes e os links pendentes do usuario. */
    @Transactional
    fun resetarSenha(id: Long): SenhaProvisoriaDTO {
        val usuario = exigir(id)
        val senha = PoliticaSenha.gerarSenhaProvisoria()
        val agora = LocalDateTime.now()

        val atualizado = usuarioRepository.save(
            usuario.copy(
                passwordHash = passwordEncoder.encode(senha),
                senhaProvisoria = true,
                // Muda a marca de senha: os tokens JWT emitidos antes param de valer.
                dataTrocaSenha = agora,
                tentativasFalhas = 0,
                bloqueadoAte = null
            )
        )

        // Links de "esqueci minha senha" em aberto perdem a validade.
        val pendentes = tokenRepository.findAllByIdUsuarioAndUsadoEmIsNull(usuario.idUsuario)
        if (pendentes.isNotEmpty()) {
            tokenRepository.saveAll(pendentes.map { it.copy(usadoEm = agora) })
        }

        log.info("Senha de '{}' resetada pelo administrador", atualizado.username)
        return SenhaProvisoriaDTO(UsuarioDTO.de(atualizado), senha)
    }

    /** Libera antes da hora uma conta bloqueada por tentativas erradas. */
    @Transactional
    fun desbloquear(id: Long): UsuarioDTO {
        val usuario = exigir(id)
        val atualizado = usuarioRepository.save(
            usuario.copy(tentativasFalhas = 0, bloqueadoAte = null)
        )
        log.info("Usuario '{}' desbloqueado pelo administrador", atualizado.username)
        return UsuarioDTO.de(atualizado)
    }

    private fun exigir(id: Long): Usuario = usuarioRepository.findById(id)
        .orElseThrow { ResourceNotFoundException("Usuario nao encontrado") }

    private fun validarNaoSeAutoDestruir(
        alvo: Usuario,
        dto: AtualizarUsuarioDTO,
        usernameLogado: String?
    ) {
        if (usernameLogado == null || alvo.username != usernameLogado) return

        require(dto.ativo) { "Voce nao pode desativar a propria conta." }
        require(dto.tipo.isAdmin) {
            "Voce nao pode remover o proprio acesso de administrador. " +
                "Peca para outro coordenador paroquial fazer isso."
        }
    }

    private fun validarSobraAdmin(atual: Usuario, novoTipo: TipoUsuario, novoAtivo: Boolean) {
        val eraAdminAtivo = atual.tipo.isAdmin && atual.ativo
        val continuaAdminAtivo = novoTipo.isAdmin && novoAtivo
        if (!eraAdminAtivo || continuaAdminAtivo) return

        val adminsAtivos = usuarioRepository.countByTipoAndAtivoTrue(TipoUsuario.COORDENADOR_PAROQUIAL)
        require(adminsAtivos > 1) {
            "Este e o unico coordenador paroquial ativo. Promova outro usuario antes de " +
                "rebaixar ou desativar este."
        }
    }
}
