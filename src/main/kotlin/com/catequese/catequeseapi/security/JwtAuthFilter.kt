package com.catequese.catequeseapi.security

import com.catequese.catequeseapi.repository.UsuarioRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Le o cabecalho "Authorization: Bearer <token>" e autentica a requisicao.
 *
 * Nao basta o token estar assinado: o usuario e relido do banco a cada chamada
 * porque um token continua valido ate expirar, e nesse meio tempo a conta pode
 * ter sido desativada ou a senha trocada. Sao consultas por indice unico
 * (username), baratas na escala deste sistema.
 *
 * Requisicao sem token segue sem autenticacao -- quem decide se aquilo exige
 * login ou nao e a SecurityConfig.
 *
 * De proposito NAO e um @Component: um bean do tipo Filter tambem seria
 * registrado automaticamente no chain de servlet do Spring Boot, fora do chain
 * do Security. A SecurityConfig instancia esta classe diretamente.
 */
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val usuarioRepository: UsuarioRepository,
    private val securityEnabled: Boolean
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")

        if (header == null || !header.startsWith("Bearer ") ||
            SecurityContextHolder.getContext().authentication != null
        ) {
            filterChain.doFilter(request, response)
            return
        }

        val claims = jwtService.lerToken(header.removePrefix("Bearer ").trim())
        val tipo = claims?.let { jwtService.tipoDoToken(it) }

        if (claims == null || tipo == null) {
            filterChain.doFilter(request, response)
            return
        }

        val usuario = usuarioRepository.findByUsername(claims.subject)

        // Conta apagada, desativada ou senha trocada depois que o token foi emitido.
        if (usuario == null ||
            !usuario.ativo ||
            jwtService.marcaDeSenhaDoToken(claims) != jwtService.marcaDeSenha(usuario)
        ) {
            filterChain.doFilter(request, response)
            return
        }

        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            usuario.username,
            null,
            listOf(SimpleGrantedAuthority(tipo.role))
        )

        // Senha provisoria: o usuario esta autenticado, mas so pode trocar a
        // propria senha. Sem isso, quem recebesse uma senha provisoria poderia
        // usar o sistema inteiro sem nunca troca-la.
        if (securityEnabled && usuario.senhaProvisoria && !rotaLiberadaComSenhaProvisoria(request)) {
            SecurityContextHolder.clearContext()
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write(
                """{"erro":"Troque a senha provisoria para continuar.","codigo":"SENHA_PROVISORIA"}"""
            )
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun rotaLiberadaComSenhaProvisoria(request: HttpServletRequest): Boolean {
        val caminho = request.requestURI ?: return false
        return ROTAS_LIBERADAS.any { caminho.endsWith(it) }
    }

    private companion object {
        val ROTAS_LIBERADAS = listOf("/api/auth/trocar-senha", "/api/auth/me")
    }
}
