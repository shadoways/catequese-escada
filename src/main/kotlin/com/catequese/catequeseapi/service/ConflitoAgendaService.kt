package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.dto.ConflitoDTO
import com.catequese.catequeseapi.model.Evento
import com.catequese.catequeseapi.model.NivelEvento
import com.catequese.catequeseapi.model.SituacaoEvento
import com.catequese.catequeseapi.repository.ComunidadeRepository
import com.catequese.catequeseapi.repository.EventoRepository
import com.catequese.catequeseapi.repository.TurmaRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Descobre se dois eventos brigam pelo mesmo dia.
 *
 * A regra NAO e "um evento por dia". Uma paroquia com quatro comunidades tem
 * varias coisas acontecendo no mesmo domingo, e isso e normal -- travar tudo
 * deixaria o sistema inutil na primeira semana. O que caracteriza conflito e
 * o PUBLICO se sobrepor: as mesmas pessoas sendo chamadas para dois lugares
 * ao mesmo tempo.
 *
 * O alcance de cada nivel:
 *
 *   DIOCESANO / REGIONAL / PAROQUIAL -> a paroquia inteira
 *   COMUNIDADE X                     -> a comunidade X e as turmas dela
 *   TURMA T                          -> so a turma T
 *
 * Daí sai a tabela de sobreposicao:
 *
 *   paroquial-ou-acima x qualquer coisa .......... conflita (pega todo mundo)
 *   comunidade X       x comunidade X ............ conflita
 *   comunidade X       x comunidade Y ............ nao conflita
 *   comunidade X       x turma da comunidade X ... conflita
 *   comunidade X       x turma de outra .......... nao conflita
 *   turma T            x turma T ................. conflita
 *   turma T            x turma U ................. nao conflita
 *
 * Evento CANCELADO nunca conflita: ele continua na agenda para quem olha o
 * mes entender que aquilo foi desmarcado, mas nao ocupa mais ninguem.
 */
@Service
class ConflitoAgendaService(
    private val eventoRepository: EventoRepository,
    private val turmaRepository: TurmaRepository,
    private val comunidadeRepository: ComunidadeRepository
) {

    /**
     * Eventos que disputam o mesmo publico do candidato no mesmo dia.
     *
     * @param ignorarId o proprio evento, quando se esta editando -- sem isso
     *   toda edicao acusaria conflito consigo mesma.
     */
    fun conflitosDe(candidato: Evento, ignorarId: Long? = null): List<ConflitoDTO> {
        val data = candidato.dataInicio ?: return emptyList()
        if (candidato.situacao == SituacaoEvento.CANCELADO) return emptyList()

        return eventoRepository.findNoPeriodo(data, data)
            .asSequence()
            .filter { it.idEvento != ignorarId }
            .filter { it.situacao != SituacaoEvento.CANCELADO }
            .filter { seSobrepoe(candidato, it) }
            .map { paraConflito(it) }
            .toList()
    }

    /** Versao para a checagem previa da tela, antes de haver evento montado. */
    fun conflitosPara(
        data: LocalDate,
        nivel: NivelEvento,
        idComunidade: Long?,
        idTurma: Long?,
        ignorarId: Long?
    ): List<ConflitoDTO> = conflitosDe(
        Evento(
            titulo = "",
            nivel = nivel,
            idComunidade = idComunidade,
            idTurma = idTurma,
            dataInicio = data
        ),
        ignorarId
    )

    // ------------------------------------------------------------------

    private fun seSobrepoe(a: Evento, b: Evento): Boolean {
        val nivelA = a.nivel ?: return false
        val nivelB = b.nivel ?: return false

        // Evento da paroquia para cima chama todo mundo: bate com qualquer um.
        if (nivelA.ehParoquialOuAcima || nivelB.ehParoquialOuAcima) return true

        val comunidadeA = comunidadeAlvo(a)
        val comunidadeB = comunidadeAlvo(b)

        // Turma sem comunidade definida ainda: sem saber de quem ela e, nao da
        // para afirmar que sobrepoe outra comunidade. Compara so turma com
        // turma -- acusar conflito por suposicao seria pior do que nao acusar,
        // porque bloquearia um cadastro legitimo sem ter como explicar por que.
        if (nivelA == NivelEvento.TURMA && nivelB == NivelEvento.TURMA) {
            return a.idTurma != null && a.idTurma == b.idTurma
        }

        if (comunidadeA == null || comunidadeB == null) return false
        return comunidadeA == comunidadeB
    }

    /** A comunidade que o evento atinge: a dele, ou a da turma dele. */
    private fun comunidadeAlvo(evento: Evento): Long? = when (evento.nivel) {
        NivelEvento.COMUNIDADE -> evento.idComunidade
        NivelEvento.TURMA -> evento.idTurma
            ?.let { turmaRepository.findById(it).orElse(null)?.idComunidade }
        else -> null
    }

    private fun paraConflito(evento: Evento) = ConflitoDTO(
        idEvento = evento.idEvento,
        titulo = evento.titulo,
        tipoRotulo = evento.tipo.rotulo,
        nivelRotulo = evento.nivel?.rotulo,
        alcance = descreverAlcance(evento),
        dataInicio = evento.dataInicio,
        horaInicio = evento.horaInicio,
        local = evento.local
    )

    /** Frase curta que responde "quem ja estava ocupado neste horario". */
    private fun descreverAlcance(evento: Evento): String = when (evento.nivel) {
        null -> "sem nível definido"

        NivelEvento.DIOCESANO, NivelEvento.REGIONAL, NivelEvento.PAROQUIAL ->
            "toda a paróquia"

        NivelEvento.COMUNIDADE -> evento.idComunidade
            ?.let { comunidadeRepository.findById(it).orElse(null)?.nome }
            ?.let { "comunidade $it" }
            ?: "uma comunidade"

        NivelEvento.TURMA -> evento.idTurma
            ?.let { turmaRepository.findById(it).orElse(null)?.nome }
            ?.let { "turma $it" }
            ?: "uma turma"
    }
}
