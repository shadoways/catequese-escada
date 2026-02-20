package com.catequese.catequeseapi.interceptor

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

@Component
class RequestLoggingInterceptor : HandlerInterceptor {

    companion object {
        private val logger = LoggerFactory.getLogger(RequestLoggingInterceptor::class.java)
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val startTime = System.currentTimeMillis()
        request.setAttribute("startTime", startTime)

        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.info("🔵 REQUEST INICIADA")
        logger.info("   Método: ${request.method}")
        logger.info("   URL: ${request.requestURI}")
        logger.info("   Query String: ${request.queryString ?: "Nenhuma"}")
        logger.info("   Remote Host: ${request.remoteHost}")
        logger.info("   Content-Type: ${request.contentType ?: "Não informado"}")

        // Logar headers importantes
        logger.debug("   Headers:")
        request.headerNames.asIterator().forEach { headerName ->
            val headerValue = request.getHeader(headerName)
            if (headerName.lowercase() != "authorization") {
                logger.debug("      $headerName: $headerValue")
            } else {
                logger.debug("      $headerName: [REDACTED]")
            }
        }

        return true
    }

    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?
    ) {
        // Pode ser usado se necessário
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        val startTime = request.getAttribute("startTime") as? Long ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime

        val statusColor = when {
            response.status in 200..299 -> "✅"
            response.status in 300..399 -> "↗️ "
            response.status in 400..499 -> "⚠️ "
            else -> "❌"
        }

        logger.info("$statusColor RESPONSE CONCLUÍDA")
        logger.info("   Status: ${response.status}")
        logger.info("   Duração: ${duration}ms")
        logger.info("   Content-Type: ${response.contentType ?: "Não informado"}")

        if (ex != null) {
            logger.error("   ❌ Exceção: ${ex.message}", ex)
        }
        logger.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

