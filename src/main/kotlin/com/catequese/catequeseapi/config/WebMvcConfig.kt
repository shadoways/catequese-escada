package com.catequese.catequeseapi.config

import com.catequese.catequeseapi.interceptor.RequestLoggingInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(private val requestLoggingInterceptor: RequestLoggingInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(requestLoggingInterceptor)
            .addPathPatterns("/api/**")
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        // Permitir frontend em desenvolvimento e produção
        val frontendUrls = listOf(
            "http://localhost:5173",           // Desenvolvimento local (Vite)
            "http://localhost:3000",           // Desenvolvimento (Create React App)
            "https://seu-dominio-frontend.com" // Produção (substitua pela URL real)
        ).filter { it.isNotEmpty() }

        registry.addMapping("/api/**")
            .allowedOrigins(*frontendUrls.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)

        // Também permitir acesso às rotas públicas
        registry.addMapping("/**")
            .allowedOrigins(*frontendUrls.toTypedArray())
            .allowedMethods("GET", "HEAD", "OPTIONS")
            .maxAge(3600)
    }
}

