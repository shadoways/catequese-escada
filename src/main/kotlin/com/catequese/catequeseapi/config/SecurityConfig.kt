package com.catequese.catequeseapi.config

import com.catequese.catequeseapi.repository.UsuarioRepository
import com.catequese.catequeseapi.security.CadastroPublicoFilter
import com.catequese.catequeseapi.security.JwtAuthFilter
import com.catequese.catequeseapi.security.JwtService
import com.catequese.catequeseapi.service.ChaveInscricaoService
import com.catequese.catequeseapi.service.ConfiguracaoService
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
 * app.security.enabled, padrao TRUE: as regras abaixo valem.
 *
 * O `false` continua existindo como valvula de escape. Se um erro de regra
 * trancar todo mundo para fora em producao, `export APP_SECURITY_ENABLED=false`
 * devolve o acesso na hora, sem precisar de novo build -- mas deixa a API
 * inteira aberta, entao serve so para destravar e corrigir.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val usuarioRepository: UsuarioRepository,
    private val configuracaoService: ConfiguracaoService,
    private val chaveInscricaoService: ChaveInscricaoService,
    // Padrao TRUE tambem aqui: se a linha sumir do application.properties,
    // o sistema tem de continuar fechado, nunca abrir sozinho.
    @Value("\${app.security.enabled:true}") private val securityEnabled: Boolean
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
            .addFilterBefore(
                JwtAuthFilter(jwtService, usuarioRepository, securityEnabled),
                UsernamePasswordAuthenticationFilter::class.java
            )
            // Depois do JwtAuthFilter, para ja saber quem esta chamando: quem
            // pode editar continua cadastrando com as inscricoes encerradas.
            .addFilterAfter(
                CadastroPublicoFilter(configuracaoService, chaveInscricaoService),
                JwtAuthFilter::class.java
            )
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
                "app.security.enabled=false -- a API esta ABERTA (valvula de escape). " +
                    "Volte para true assim que o problema estiver resolvido."
            )
            // Mesmo destrancado, gestao de usuarios e de chaves continua restrita:
            // deixar aberto permitiria a qualquer um criar um administrador ou
            // emitir chaves de inscricao.
            http.authorizeHttpRequests {
                it.requestMatchers(HttpMethod.GET, "/api/chaves/validar").permitAll()
                    .requestMatchers("/api/usuarios/**").hasRole("COORDENADOR_PAROQUIAL")
                    .requestMatchers("/api/chaves/**").hasRole("COORDENADOR_PAROQUIAL")
                    .requestMatchers(HttpMethod.PUT, "/api/config/**")
                    .hasRole("COORDENADOR_PAROQUIAL")
                    .anyRequest().permitAll()
            }
            return http.build()
        }

        log.info("app.security.enabled=true -- restricoes de acesso por tipo de usuario ativas.")

        http.authorizeHttpRequests { auth ->
            auth
                // ---- Publico: paginas e arquivos estaticos ----
                // As telas em si sao publicas; o que protege os dados e a API.
                // Sem token, index.html abre mas nao consegue carregar nada.
                .requestMatchers(
                    "/", "/*.html", "/*.css", "/*.js",
                    "/*.png", "/*.jpg", "/*.svg", "/*.ico",
                    "/error"
                ).permitAll()

                // ---- Publico: login e recuperacao de senha ----
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/auth/login",
                    "/api/auth/esqueci-senha",
                    "/api/auth/redefinir-senha"
                ).permitAll()

                // Trocar a propria senha vale para QUALQUER tipo logado.
                // Sem esta linha o catequista cairia na regra de escrita mais
                // abaixo e nao conseguiria trocar a propria senha.
                .requestMatchers(HttpMethod.POST, "/api/auth/trocar-senha").authenticated()

                // ---- Publico: status do cadastro (Etapa 4) ----
                .requestMatchers(HttpMethod.GET, "/api/config/cadastro").permitAll()

                // ---- Publico: a tela de cadastro e aberta ----
                // O formulario precisa das listas de turma/comunidade para montar os
                // selects, envia a inscricao inteira de uma vez e sobe os anexos.
                // Quem barra quem pode gravar e o CadastroPublicoFilter, que exige
                // uma chave de inscricao valida e o cadastro aberto.
                .requestMatchers(HttpMethod.GET, "/api/turmas", "/api/comunidades").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/inscricoes").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/files/**").permitAll()

                // A tela publica confere o codigo antes de mostrar o formulario.
                // Vem ANTES da regra de /api/chaves, senao cairia no acesso de admin.
                .requestMatchers(HttpMethod.GET, "/api/chaves/validar").permitAll()

                // ---- Somente administrador (coordenador paroquial) ----
                .requestMatchers("/api/usuarios/**").hasRole("COORDENADOR_PAROQUIAL")
                .requestMatchers("/api/chaves/**").hasRole("COORDENADOR_PAROQUIAL")
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
