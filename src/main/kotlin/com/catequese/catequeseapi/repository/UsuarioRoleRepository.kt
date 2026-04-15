package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.UsuarioRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UsuarioRoleRepository : JpaRepository<UsuarioRole, Long>

