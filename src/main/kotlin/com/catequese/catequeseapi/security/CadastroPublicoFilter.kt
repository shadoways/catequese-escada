package com.catequese.catequeseapi.security

import com.catequese.catequeseapi.service.ChaveInscricaoService
import com.catequese.catequeseapi.service.ConfiguracaoService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Porteiro do cadastro publico. Duas checagens, nesta ordem:
 *
 * 1. O interruptor de inscricoes esta ligado?
 * 2. Quem esta enviando apresentou uma chave de inscricao valida?
 *
 * Esconder o formulario na tela nao impede nada: quem souber o endereco da API
 * ainda gravaria dados com um POST direto. Por isso a barreira fica aqui, no
 * caminho de todas as requisicoes.
 *
 * Coordenador e coordenador paroquial passam sem chave e mesmo com as
 * inscricoes encerradas -- e comum precisar incluir alguem a mao depois do
 * prazo, e a chave existe para conter o formulario publico, nao para
 * atrapalhar quem ja esta autenticado.
 *
 * O consumo do uso da chave NAO acontece aqui: quem marca o uso e o
 * InscricaoService, dentro da transacao, para que uma gravacao que falhe nao
 * gaste uma vaga. Aqui a chave e apenas conferida.
 *
 * De proposito NAO e um @Component: veja a nota em JwtAuthFilter.
 */
class CadastroPublicoFilter(
    private val configuracaoService: ConfiguracaoService,
    private val chaveInscricaoService: ChaveInscricaoService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val caminho = request.requestURI ?: ""
        val ehEnvioPublico = request.method.equals("POST", ignoreCase = true) &&
            ROTAS_DE_CADASTRO.any { caminho.contains(it) }

        if (!ehEnvioPublico || podeEditar()) {
            filterChain.doFilter(request, response)
            return
        }

        if (!configuracaoService.cadastroAberto()) {
            recusar(
                response,
                "As inscricoes estao encerradas no momento.",
                "CADASTRO_FECHADO"
            )
            return
        }

        val codigo = request.getHeader(CABECALHO_CHAVE)
        val chave = codigo?.takeIf { it.isNotBlank() }?.let { chaveInscricaoService.conferir(it) }

        if (chave == null) {
            recusar(
                response,
                "Chave de inscricao ausente ou nao encontrada. Use o link enviado pela paroquia.",
                "CHAVE_INVALIDA"
            )
            return
        }

        chave.motivoRecusa()?.let { motivo ->
            recusar(response, motivo, "CHAVE_INVALIDA")
            return
        }

        filterChain.doFilter(request, response)
    }

    private fun recusar(response: HttpServletResponse, mensagem: String, codigo: String) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"erro":"$mensagem","codigo":"$codigo"}""")
    }

    private fun podeEditar(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication ?: return false
        return auth.authorities.any { it.authority in PAPEIS_QUE_EDITAM }
    }

    private companion object {
        const val CABECALHO_CHAVE = "X-Chave-Inscricao"

        /** Envio da inscricao e upload dos anexos que a acompanham. */
        val ROTAS_DE_CADASTRO = listOf("/api/inscricoes", "/api/files")

        val PAPEIS_QUE_EDITAM = setOf("ROLE_COORDENADOR", "ROLE_COORDENADOR_PAROQUIAL")
    }
}
