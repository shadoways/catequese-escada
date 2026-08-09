package com.catequese.catequeseapi.config

import com.catequese.catequeseapi.security.JwtAuthFilter
import com.catequese.catequeseapi.security.JwtService
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Regras de acesso da API.
 *
 * IMPORTANTE -- app.security.enabled:
 * enquanto o frontend nao tiver tela de login (Etapa 2) e as telas nao esconderem
 * as acoes por papel (Etapa 3), ligar as restricoes quebraria o uso atual do
 * sistema em producao. Por isso o padrao e `false`: o login ja funciona e ja
 * emite token, mas nenhuma rota e bloqueada ainda. Basta trocar para `true`
 * (ou exportar APP_SECURITY_ENABLED=true) para valer todo o controle abaixo.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    @Value("\${app.security.enabled:false}") private val securityEnabled: Boolean
) {
    private val log = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        // API stateless com token: sessao e CSRF de formulario nao se aplicam.
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .addFilterBefore(JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter::class.java)
            // Sem isso o Spring devolveria 403 tambem para quem nao mandou token.
            // O front precisa distinguir: 401 = faca login, 403 = logado mas sem permissao.
            .exceptionHandling { ex ->
                ex.authenticationEntryPoint { _, response, _ ->
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"erro":"Nao autenticado"}""")
                }
                ex.accessDeniedHandler { _, response, _ ->
                    response.status = HttpServletResponse.SC_FORBIDDEN
                    response.contentType = "application/json;charset=UTF-8"
                    response.writer.write("""{"erro":"Sem permissao para esta acao"}""")
                }
            }

        if (!securityEnabled) {
            log.warn(
                "app.security.enabled=false -- a API esta ABERTA. O login funciona e emite " +
                    "token, mas nenhuma rota exige autenticacao ainda."
            )
            http.authorizeHttpRequests { it.anyRequest().permitAll() }
            return http.build()
        }

        log.info("app.security.enabled=true -- restricoes de acesso por tipo de usuario ativas.")

        http.authorizeHttpRequests { auth ->
            auth
                // ---- Publico: paginas e arquivos estaticos ----
                .requestMatchers(
                    "/", "/index.html", "/ficha.html", "/login.html",
                    "/*.css", "/*.js", "/*.png", "/*.ico",
                    "/error"
                ).permitAll()

                // ---- Publico: login ----
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                // ---- Publico: status do cadastro (Etapa 4) ----
                .requestMatchers(HttpMethod.GET, "/api/config/cadastro").permitAll()

                // ---- Publico: a tela de cadastro e aberta ----
                // O formulario precisa das listas de turma/comunidade para montar os
                // selects, e precisa gravar catequisando, ficha, documentos e arquivos.
                .requestMatchers(HttpMethod.GET, "/api/turmas", "/api/comunidades").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/catequisandos").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/fichas").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/documentos").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/files/**").permitAll()

                // ---- Somente administrador (coordenador paroquial) ----
                .requestMatchers("/api/usuarios/**").hasRole("COORDENADOR_PAROQUIAL")
                .requestMatchers(HttpMethod.PUT, "/api/config/**").hasRole("COORDENADOR_PAROQUIAL")

                // ---- Leitura: qualquer usuario logado, inclusive catequista ----
                .requestMatchers(HttpMethod.GET, "/api/**").authenticated()

                // ---- Escrita: coordenador ou coordenador paroquial ----
                .requestMatchers(HttpMethod.POST, "/api/**")
                .hasAnyRole("COORDENADOR", "COORDENADOR_PAROQUIAL")
                .requestMatchers(HttpMethod.PUT, "/api/**")
                .hasAnyRole("COORDENADOR", "COORDENADOR_PAROQUIAL")
                .requestMatchers(HttpMethod.PATCH, "/api/**")
                .hasAnyRole("COORDENADOR", "COORDENADOR_PAROQUIAL")
                .requestMatchers(HttpMethod.DELETE, "/api/**")
                .hasAnyRole("COORDENADOR", "COORDENADOR_PAROQUIAL")

                .anyRequest().authenticated()
        }

        return http.build()
    }
}
