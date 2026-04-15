package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.auth.CreateUsuarioDTO
import com.catequese.catequeseapi.dto.auth.UsuarioDTO
import com.catequese.catequeseapi.enums.RoleType
import com.catequese.catequeseapi.exception.ResourceNotFoundException
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.model.UsuarioRole
import com.catequese.catequeseapi.repository.CatequistaRepository
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.repository.UsuarioRoleRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UsuarioService(
    private val usuarioRepository: UsuarioRepository,
    private val usuarioRoleRepository: UsuarioRoleRepository,
    private val comunidadeRepository: ComunidadeRepository,
    private val catequistaRepository: CatequistaRepository,
    private val passwordEncoder: PasswordEncoder
) {
    companion object {
        private val logger = LoggerFactory.getLogger(UsuarioService::class.java)
    }

    /**
     * Lista todos os usuários
     */
    fun findAll(): List<UsuarioDTO> {
        logger.info("🔍 Listando todos os usuários")
        return usuarioRepository.findAll().map { toDTO(it) }
    }

    /**
     * Busca usuário por ID
     */
    fun findById(id: Long): UsuarioDTO {
        logger.info("🔍 Buscando usuário por ID: $id")
        val usuario = usuarioRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }
        return toDTO(usuario)
    }

    /**
     * Busca usuário por email
     */
    fun findByEmail(email: String): UsuarioDTO {
        val normalizedEmail = email.trim().lowercase()
        logger.info("🔍 Buscando usuário por email")
        val usuario = usuarioRepository.findByEmail(normalizedEmail)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }
        return toDTO(usuario)
    }

    /**
     * Cria novo usuário
     */
    @Transactional
    fun create(dto: CreateUsuarioDTO): UsuarioDTO {
        val normalizedEmail = dto.email.trim().lowercase()
        logger.info("📝 Criando novo usuário")

        // Verificar se email já existe
        if (usuarioRepository.existsByEmail(normalizedEmail)) {
            throw IllegalArgumentException("Email já cadastrado")
        }

        // Validar senha
        if (dto.password.length < 6) {
            throw IllegalArgumentException("Senha deve ter no mínimo 6 caracteres")
        }

        // Buscar comunidade e catequista se informados
        val comunidade = dto.idComunidade?.let {
            comunidadeRepository.findById(it)
                .orElseThrow { ResourceNotFoundException("Comunidade não encontrada") }
        }

        val catequista = dto.idCatequista?.let {
            catequistaRepository.findById(it)
                .orElseThrow { ResourceNotFoundException("Catequista não encontrado") }
        }

        // Criar usuário
        val usuario = Usuario(
            nome = dto.nome,
            email = normalizedEmail,
            passwordHash = passwordEncoder.encode(dto.password),
            ativo = true,
            comunidade = comunidade,
            catequista = catequista
        )

        val usuarioSalvo = usuarioRepository.save(usuario)

        // Adicionar roles
        val roles = dto.roles.map { roleString ->
            val roleType = try {
                RoleType.valueOf(roleString)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Role inválida: $roleString")
            }

            UsuarioRole(
                role = roleType,
                usuario = usuarioSalvo
            )
        }

        usuarioRoleRepository.saveAll(roles)

        logger.info("✅ Usuário criado")

        // Recarregar para pegar as roles
        val usuarioCompleto = usuarioRepository.findById(usuarioSalvo.idUsuario).get()
        return toDTO(usuarioCompleto)
    }

    /**
     * Atualiza usuário existente
     */
    @Transactional
    fun update(id: Long, dto: UsuarioDTO): UsuarioDTO {
        logger.info("📝 Atualizando usuário: $id")
        val normalizedEmail = dto.email.trim().lowercase()

        val usuario = usuarioRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }

        // Verificar se email já existe em outro usuário
        if (normalizedEmail != usuario.email && usuarioRepository.existsByEmail(normalizedEmail)) {
            throw IllegalArgumentException("Email já cadastrado")
        }

        // Buscar comunidade e catequista se informados
        val comunidade = dto.idComunidade?.let {
            comunidadeRepository.findById(it)
                .orElseThrow { ResourceNotFoundException("Comunidade não encontrada") }
        }

        val catequista = dto.idCatequista?.let {
            catequistaRepository.findById(it)
                .orElseThrow { ResourceNotFoundException("Catequista não encontrado") }
        }

        // Atualizar dados
        val usuarioAtualizado = usuario.copy(
            nome = dto.nome,
            email = normalizedEmail,
            ativo = dto.ativo,
            comunidade = comunidade,
            catequista = catequista
        )

        val salvo = usuarioRepository.save(usuarioAtualizado)

        // Atualizar roles se necessário
        if (dto.roles.isNotEmpty()) {
            // Remover roles antigas
            usuarioRoleRepository.deleteAll(usuario.roles)

            // Adicionar novas roles
            val novasRoles = dto.roles.map { roleType ->
                UsuarioRole(
                    role = roleType,
                    usuario = salvo
                )
            }
            usuarioRoleRepository.saveAll(novasRoles)
        }

        logger.info("✅ Usuário atualizado: $id")

        // Recarregar para pegar as roles atualizadas
        val usuarioCompleto = usuarioRepository.findById(id).get()
        return toDTO(usuarioCompleto)
    }

    /**
     * Ativa ou desativa usuário
     */
    fun toggleAtivo(id: Long): UsuarioDTO {
        logger.info("🔄 Alterando status do usuário: $id")

        val usuario = usuarioRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Usuário não encontrado") }

        val usuarioAtualizado = usuario.copy(ativo = !usuario.ativo)
        usuarioRepository.save(usuarioAtualizado)

        logger.info("✅ Status alterado: ${usuarioAtualizado.ativo}")

        return toDTO(usuarioAtualizado)
    }

    /**
     * Deleta usuário
     */
    fun delete(id: Long) {
        logger.info("🗑️  Deletando usuário: $id")

        if (!usuarioRepository.existsById(id)) {
            throw ResourceNotFoundException("Usuário não encontrado")
        }

        usuarioRepository.deleteById(id)

        logger.info("✅ Usuário deletado: $id")
    }

    /**
     * Converte Usuario para DTO
     */
    private fun toDTO(usuario: Usuario): UsuarioDTO {
        return UsuarioDTO(
            idUsuario = usuario.idUsuario,
            nome = usuario.nome,
            email = usuario.email,
            ativo = usuario.ativo,
            roles = usuario.roles.map { it.role },
            idComunidade = usuario.comunidade?.idComunidade,
            idCatequista = usuario.catequista?.idCatequista
        )
    }
}

