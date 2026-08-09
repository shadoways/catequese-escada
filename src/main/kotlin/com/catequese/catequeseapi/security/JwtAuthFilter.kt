package com.catequese.catequeseapi.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Le o cabecalho "Authorization: Bearer <token>" e, se o token for valido,
 * coloca o usuario autenticado no contexto do Spring Security com a role
 * correspondente ao seu tipo (ROLE_CATEQUISTA, ROLE_COORDENADOR, ...).
 *
 * Requisicao sem token simplesmente segue sem autenticacao -- quem decide se
 * aquilo exige login ou nao e a SecurityConfig.
 *
 * De proposito NAO e um @Component: um bean do tipo Filter tambem seria
 * registrado automaticamente no chain de servlet do Spring Boot, fora do chain
 * do Security. A SecurityConfig instancia esta classe diretamente.
 */
class JwtAuthFilter(private val jwtService: JwtService) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")

        if (header != null && header.startsWith("Bearer ") &&
            SecurityContextHolder.getContext().authentication == null
        ) {
            val claims = jwtService.lerToken(header.removePrefix("Bearer ").trim())
            val tipo = claims?.let { jwtService.tipoDoToken(it) }

            if (claims != null && tipo != null) {
                val auth = UsernamePasswordAuthenticationToken(
                    claims.subject,
                    null,
                    listOf(SimpleGrantedAuthority(tipo.role))
                )
                SecurityContextHolder.getContext().authentication = auth
            }
        }

        filterChain.doFilter(request, response)
    }
}
