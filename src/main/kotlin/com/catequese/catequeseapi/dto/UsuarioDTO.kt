package com.catequese.catequeseapi.dto

import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import java.time.LocalDateTime

/**
 * Visao segura do usuario para a tela de administracao.
 * Nunca carrega o hash da senha.
 */
data class UsuarioDTO(
    val idUsuario: Long,
    val nome: String,
    val username: String,
    val email: String?,
    val telefone: String?,
    val tipo: TipoUsuario,
    val ativo: Boolean,
    val senhaProvisoria: Boolean,
    val bloqueado: Boolean,
    val bloqueadoAte: LocalDateTime?,
    val ultimoLogin: LocalDateTime?,
    val dataCriacao: LocalDateTime?,
    val idCatequista: Long?,
    val idCoordenador: Long?
) {
    companion object {
        fun de(usuario: Usuario) = UsuarioDTO(
            idUsuario = usuario.idUsuario,
            nome = usuario.nome,
            username = usuario.username,
            email = usuario.email,
            telefone = usuario.telefone,
            tipo = usuario.tipo,
            ativo = usuario.ativo,
            senhaProvisoria = usuario.senhaProvisoria,
            bloqueado = usuario.estaBloqueado(),
            bloqueadoAte = usuario.bloqueadoAte,
            ultimoLogin = usuario.ultimoLogin,
            dataCriacao = usuario.dataCriacao,
            idCatequista = usuario.idCatequista,
            idCoordenador = usuario.idCoordenador
        )
    }
}

/** Criacao de usuario. A senha nao vem daqui: quem gera e o sistema. */
data class CriarUsuarioDTO(
    val nome: String = "",
    val username: String = "",
    val email: String? = null,
    val telefone: String? = null,
    val tipo: TipoUsuario = TipoUsuario.CATEQUISTA,
    val idCatequista: Long? = null,
    val idCoordenador: Long? = null
)

/** Edicao de usuario. Tambem nao mexe em senha. */
data class AtualizarUsuarioDTO(
    val nome: String = "",
    val email: String? = null,
    val telefone: String? = null,
    val tipo: TipoUsuario = TipoUsuario.CATEQUISTA,
    val ativo: Boolean = true,
    val idCatequista: Long? = null,
    val idCoordenador: Long? = null
)

/**
 * Resposta da criacao e do reset de senha.
 * A senha provisoria aparece UMA UNICA VEZ, aqui -- depois so resetando de novo.
 */
data class SenhaProvisoriaDTO(
    val usuario: UsuarioDTO,
    val senhaProvisoria: String,
    val aviso: String = "Anote e entregue esta senha ao usuario. Ela nao sera mostrada de novo " +
        "e devera ser trocada no primeiro acesso."
)
