package com.catequese.catequeseapi.model

import com.catequese.catequeseapi.enums.RoleType
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*

@Entity
@Table(name = "tb_usuario_role")
data class UsuarioRole(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario_role")
    val idUsuarioRole: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: RoleType,

    @ManyToOne
    @JoinColumn(name = "id_usuario", nullable = false)
    @JsonIgnore
    val usuario: Usuario
)

