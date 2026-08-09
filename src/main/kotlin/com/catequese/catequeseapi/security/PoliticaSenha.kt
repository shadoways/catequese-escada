package com.catequese.catequeseapi.security

import java.security.SecureRandom

/**
 * Regras de senha e geracao de senha provisoria.
 *
 * Mantido simples de proposito: exigencias exageradas (varios simbolos, troca
 * mensal) fazem as pessoas anotarem a senha no papel, o que e pior. O que
 * realmente protege aqui e o tamanho minimo somado ao bloqueio por tentativas.
 */
object PoliticaSenha {

    const val TAMANHO_MINIMO = 8

    /** Devolve a mensagem do problema, ou null se a senha for aceitavel. */
    fun validar(senha: String, username: String, email: String?): String? {
        if (senha.length < TAMANHO_MINIMO) {
            return "A senha deve ter pelo menos $TAMANHO_MINIMO caracteres."
        }
        if (senha.none { it.isLetter() }) {
            return "A senha deve conter pelo menos uma letra."
        }
        if (senha.none { it.isDigit() }) {
            return "A senha deve conter pelo menos um numero."
        }
        if (senha.trim() != senha) {
            return "A senha nao pode comecar nem terminar com espaco."
        }
        if (senha.equals(username, ignoreCase = true)) {
            return "A senha nao pode ser igual ao nome de usuario."
        }
        if (email != null && senha.equals(email, ignoreCase = true)) {
            return "A senha nao pode ser igual ao e-mail."
        }
        if (senha.lowercase() in SENHAS_OBVIAS) {
            return "Essa senha e muito comum. Escolha outra."
        }
        return null
    }

    private val SENHAS_OBVIAS = setOf(
        "12345678", "123456789", "1234567890", "senha123", "password",
        "password1", "catequese", "catequese1", "abcd1234", "qwerty123"
    )

    private val RANDOM = SecureRandom()

    // Sem I, l, 1, O, 0: a senha provisoria costuma ser lida em voz alta ou copiada a mao.
    private const val ALFABETO = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"

    /**
     * Senha provisoria para usuario novo ou reset feito pelo admin.
     * Sempre sai com pelo menos uma letra e um numero, para passar na propria validacao.
     */
    fun gerarSenhaProvisoria(tamanho: Int = 10): String {
        val letras = ALFABETO.filter { it.isLetter() }
        val numeros = ALFABETO.filter { it.isDigit() }

        val obrigatorios = listOf(
            letras[RANDOM.nextInt(letras.length)],
            numeros[RANDOM.nextInt(numeros.length)]
        )
        val restante = (1..(tamanho - obrigatorios.size))
            .map { ALFABETO[RANDOM.nextInt(ALFABETO.length)] }

        return (obrigatorios + restante).shuffled(RANDOM).joinToString("")
    }
}
