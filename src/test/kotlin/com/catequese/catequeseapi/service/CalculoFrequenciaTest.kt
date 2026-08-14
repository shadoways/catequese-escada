package com.catequese.catequeseapi.service

import com.catequese.catequeseapi.model.SituacaoFrequencia
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Teste da regra de frequencia.
 *
 * E o unico ponto do sistema em que uma conta errada tem consequencia sobre
 * uma pessoa -- alguem pode deixar de concluir a catequese por causa dela.
 * Por isso a conta foi isolada num objeto sem banco: da para cobrir as
 * fronteiras de verdade, e nao so por leitura.
 */
class CalculoFrequenciaTest {

    @Nested
    @DisplayName("apurar")
    inner class Apurar {

        @Test
        fun `sem encontro fechado nao existe percentual`() {
            val r = CalculoFrequencia.apurar(encontrosFechados = 0, presencas = 0, justificadas = 0)

            // Nem 0% (ninguem faltou) nem 100% (ninguem compareceu): sem apuracao.
            assertNull(r.percentual)
            assertEquals(SituacaoFrequencia.SEM_APURACAO, r.situacao)
            assertEquals(0, r.encontrosConsiderados)
        }

        @Test
        fun `presenca em todos os encontros da 100 por cento`() {
            val r = CalculoFrequencia.apurar(10, 10, 0)

            assertEquals(100.0, r.percentual)
            assertEquals(0, r.faltas)
            assertEquals(SituacaoFrequencia.REGULAR, r.situacao)
        }

        @Test
        fun `falta justificada sai do denominador em vez de contar contra`() {
            // 10 encontros, 2 justificadas, presente nos 8 restantes = 100%.
            val r = CalculoFrequencia.apurar(encontrosFechados = 10, presencas = 8, justificadas = 2)

            assertEquals(8, r.encontrosConsiderados)
            assertEquals(0, r.faltas)
            assertEquals(100.0, r.percentual)
            assertEquals(SituacaoFrequencia.REGULAR, r.situacao)
        }

        @Test
        fun `justificada nao vira presenca`() {
            // 10 encontros, 2 justificadas, presente em 6 dos 8 restantes = 75%.
            val r = CalculoFrequencia.apurar(10, 6, 2)

            assertEquals(8, r.encontrosConsiderados)
            assertEquals(2, r.faltas)
            assertEquals(75.0, r.percentual)
            assertEquals(SituacaoFrequencia.ABAIXO_DO_MINIMO, r.situacao)
        }

        @Test
        fun `tudo justificado volta a ficar sem apuracao`() {
            val r = CalculoFrequencia.apurar(encontrosFechados = 4, presencas = 0, justificadas = 4)

            assertEquals(0, r.encontrosConsiderados)
            assertNull(r.percentual)
            assertEquals(SituacaoFrequencia.SEM_APURACAO, r.situacao)
        }

        @Test
        fun `exatamente no minimo ainda nao reprova mas avisa`() {
            // 8 de 10 = 80%. A regra e "abaixo de 80", entao 80 cumpre --
            // mas fica no limite, e o aviso preventivo existe para isso.
            val r = CalculoFrequencia.apurar(10, 8, 0)

            assertEquals(80.0, r.percentual)
            assertEquals(SituacaoFrequencia.EM_RISCO, r.situacao)
            assertTrue(r.situacao.atingiuMinimo)
        }

        @Test
        fun `um ponto abaixo do minimo reprova`() {
            val r = CalculoFrequencia.apurar(100, 79, 0)

            assertEquals(79.0, r.percentual)
            assertEquals(SituacaoFrequencia.ABAIXO_DO_MINIMO, r.situacao)
            assertTrue(!r.situacao.atingiuMinimo)
        }

        @Test
        fun `fronteira do aviso preventivo`() {
            // alerta padrao = 85: 84% avisa, 85% ja e regular.
            assertEquals(SituacaoFrequencia.EM_RISCO, CalculoFrequencia.apurar(100, 84, 0).situacao)
            assertEquals(SituacaoFrequencia.REGULAR, CalculoFrequencia.apurar(100, 85, 0).situacao)
        }

        @Test
        fun `alerta configuravel muda a fronteira do aviso`() {
            val semAviso = CalculoFrequencia.apurar(100, 84, 0, minimo = 80, alerta = 80)
            assertEquals(SituacaoFrequencia.REGULAR, semAviso.situacao)

            val avisoAlto = CalculoFrequencia.apurar(100, 89, 0, minimo = 80, alerta = 90)
            assertEquals(SituacaoFrequencia.EM_RISCO, avisoAlto.situacao)
        }

        @Test
        fun `percentual quebrado vem com uma casa decimal`() {
            // 5 de 6 = 83,3333... Sem arredondar, a tela mostraria 83.33333333333333.
            val r = CalculoFrequencia.apurar(6, 5, 0)
            assertEquals(83.3, r.percentual)
        }

        @Test
        fun `dado incoerente nao produz falta negativa`() {
            // Nao deveria acontecer, mas se acontecer o relatorio nao pode
            // mostrar "-2 faltas" para o catequista.
            val r = CalculoFrequencia.apurar(encontrosFechados = 5, presencas = 8, justificadas = 0)

            assertEquals(0, r.faltas)
            assertTrue(r.percentual!! >= 100.0)
        }

        @Test
        fun `mais justificadas do que encontros nao quebra a conta`() {
            val r = CalculoFrequencia.apurar(encontrosFechados = 2, presencas = 0, justificadas = 5)

            assertEquals(0, r.encontrosConsiderados)
            assertEquals(SituacaoFrequencia.SEM_APURACAO, r.situacao)
        }
    }

    @Nested
    @DisplayName("periodos")
    inner class Periodos {

        @Test
        fun `ano civil vai de primeiro de janeiro a trinta e um de dezembro`() {
            val p = CalculoFrequencia.anoCivil(2026)

            assertEquals(LocalDate.of(2026, 1, 1), p.inicio)
            assertEquals(LocalDate.of(2026, 12, 31), p.fim)
        }

        @Test
        fun `semestres cobrem o ano inteiro sem sobreposicao`() {
            val primeiro = CalculoFrequencia.semestre(2026, 1)
            val segundo = CalculoFrequencia.semestre(2026, 2)

            assertEquals(LocalDate.of(2026, 1, 1), primeiro.inicio)
            assertEquals(LocalDate.of(2026, 6, 30), primeiro.fim)
            assertEquals(LocalDate.of(2026, 7, 1), segundo.inicio)
            assertEquals(LocalDate.of(2026, 12, 31), segundo.fim)
            assertEquals(primeiro.fim.plusDays(1), segundo.inicio)
        }

        @Test
        fun `junho e primeiro semestre e julho e segundo`() {
            assertEquals(1, CalculoFrequencia.semestreDe(LocalDate.of(2026, 6, 30)))
            assertEquals(2, CalculoFrequencia.semestreDe(LocalDate.of(2026, 7, 1)))
            assertEquals(1, CalculoFrequencia.semestreDe(LocalDate.of(2026, 1, 1)))
            assertEquals(2, CalculoFrequencia.semestreDe(LocalDate.of(2026, 12, 31)))
        }
    }

    @Nested
    @DisplayName("inicio efetivo pela data de matricula")
    inner class InicioEfetivo {

        private val ano = CalculoFrequencia.anoCivil(2026)

        @Test
        fun `sem data de matricula vale o inicio do periodo`() {
            assertEquals(ano.inicio, CalculoFrequencia.inicioEfetivo(ano, null))
        }

        @Test
        fun `quem entrou em abril nao responde por fevereiro e marco`() {
            val abril = LocalDate.of(2026, 4, 10)
            assertEquals(abril, CalculoFrequencia.inicioEfetivo(ano, abril))
        }

        @Test
        fun `matricula anterior ao periodo nao antecipa a contagem`() {
            // Matriculado em 2025, apurando 2026: conta desde 1o de janeiro.
            val antiga = LocalDate.of(2025, 3, 1)
            assertEquals(ano.inicio, CalculoFrequencia.inicioEfetivo(ano, antiga))
        }

        @Test
        fun `matricula no proprio dia de inicio nao muda nada`() {
            assertEquals(ano.inicio, CalculoFrequencia.inicioEfetivo(ano, ano.inicio))
        }

        @Test
        fun `no segundo semestre quem entrou em agosto conta de agosto`() {
            val segundo = CalculoFrequencia.semestre(2026, 2)
            val agosto = LocalDate.of(2026, 8, 3)

            assertEquals(agosto, CalculoFrequencia.inicioEfetivo(segundo, agosto))
            // E no primeiro semestre esse mesmo aluno comeca... depois do fim.
            // Quem trata isso e o servico, filtrando encontros do periodo: nao
            // sobra nenhum, e o resultado e SEM_APURACAO.
            val primeiro = CalculoFrequencia.semestre(2026, 1)
            assertTrue(CalculoFrequencia.inicioEfetivo(primeiro, agosto).isAfter(primeiro.fim))
        }
    }

    @Nested
    @DisplayName("situacao consolidada")
    inner class Consolidada {

        @Test
        fun `a pior situacao e a que vale`() {
            val pior = SituacaoFrequencia.pior(
                listOf(SituacaoFrequencia.REGULAR, SituacaoFrequencia.ABAIXO_DO_MINIMO)
            )
            assertEquals(SituacaoFrequencia.ABAIXO_DO_MINIMO, pior)
        }

        @Test
        fun `ir bem no segundo semestre nao apaga o primeiro`() {
            val primeiro = CalculoFrequencia.apurar(20, 10, 0).situacao
            val segundo = CalculoFrequencia.apurar(20, 20, 0).situacao

            assertEquals(SituacaoFrequencia.ABAIXO_DO_MINIMO, primeiro)
            assertEquals(SituacaoFrequencia.REGULAR, segundo)
            assertEquals(
                SituacaoFrequencia.ABAIXO_DO_MINIMO,
                SituacaoFrequencia.pior(listOf(primeiro, segundo))
            )
        }

        @Test
        fun `lista vazia nao quebra`() {
            assertEquals(SituacaoFrequencia.NAO_SE_APLICA, SituacaoFrequencia.pior(emptyList()))
        }

        @Test
        fun `sem apuracao nao conta como minimo atingido`() {
            assertTrue(!SituacaoFrequencia.SEM_APURACAO.atingiuMinimo)
            assertTrue(!SituacaoFrequencia.NAO_SE_APLICA.atingiuMinimo)
            assertTrue(SituacaoFrequencia.REGULAR.atingiuMinimo)
            assertTrue(SituacaoFrequencia.EM_RISCO.atingiuMinimo)
        }
    }
}
