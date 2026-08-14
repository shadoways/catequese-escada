package com.catequese.catequeseapi.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Liga o agendador do Spring, usado hoje pelo fechamento automatico dos
 * encontros esquecidos.
 *
 * Fica numa @Configuration propria, e nao junto do @Component agendado, porque
 * anotacao de habilitacao pertence a configuracao -- e assim, quando aparecer a
 * segunda rotina, ja existe um lugar obvio para ela.
 */
@Configuration
@EnableScheduling
class AgendamentoConfig
