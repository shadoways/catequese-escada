package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.TurmaCatequistaRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import com.catequese.catequeseapi.repository.UsuarioRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

/**
 * Responde "o que este usuario pode enxergar".
 *
 * O coordenador so ve a propria comunidade; o catequista, as turmas em que ele
 * atua; o coordenador paroquial ve tudo. Esta regra e de DADOS, nao de tela:
 * esconder no frontend nao adianta, porque a API continuaria devolvendo o
 * resto para quem chamasse direto.
 *
 * Fica num servico proprio, e nao espalhada pelos controllers, para o dia em
 * que a regra mudar existir um lugar unico para mexer.
 */
@Service
class EscopoAcessoService(
    private val usuarioRepository: UsuarioRepository,
    private val turmaCatequistaRepository: TurmaCatequistaRepository,
    private val turmaRepository: TurmaRepository
) {

    fun usuarioLogado(): Usuario? {
        val username = SecurityContextHolder.getContext().authentication?.name ?: return null
        return usuarioRepository.findByUsername(username)?.takeIf { it.ativo }
    }

    fun ehAdmin(): Boolean = usuarioLogado()?.tipo?.isAdmin == true

    /**
     * Comunidades que o usuario alcanca.
     * `null` significa TODAS -- e nao "nenhuma", cuidado ao usar.
     */
    fun comunidadesPermitidas(): List<Long>? {
        val usuario = usuarioLogado() ?: return emptyList()
        if (usuario.tipo == TipoUsuario.COORDENADOR_PAROQUIAL) return null

        val comunidade = usuario.idComunidade ?: return null
        return listOf(comunidade)
    }

    /** Conveniencia para filtrar uma entidade que tenha comunidade. */
    fun podeVerComunidade(idComunidade: Long?): Boolean {
        val permitidas = comunidadesPermitidas() ?: return true
        if (idComunidade == null) return permitidas.isEmpty().not()
        return idComunidade in permitidas
    }

    /**
     * Turmas em que o usuario e catequista.
     * `null` = nao se aplica esse filtro (coordenador e admin veem por comunidade).
     */
    fun turmasDoCatequista(): List<Long>? {
        val usuario = usuarioLogado() ?: return emptyList()
        if (usuario.tipo != TipoUsuario.CATEQUISTA) return null

        val idCatequista = usuario.idCatequista ?: return emptyList()
        return turmaCatequistaRepository.findByIdCatequista(idCatequista).map { it.idTurma }
    }

    /** True se o usuario pode alterar dados cadastrais (coordenador para cima). */
    fun podeEditarCadastro(): Boolean = usuarioLogado()?.tipo?.podeEditar == true

    /**
     * Catequistas que o usuario alcanca -- mesmo espirito de
     * `comunidadesPermitidas()`, para a tela de Consultar Catequistas.
     * `null` = TODOS (coordenador paroquial).
     *
     * Coordenador de comunidade enxerga quem atua numa turma DA COMUNIDADE
     * DELE (`turma.idComunidade`) -- mesmo recorte que a Chamada ja usa, nao
     * um novo. Catequista comum enxerga so o proprio id.
     */
    fun catequistasPermitidos(): List<Long>? {
        val usuario = usuarioLogado() ?: return emptyList()
        return when (usuario.tipo) {
            TipoUsuario.COORDENADOR_PAROQUIAL -> null
            TipoUsuario.CATEQUISTA -> listOfNotNull(usuario.idCatequista)
            TipoUsuario.COORDENADOR -> {
                val comunidade = usuario.idComunidade ?: return emptyList()
                turmaRepository.findAll()
                    .filter { it.idComunidade == comunidade }
                    .flatMap { turmaCatequistaRepository.findByIdTurma(it.idTurma) }
                    .map { it.idCatequista }
                    .distinct()
            }
        }
    }

    /** Conveniencia para filtrar um catequista especifico, igual a `podeVerComunidade`. */
    fun podeVerCatequista(idCatequista: Long): Boolean {
        val permitidos = catequistasPermitidos() ?: return true
        return idCatequista in permitidos
    }

    /**
     * A comunidade de um catequista, pela turma em que ele atua -- nao ha
     * campo direto em `Catequista`. Quando ele atua em turmas de comunidades
     * diferentes (dupla incomum, mas o modelo permite), fica a primeira
     * encontrada: e so para exibicao nesta tela, o recorte de verdade e por
     * turma, nao por este valor.
     */
    fun comunidadeDoCatequista(idCatequista: Long): Long? =
        turmaCatequistaRepository.findByIdCatequista(idCatequista)
            .asSequence()
            .mapNotNull { vinculo -> turmaRepository.findById(vinculo.idTurma).orElse(null)?.idComunidade }
            .firstOrNull()
}
