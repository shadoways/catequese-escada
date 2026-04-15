package com.catequese.catequeseapi.config

import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.service.AuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val authService: AuthService,
    private val usuarioRepository: UsuarioRepository
) : OncePerRequestFilter() {

    companion object {
        private val logger = LoggerFactory.getLogger(JwtAuthenticationFilter::class.java)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authHeader.substringAfter("Bearer ").trim()

        if (!authService.validateToken(token) || SecurityContextHolder.getContext().authentication != null) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val email = authService.getEmailFromToken(token)
            val usuario = usuarioRepository.findByEmail(email).orElse(null)

            if (usuario != null && usuario.ativo) {
                val authorities = usuario.roles
                    .map { SimpleGrantedAuthority("ROLE_${it.role.name}") }

                val authentication = UsernamePasswordAuthenticationToken(email, null, authorities).apply {
                    details = WebAuthenticationDetailsSource().buildDetails(request)
                }

                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (ex: Exception) {
            logger.warn("Token JWT ignorado por erro de parsing")
        }

        filterChain.doFilter(request, response)
    }
}

