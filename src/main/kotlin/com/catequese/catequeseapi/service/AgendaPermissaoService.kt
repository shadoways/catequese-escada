package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.TipoUsuario
import com.catequese.catequeseapi.model.Usuario
import com.catequese.catequeseapi.repository.TurmaRepository
import org.springframework.stereotype.Service

/**
 * Responde "este usuario pode mexer neste evento".
 *
 * A regra em uma frase: TODO mundo logado VE a agenda inteira; quem EDITA
 * depende do nivel do evento. Ver tudo e proposital -- saber que outra
 * comunidade marcou batismo no mesmo sabado e exatamente o conflito que uma
 * agenda existe para mostrar. Esconder so atrapalharia.
 *
 *   nivel                     | paroquial | coordenador          | catequista
 *   --------------------------|-----------|----------------------|--------------------
 *   DIOCESANO/REGIONAL/PAROQ. | edita     | ve                   | ve
 *   COMUNIDADE (a dele)       | edita     | edita                | ve
 *   COMUNIDADE (outra)        | edita     | ve                   | ve
 *   TURMA (turma dele)        | edita     | edita se for da com. | edita
 *   TURMA (turma de outro)    | edita     | edita se for da com. | ve
 *
 * Fica separada do AgendaService porque e a parte que nao pode errar: e a
 * unica trava real, ja que esconder botao no frontend nao impede ninguem de
 * chamar a API direto.
 */
@Service
class AgendaPermissaoService(
    private val escopo: EscopoAcessoService,
    private val turmaRepository: TurmaRepository
) {

    /**
     * Qualquer usuario logado enxerga a agenda inteira.
     *
     * Existe como funcao, e nao como `true` espalhado pelo codigo, porque no
     * dia em que a paroquia quiser esconder o que e de outra comunidade o
     * lugar de mexer sera um so.
     */
    fun podeVer(usuario: Usuario?): Boolean = usuario != null

    /** True se o usuario pode criar, alterar ou excluir este evento. */
    fun podeEditar(evento: Evento, usuario: Usuario? = escopo.usuarioLogado()): Boolean {
        if (usuario == null) return false

        // O coordenador paroquial e o administrador: nao ha evento fora do alcance dele.
        if (usuario.tipo == TipoUsuario.COORDENADOR_PAROQUIAL) return true

        val nivel = evento.nivel ?: return false

        return when (usuario.tipo) {
            TipoUsuario.COORDENADOR -> podeEditarComoCoordenador(evento, nivel, usuario)
            TipoUsuario.CATEQUISTA -> podeEditarComoCatequista(evento, nivel)
            TipoUsuario.COORDENADOR_PAROQUIAL -> true
        }
    }

    private fun podeEditarComoCoordenador(
        evento: Evento,
        nivel: NivelEvento,
        usuario: Usuario
    ): Boolean {
        // Diocesano, regional e paroquial sao do coordenador paroquial, e so dele.
        if (nivel.ehParoquialOuAcima) return false

        /*
         * Coordenador sem comunidade vinculada nao edita nada de comunidade.
         *
         * Isto diverge de EscopoAcessoService.comunidadesPermitidas(), que
         * trata comunidade nula como "ve todas" -- ali a regra e de LEITURA, e
         * abrir demais so mostra dado a mais. Aqui a regra e de ESCRITA: tratar
         * nulo como "todas" daria a qualquer coordenador sem vinculo o poder de
         * alterar evento de qualquer comunidade, que e o oposto do pedido.
         * Na duvida, fecha.
         */
        val comunidadeDoUsuario = usuario.idComunidade ?: return false

        return when (nivel) {
            NivelEvento.COMUNIDADE -> evento.idComunidade == comunidadeDoUsuario
            NivelEvento.TURMA -> comunidadeDaTurma(evento.idTurma) == comunidadeDoUsuario
            else -> false
        }
    }

    private fun podeEditarComoCatequista(evento: Evento, nivel: NivelEvento): Boolean {
        if (nivel != NivelEvento.TURMA) return false

        val idTurma = evento.idTurma ?: return false
        val minhasTurmas = escopo.turmasDoCatequista() ?: return false
        return idTurma in minhasTurmas
    }

    /** Null quando a turma nao existe ou ainda nao foi classificada. */
    private fun comunidadeDaTurma(idTurma: Long?): Long? {
        if (idTurma == null) return null
        return turmaRepository.findById(idTurma).orElse(null)?.idComunidade
    }

    /**
     * Niveis que este usuario consegue usar ao criar um evento.
     *
     * Alimenta o `<select>` da tela: em vez de mostrar os cinco e recusar
     * depois de preencher o formulario, a tela ja oferece so o que vai passar.
     */
    fun niveisQuePodeCriar(usuario: Usuario? = escopo.usuarioLogado()): List<NivelEvento> {
        if (usuario == null) return emptyList()

        return when (usuario.tipo) {
            TipoUsuario.COORDENADOR_PAROQUIAL -> NivelEvento.entries.toList()

            TipoUsuario.COORDENADOR ->
                if (usuario.idComunidade == null) emptyList()
                else listOf(NivelEvento.COMUNIDADE, NivelEvento.TURMA)

            TipoUsuario.CATEQUISTA -> listOf(NivelEvento.TURMA)
        }
    }
}
