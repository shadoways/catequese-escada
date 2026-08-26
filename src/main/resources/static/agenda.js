/*
 * Agenda da catequese.
 *
 * A tela e uma linha do tempo agrupada por mes, e nao uma grade de calendario:
 * os eventos da catequese sao esparsos (poucos por mes), e numa grade a maior
 * parte do espaco fica vazia justamente escondendo o que interessa -- local,
 * quem participa e como esta a frequencia. Numa lista isso tudo cabe sem
 * clique.
 *
 * Dois eixos independentes codificados de formas diferentes:
 *   - NIVEL (de quem o evento e)  -> a cor da tarja da esquerda
 *   - TIPO  (o que o evento e)    -> o icone
 * Tentar juntar os dois na mesma cor foi o que deixou a versao anterior
 * ilegivel.
 */
(() => {
  'use strict';

  const ICONES = {
    FORMACAO: '<path d="M22 10L12 5 2 10l10 5 10-5z"/><path d="M6 12v5c3 2 9 2 12 0v-5"/>',
    SACRAMENTO: '<path d="M12 2s5 5.5 5 9a5 5 0 0 1-10 0c0-3.5 5-9 5-9z"/>',
    RITO_RICA: '<path d="M12 3v18M8 7h8"/><path d="M6 21h12"/>',
    ENCONTRO: '<rect x="3" y="4" width="18" height="17" rx="2"/><path d="M8 2v4M16 2v4M3 10h18"/>'
  };

  const MESES = [
    'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
    'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
  ];

  const DIAS_SEMANA = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];

  const NIVEIS = ['DIOCESANO', 'REGIONAL', 'PAROQUIAL', 'COMUNIDADE', 'TURMA'];
  const TIPOS = ['FORMACAO', 'SACRAMENTO', 'RITO_RICA', 'ENCONTRO'];

  const ROTULO_NIVEL = {
    DIOCESANO: 'Diocesano',
    REGIONAL: 'Regional',
    PAROQUIAL: 'Paroquial',
    COMUNIDADE: 'Comunidade',
    TURMA: 'Turma'
  };

  const ROTULO_TIPO = {
    FORMACAO: 'Formação',
    SACRAMENTO: 'Sacramento',
    RITO_RICA: 'Rito do RICA',
    ENCONTRO: 'Encontro'
  };

  let dados = null;
  let opcoes = null;
  let editando = null;
  const filtros = { nivel: null, tipo: null };

  const el = (id) => document.getElementById(id);

  const escapar = (valor) => {
    const div = document.createElement('div');
    div.textContent = valor === null || valor === undefined ? '' : String(valor);
    return div.innerHTML;
  };

  /*
   * Data vem do backend como "2026-03-08". Montar com `new Date(texto)` faria
   * o navegador interpretar como UTC e, em fuso negativo, exibir o dia
   * anterior -- um evento de domingo apareceria no sabado. Por isso a data e
   * quebrada na mao.
   */
  const partesDaData = (iso) => {
    if (!iso) return null;
    const [ano, mes, dia] = String(iso).split('-').map(Number);
    if (!ano || !mes || !dia) return null;
    return { ano, mes, dia, semana: new Date(ano, mes - 1, dia).getDay() };
  };

  const mostrarStatus = (alvo, texto, classe) => {
    const caixa = el(alvo);
    if (!caixa) return;
    caixa.innerHTML = texto
      ? `<span class="status ${classe || 'neutro'}">${escapar(texto)}</span>`
      : '';
  };

  // ------------------------------------------------------------------
  // Carregamento
  // ------------------------------------------------------------------

  const anoSelecionado = () => {
    const select = el('agenda-ano');
    const valor = select && select.value ? Number(select.value) : NaN;
    return Number.isFinite(valor) ? valor : new Date().getFullYear();
  };

  const montarAnos = () => {
    const select = el('agenda-ano');
    if (!select || select.options.length) return;

    const atual = new Date().getFullYear();
    for (let ano = atual + 1; ano >= atual - 3; ano -= 1) {
      const opcao = document.createElement('option');
      opcao.value = String(ano);
      opcao.textContent = String(ano);
      if (ano === atual) opcao.selected = true;
      select.appendChild(opcao);
    }
    select.addEventListener('change', carregarAgenda);
  };

  async function carregarAgenda() {
    montarAnos();
    montarFiltros();
    mostrarStatus('agenda-status', 'Carregando a agenda...', 'neutro');

    try {
      // As opcoes so mudam quando muda o cadastro; uma vez por sessao basta.
      if (!opcoes) {
        const respostaOpcoes = await fetch('/api/agenda/opcoes');
        if (respostaOpcoes.ok) {
          opcoes = await respostaOpcoes.json();
          prepararFormulario();
        }
      }

      const resposta = await fetch(`/api/agenda?ano=${encodeURIComponent(anoSelecionado())}`);
      if (!resposta.ok) {
        mostrarStatus('agenda-status', 'Não foi possível carregar a agenda.', 'error');
        return;
      }

      dados = await resposta.json();
      mostrarStatus('agenda-status', '');
      renderResumo();
      renderLista();
    } catch (erro) {
      mostrarStatus('agenda-status', 'Erro de conexão ao carregar a agenda.', 'error');
    }
  }

  // ------------------------------------------------------------------
  // Faixa de resumo -- responde "tem algo comigo?" antes da lista
  // ------------------------------------------------------------------

  const renderResumo = () => {
    const alvo = el('agenda-resumo');
    if (!alvo || !dados) return;

    const resumo = dados.resumo || {};
    const cartoes = [];

    if (resumo.proximoEvento) {
      const ev = resumo.proximoEvento;
      const d = partesDaData(ev.dataInicio);
      const quando = d ? `${d.dia} de ${MESES[d.mes - 1].toLowerCase()}` : 'sem data';
      cartoes.push(`
        <div class="agenda-card">
          <p class="agenda-card-rot">Próximo evento</p>
          <p class="agenda-card-val">${escapar(ev.titulo)}</p>
          <p class="agenda-card-sub">${escapar(quando)}${ev.local ? ' · ' + escapar(ev.local) : ''}</p>
        </div>`);
    }

    (resumo.formacoesEmRisco || []).forEach((freq) => {
      cartoes.push(`
        <div class="agenda-card alerta">
          <p class="agenda-card-rot">Atenção na frequência</p>
          <p class="agenda-card-val">${escapar(freq.formacaoNome)}: ${freq.percentual}%</p>
          <p class="agenda-card-sub">${freq.presencas} de ${freq.presencas + freq.faltas} encontros
            · mínimo ${freq.percentualMinimo}%</p>
        </div>`);
    });

    if (resumo.eventosDasMinhasTurmas) {
      cartoes.push(`
        <div class="agenda-card">
          <p class="agenda-card-rot">Suas turmas</p>
          <p class="agenda-card-val">${resumo.eventosDasMinhasTurmas} evento(s)</p>
          <p class="agenda-card-sub">marcados neste ano</p>
        </div>`);
    }

    alvo.innerHTML = cartoes.join('');
  };

  // ------------------------------------------------------------------
  // Filtros
  // ------------------------------------------------------------------

  const montarFiltros = () => {
    const caixaNivel = el('agenda-filtros-nivel');
    const caixaTipo = el('agenda-filtros-tipo');
    if (!caixaNivel || !caixaTipo || caixaNivel.dataset.pronto) return;

    const chip = (rotulo, valor, tone) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'agenda-chip';
      if (tone) b.style.setProperty('--tone', tone);
      b.innerHTML = (tone ? '<i class="agenda-chip-dot"></i>' : '') + escapar(rotulo);
      b.dataset.valor = valor === null ? '' : valor;
      return b;
    };

    const ligar = (caixa, valores, campo, comCor) => {
      const lista = [chip('Todos', null)].concat(
        valores.map((v) => chip(
          campo === 'nivel' ? ROTULO_NIVEL[v] : ROTULO_TIPO[v],
          v,
          comCor ? `var(--nivel-${v.toLowerCase()})` : null
        ))
      );
      lista.forEach((b) => {
        b.addEventListener('click', () => {
          filtros[campo] = b.dataset.valor || null;
          lista.forEach((outro) => outro.classList.toggle(
            'ativo',
            (outro.dataset.valor || null) === filtros[campo]
          ));
          renderLista();
        });
        caixa.appendChild(b);
      });
      lista[0].classList.add('ativo');
    };

    ligar(caixaNivel, NIVEIS, 'nivel', true);
    ligar(caixaTipo, TIPOS, 'tipo', false);
    caixaNivel.dataset.pronto = '1';
  };

  // ------------------------------------------------------------------
  // Lista
  // ------------------------------------------------------------------

  const renderLista = () => {
    const alvo = el('agenda-lista');
    if (!alvo || !dados) return;

    const eventos = (dados.eventos || []).filter((ev) => {
      if (filtros.nivel && ev.nivel !== filtros.nivel) return false;
      if (filtros.tipo && ev.tipo !== filtros.tipo) return false;
      return true;
    });

    if (!eventos.length) {
      alvo.innerHTML = `<p class="muted">Nenhum evento ${
        filtros.nivel || filtros.tipo ? 'com esse filtro' : 'neste ano'
      }.</p>`;
      return;
    }

    let mesAtual = null;
    const partes = [];

    eventos.forEach((ev) => {
      const d = partesDaData(ev.dataInicio);
      const mes = d ? d.mes : 0;

      if (mes !== mesAtual) {
        mesAtual = mes;
        const nome = d ? `${MESES[d.mes - 1]} de ${d.ano}` : 'Sem data';
        partes.push(`<p class="agenda-mes">${escapar(nome)}</p>`);
      }

      partes.push(linhaDeEvento(ev, d));
    });

    alvo.innerHTML = partes.join('');

    alvo.querySelectorAll('[data-editar]').forEach((botao) => {
      botao.addEventListener('click', () => {
        const id = Number(botao.dataset.editar);
        const evento = (dados.eventos || []).find((e) => e.idEvento === id);
        if (evento) abrirFormulario(evento);
      });
    });
  };

  const linhaDeEvento = (ev, d) => {
    const tone = ev.nivel ? `var(--nivel-${ev.nivel.toLowerCase()})` : 'var(--stroke)';
    const icone = ICONES[ev.tipo] || ICONES.ENCONTRO;

    // Quem e o dono aparece em texto, e nao so na cor: sem isso a tarja
    // exigiria decorar a legenda para significar alguma coisa.
    const contexto = [];
    if (ev.comunidadeNome) contexto.push(escapar(ev.comunidadeNome));
    if (ev.turmaNome) contexto.push(escapar(ev.turmaNome));
    if (ev.local) contexto.push(escapar(ev.local));
    if (ev.horaInicio) contexto.push(escapar(ev.horaInicio));

    let direita = `<span class="status neutro">${escapar(ev.tipoRotulo)}</span>`;

    if (ev.situacao === 'CANCELADO') {
      direita = `<span class="status error">Cancelado</span>`;
    } else if (ev.minhaFrequencia && ev.minhaFrequencia.percentual !== null) {
      const f = ev.minhaFrequencia;
      const classe = f.atingiuMinimo ? 'ok' : 'warning';
      direita = `
        <span class="status ${classe}">Você: ${f.percentual}%</span>
        <div class="agenda-barra ${f.atingiuMinimo ? '' : 'risco'}">
          <i style="width:${Math.max(0, Math.min(100, f.percentual))}%"></i>
        </div>
        <p class="agenda-freq">${f.presencas} de ${f.presencas + f.faltas}
          · mínimo ${f.percentualMinimo}%</p>`;
    }

    const lapis = ev.podeEditar
      ? `<button type="button" class="agenda-editar" data-editar="${ev.idEvento}"
           title="Editar ${escapar(ev.titulo)}">Editar</button>`
      : '';

    return `
      <div class="agenda-ev" style="--tone: ${tone}">
        <div class="agenda-ev-data">
          <span class="agenda-ev-dia">${d ? String(d.dia).padStart(2, '0') : '—'}</span>
          <span class="agenda-ev-sem">${d ? DIAS_SEMANA[d.semana] : ''}</span>
        </div>
        <div class="agenda-ev-corpo">
          <p class="agenda-ev-tit">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                 aria-hidden="true">${icone}</svg>
            ${escapar(ev.titulo)}
          </p>
          <p class="agenda-ev-meta">
            ${ev.nivelRotulo ? `<span class="agenda-ev-nivel">${escapar(ev.nivelRotulo)}</span>` : ''}
            ${contexto.length ? '<span>' + contexto.join(' · ') + '</span>' : ''}
          </p>
          ${ev.motivoCancelamento
            ? `<p class="muted">Motivo: ${escapar(ev.motivoCancelamento)}</p>` : ''}
        </div>
        <div class="agenda-ev-dir">${direita}${lapis}</div>
      </div>`;
  };

  // ------------------------------------------------------------------
  // Formulario
  // ------------------------------------------------------------------

  const preencherSelect = (id, itens, vazio) => {
    const select = el(id);
    if (!select) return;
    select.innerHTML = (vazio ? `<option value="">${escapar(vazio)}</option>` : '') +
      itens.map((o) => `<option value="${escapar(o.valor)}">${escapar(o.rotulo)}</option>`).join('');
  };

  const prepararFormulario = () => {
    if (!opcoes) return;

    const botaoNovo = el('agenda-novo');
    if (botaoNovo) botaoNovo.hidden = !opcoes.podeCriar;

    preencherSelect('agenda-f-nivel', opcoes.niveisQuePodeCriar, 'Escolha...');
    preencherSelect('agenda-f-tipo', opcoes.tipos);
    preencherSelect('agenda-f-comunidade', opcoes.comunidades, 'Escolha...');
    preencherSelect('agenda-f-turma', opcoes.turmas, 'Escolha...');
    preencherSelect('agenda-f-formacao', opcoes.formacoes, 'Escolha...');

    el('agenda-f-nivel').addEventListener('change', ajustarCamposCondicionais);
    el('agenda-f-tipo').addEventListener('change', ajustarCamposCondicionais);
    el('agenda-f-situacao').addEventListener('change', ajustarCamposCondicionais);
  };

  /*
   * Mostra so o vinculo que o nivel escolhido exige. Deixar os tres visiveis
   * convidaria a preencher comunidade num evento paroquial -- que o backend
   * recusaria depois do formulario todo preenchido.
   */
  const ajustarCamposCondicionais = () => {
    const nivel = el('agenda-f-nivel').value;
    const tipo = el('agenda-f-tipo').value;
    const situacao = el('agenda-f-situacao').value;

    el('agenda-f-comunidade-campo').hidden = nivel !== 'COMUNIDADE';
    el('agenda-f-turma-campo').hidden = nivel !== 'TURMA';
    el('agenda-f-formacao-campo').hidden = tipo !== 'FORMACAO';
    el('agenda-f-motivo-campo').hidden = situacao !== 'CANCELADO';
  };

  const abrirFormulario = (evento) => {
    editando = evento || null;

    el('agenda-form-titulo').textContent = evento ? 'Editar evento' : 'Novo evento';
    el('agenda-f-titulo').value = evento ? evento.titulo : '';
    el('agenda-f-nivel').value = evento && evento.nivel ? evento.nivel : '';
    el('agenda-f-tipo').value = evento ? evento.tipo : 'ENCONTRO';
    el('agenda-f-comunidade').value = evento && evento.idComunidade ? String(evento.idComunidade) : '';
    el('agenda-f-turma').value = evento && evento.idTurma ? String(evento.idTurma) : '';
    el('agenda-f-formacao').value = evento && evento.idFormacao ? String(evento.idFormacao) : '';
    el('agenda-f-data').value = evento && evento.dataInicio ? evento.dataInicio : '';
    el('agenda-f-data-fim').value = evento && evento.dataFim ? evento.dataFim : '';
    el('agenda-f-hora').value = evento && evento.horaInicio ? evento.horaInicio : '';
    el('agenda-f-local').value = evento && evento.local ? evento.local : '';
    el('agenda-f-situacao').value = evento ? evento.situacao : 'PREVISTO';
    el('agenda-f-motivo').value = evento && evento.motivoCancelamento ? evento.motivoCancelamento : '';
    el('agenda-f-descricao').value = evento && evento.descricao ? evento.descricao : '';

    el('agenda-excluir').hidden = !evento;
    mostrarStatus('agenda-form-erro', '');
    ajustarCamposCondicionais();

    el('agenda-form-painel').hidden = false;
    el('agenda-form-painel').scrollIntoView({ behavior: 'smooth', block: 'start' });
    el('agenda-f-titulo').focus();
  };

  const fecharFormulario = () => {
    editando = null;
    el('agenda-form-painel').hidden = true;
  };

  const corpoDoFormulario = () => {
    const numero = (id) => {
      const valor = el(id).value;
      return valor ? Number(valor) : null;
    };

    return {
      titulo: el('agenda-f-titulo').value,
      nivel: el('agenda-f-nivel').value,
      tipo: el('agenda-f-tipo').value,
      idComunidade: numero('agenda-f-comunidade'),
      idTurma: numero('agenda-f-turma'),
      idFormacao: numero('agenda-f-formacao'),
      dataInicio: el('agenda-f-data').value || null,
      dataFim: el('agenda-f-data-fim').value || null,
      horaInicio: el('agenda-f-hora').value || null,
      local: el('agenda-f-local').value || null,
      situacao: el('agenda-f-situacao').value,
      motivoCancelamento: el('agenda-f-motivo').value || null,
      descricao: el('agenda-f-descricao').value || null
    };
  };

  const salvar = async () => {
    const botao = el('agenda-salvar');
    botao.disabled = true;
    mostrarStatus('agenda-form-erro', '');

    try {
      const url = editando
        ? `/api/agenda/eventos/${editando.idEvento}`
        : '/api/agenda/eventos';

      const resposta = await fetch(url, {
        method: editando ? 'PUT' : 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(corpoDoFormulario())
      });

      if (!resposta.ok) {
        // O backend manda a razao em `erro`/`mensagem` -- mostrar isso e melhor
        // do que um "falhou" generico, porque as recusas aqui sao acionaveis
        // ("escolha a comunidade", "esse nivel nao e seu").
        let detalhe = 'Não foi possível salvar o evento.';
        try {
          const corpo = await resposta.json();
          detalhe = corpo.erro || corpo.mensagem || corpo.message || detalhe;
        } catch (ignorado) { /* resposta sem corpo JSON */ }
        mostrarStatus('agenda-form-erro', detalhe, 'error');
        return;
      }

      fecharFormulario();
      await carregarAgenda();
    } catch (erro) {
      mostrarStatus('agenda-form-erro', 'Erro de conexão ao salvar.', 'error');
    } finally {
      botao.disabled = false;
    }
  };

  const excluir = async () => {
    if (!editando) return;
    if (!window.confirm(`Excluir "${editando.titulo}" da agenda?`)) return;

    try {
      const resposta = await fetch(`/api/agenda/eventos/${editando.idEvento}`, { method: 'DELETE' });
      if (!resposta.ok) {
        mostrarStatus('agenda-form-erro', 'Não foi possível excluir o evento.', 'error');
        return;
      }
      fecharFormulario();
      await carregarAgenda();
    } catch (erro) {
      mostrarStatus('agenda-form-erro', 'Erro de conexão ao excluir.', 'error');
    }
  };

  // ------------------------------------------------------------------

  document.addEventListener('DOMContentLoaded', () => {
    const novo = el('agenda-novo');
    if (novo) novo.addEventListener('click', () => abrirFormulario(null));

    const salvarBtn = el('agenda-salvar');
    if (salvarBtn) salvarBtn.addEventListener('click', salvar);

    const cancelar = el('agenda-cancelar');
    if (cancelar) cancelar.addEventListener('click', fecharFormulario);

    const excluirBtn = el('agenda-excluir');
    if (excluirBtn) excluirBtn.addEventListener('click', excluir);
  });

  window.carregarAgenda = carregarAgenda;
})();
