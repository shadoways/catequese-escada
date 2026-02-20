package com.catequese.catequeseapi.model

    import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "tb_login")
data class Login(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val idLogin: Long = 0,

    val username: String,
    val passwordHash: String,

    @ManyToOne
    @JoinColumn(name = "id_catequista")
    val catequista: Catequista? = null,

    @OneToMany(mappedBy = "login")
    @JsonIgnore
    val permissoes: List<Permissao> = emptyList()
)