package com.catequese.catequeseapi.security

import com.catequese.catequeseapi.service.ConfiguracaoService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Faz valer o interruptor de cadastro publico.
 *
 * Esconder o formulario na tela nao impede nada: quem souber o endereco da API
 * ainda conseguiria gravar um cadastro com um POST direto. Por isso o bloqueio
 * fica aqui, no caminho de todas as requisicoes.
 *
 * Quem pode editar (coordenador e coordenador paroquial) continua cadastrando
 * mesmo com as inscricoes encerradas -- e comum precisar incluir alguem a mao
 * depois do prazo.
 *
 * De proposito NAO e um @Component: veja a nota em JwtAuthFilter.
 */
class CadastroPublicoFilter(
    private val configuracaoService: ConfiguracaoService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val precisaChecar = request.method.equals("POST", ignoreCase = true) &&
            ehRotaDeCadastro(request.requestURI)

        if (!precisaChecar || configuracaoService.cadastroAberto() || podeEditar()) {
            filterChain.doFilter(request, response)
            return
        }

        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write(
            """{"erro":"As inscricoes estao encerradas no momento.","codigo":"CADASTRO_FECHADO"}"""
        )
    }

    private fun ehRotaDeCadastro(uri: String?): Boolean {
        val caminho = uri ?: return false
        return ROTAS_DE_CADASTRO.any { caminho.contains(it) }
    }

    private fun podeEditar(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.any { it.authority in PAPEIS_QUE_EDITAM }
    }

    private companion object {
        val ROTAS_DE_CADASTRO = listOf(
            "/api/catequisandos",
            "/api/fichas",
            "/api/documentos",
            "/api/files"
        )
        val PAPEIS_QUE_EDITAM = setOf("ROLE_COORDENADOR", "ROLE_COORDENADOR_PAROQUIAL")
    }
}
