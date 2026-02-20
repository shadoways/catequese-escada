package com.catequese.catequeseapi.repository

import com.catequese.catequeseapi.model.Login
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface LoginRepository : JpaRepository<Login, Long>