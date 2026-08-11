package com.catequese.catequeseapi.security

import java.security.SecureRandom

/**
 * Regras de senha e geracao de senha provisoria.
 *
 * ATENCAO: estas regras estao espelhadas em static/senha-forte.js, que mostra a
 * conferencia enquanto o usuario digita. Mexeu aqui, mexa la tambem -- a tela e
 * so uma cortesia, a validacao que vale e esta.
 */
object PoliticaSenha {

    const val TAMANHO_MINIMO = 8

    /** Devolve a mensagem do primeiro problema encontrado, ou null se a senha servir. */
    fun validar(senha: String, username: String, email: String?): String? {
        if (senha.length < TAMANHO_MINIMO) {
            return "A senha deve ter pelo menos $TAMANHO_MINIMO caracteres."
        }
        if (senha.none { it.isUpperCase() }) {
            return "A senha deve conter pelo menos uma letra maiuscula."
        }
        if (senha.none { it.isDigit() }) {
            return "A senha deve conter pelo menos um numero."
        }
        if (senha.none { ehEspecial(it) }) {
            return "A senha deve conter pelo menos um caractere especial, como ! @ # $ % * ?"
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

    /** Qualquer coisa que nao seja letra, numero ou espaco conta como especial. */
    fun ehEspecial(c: Char): Boolean = !c.isLetterOrDigit() && !c.isWhitespace()

    private val SENHAS_OBVIAS = setOf(
        "senha@123", "senha123!", "password1!", "catequese@1", "abcd@1234",
        "qwerty@123", "12345678!", "admin@123"
    )

    private val RANDOM = SecureRandom()

    // Sem I, l, 1, O, 0: a senha provisoria costuma ser lida em voz alta ou
    // copiada a mao. Os especiais tambem sao os de digitacao mais simples.
    private const val MAIUSCULAS = "ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val MINUSCULAS = "abcdefghijkmnpqrstuvwxyz"
    private const val NUMEROS = "23456789"
    private const val ESPECIAIS = "!@#$%*?"

    /**
     * Senha provisoria para usuario novo ou reset feito pelo admin.
     * Nasce cumprindo a propria politica: uma maiuscula, uma minuscula, um
     * numero e um especial, no minimo.
     */
    fun gerarSenhaProvisoria(tamanho: Int = 12): String {
        val todos = MAIUSCULAS + MINUSCULAS + NUMEROS + ESPECIAIS

        val obrigatorios = listOf(
            sorteia(MAIUSCULAS),
            sorteia(MINUSCULAS),
            sorteia(NUMEROS),
            sorteia(ESPECIAIS)
        )
        val restante = (1..(tamanho - obrigatorios.size)).map { sorteia(todos) }

        return (obrigatorios + restante).shuffled(RANDOM).joinToString("")
    }

    private fun sorteia(alfabeto: String): Char = alfabeto[RANDOM.nextInt(alfabeto.length)]
}
