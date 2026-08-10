package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UsuarioRepository : JpaRepository<Usuario, Long> {

    fun findByUsername(username: String): Usuario?

    fun existsByUsername(username: String): Boolean

    /** Usado na recuperacao de senha. Ignora maiusculas/minusculas. */
    fun findFirstByEmailIgnoreCaseAndAtivoTrue(email: String): Usuario?

    fun findAllByOrderByNomeAsc(): List<Usuario>

    /** Guarda-costas: impede o sistema ficar sem nenhum administrador ativo. */
    fun countByTipoAndAtivoTrue(tipo: TipoUsuario): Long
}
