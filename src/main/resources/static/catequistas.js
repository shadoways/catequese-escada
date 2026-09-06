/*
 * Consultar Catequistas -- historico de formacao (tela-catequistas.md).
 *
 * Nomes com prefixo "cat" para nao colidir com script.js e as demais telas,
 * que compartilham o mesmo escopo global no index.html (mesmo criterio de
 * "cham" em chamada.js e "cfg" em configuracoes.js).
 *
 * Duas telas, nunca as duas ao mesmo tempo -- mesmo padrao da Chamada:
 *   1. Lista -- quem EscopoAcessoService ja recortou para este usuario;
 *   2. Detalhe -- tres abas (Resumo / Conhecimentos / Formacoes) sobre UM
 *      catequista, aberto ao clicar na lista.
 *
 * "Currículo" saiu do texto que o usuario le (nao e profissao, foi pedido do
 * Gabriel) -- o titulo do detalhe agora e so o nome. Os identificadores
 * internos (endpoint /curriculo, CurriculoCatequistaService, os ids
 * cat-curriculo-*) continuam com o nome antigo de proposito: mudar rota e
 * classe Kotlin so por causa do rotulo na tela seria um refactor grande sem
 * nenhum ganho para quem usa -- so o texto visivel mudou.
 */

let catLista = [];
let catAtual = null; // { id, nome } -- o catequista aberto no detalhe agora.
let catConhecimentosCarregados = false;
let catHistorico = null; // null = ainda nao buscado para catAtual.

const CAT_SELO = {
  VERDE: 'ok',
  AMARELO: 'warning',
  VERMELHO: 'error',
  NEUTRO: 'neutro'
};

const CAT_SITUACAO_SELO = {
  PRESENTE: 'ok',
  FALTA: 'error',
  JUSTIFICADA: 'neutro'
};

const CAT_SITUACAO_ROTULO = {
  PRESENTE: 'Presente',
  FALTA: 'Faltou',
  JUSTIFICADA: 'Justificada'
};

const CAT_NIVEIS = [
  { chave: 'diocesana', rotulo: 'Formação diocesana' },
  { chave: 'regional', rotulo: 'Formação regional' },
  { chave: 'paroquial', rotulo: 'Formação paroquial' }
];

const CAT_MESES = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
];

const catEscape = (valor) => {
  const div = document.createElement('div');
  div.textContent = valor === null || valor === undefined ? '' : String(valor);
  return div.innerHTML;
};

const catDataBR = (iso) => {
  if (!iso) return '';
  const partes = String(iso).slice(0, 10).split('-');
  if (partes.length !== 3) return '';
  return `${partes[2]}/${partes[1]}/${partes[0]}`;
};

const catPercentualTexto = (percentual) =>
  percentual === null || percentual === undefined ? 'Sem apuração ainda' : `${percentual}%`;

const catStatus = (alvo, texto, tipo = '') => {
  const caixa = document.getElementById(alvo);
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${catEscape(texto)}</div>` : '';
};

// ---- Tela 1: lista ---------------------------------------------------------

const catCarregarLista = async () => {
  catStatus('cat-lista', 'Carregando os catequistas…');
  try {
    const resposta = await fetch('/api/catequistas/curriculo');
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      catStatus('cat-lista', (corpo && corpo.erro) || 'Não foi possível carregar a lista.', 'error');
      return;
    }
    catLista = await resposta.json();
    catPopularFiltroComunidade();
    catDesenharLista();
  } catch (err) {
    catStatus('cat-lista', `Erro de conexão: ${err.message}`, 'error');
  }
};

// As comunidades vem da propria lista ja carregada -- mesmo espirito da
// busca por nome, sem chamada nova a API so para montar um filtro.
const catPopularFiltroComunidade = () => {
  const select = document.getElementById('cat-filtro-comunidade');
  if (!select) return;
  const atual = select.value;

  const comunidades = [...new Set(catLista.map((c) => c.comunidade).filter(Boolean))]
    .sort((a, b) => a.localeCompare(b, 'pt-BR'));

  select.innerHTML = '<option value="">Todas as comunidades</option>'
    + comunidades.map((nome) => `<option value="${catEscape(nome)}">${catEscape(nome)}</option>`).join('');

  // Preserva a escolha se ela continuar existindo na lista recarregada.
  if (comunidades.includes(atual)) select.value = atual;
};

const catDesenharLista = () => {
  const alvo = document.getElementById('cat-lista');
  if (!alvo) return;

  const termo = (document.getElementById('cat-busca')?.value || '').trim().toLowerCase();
  const comunidade = document.getElementById('cat-filtro-comunidade')?.value || '';

  const filtrada = catLista.filter((c) => {
    if (termo && !c.nome.toLowerCase().includes(termo)) return false;
    if (comunidade && c.comunidade !== comunidade) return false;
    return true;
  });

  if (!catLista.length) {
    alvo.innerHTML = '<p class="muted">Nenhum catequista visível com o seu acesso.</p>';
    return;
  }
  if (!filtrada.length) {
    alvo.innerHTML = '<p class="muted">Nenhum catequista com estes filtros.</p>';
    return;
  }

  alvo.innerHTML = filtrada.map((c) => `
    <button type="button" class="result-item" data-id="${c.idCatequista}" data-nome="${catEscape(c.nome)}">
      <span>
        <span class="nome">${catEscape(c.nome)}</span>
        <span class="meta">${catEscape(c.comunidade || 'Sem comunidade')} · ${catEscape(c.ano)}</span>
      </span>
      <span class="status ${CAT_SELO[c.estado] || 'neutro'}">
        ${catPercentualTexto(c.percentual)} · ${catEscape(c.estadoRotulo)}
      </span>
    </button>
  `).join('');

  alvo.querySelectorAll('[data-id]').forEach((botao) =>
    botao.addEventListener('click', () => catAbrirCatequista(Number(botao.dataset.id), botao.dataset.nome)));
};

// ---- Tela 2: detalhe (Resumo / Conhecimentos / Formações) ------------------

const catMostrarLista = () => {
  document.getElementById('cat-tela-lista').hidden = false;
  document.getElementById('cat-tela-curriculo').hidden = true;
};

const catMostrarVista = (qual) => {
  document.getElementById('cat-vista-resumo').hidden = qual !== 'resumo';
  document.getElementById('cat-vista-conhecimentos').hidden = qual !== 'conhecimentos';
  document.getElementById('cat-vista-formacoes').hidden = qual !== 'formacoes';
  document.querySelectorAll('#cat-tela-curriculo .adm-subnav-btn').forEach((b) => {
    b.classList.toggle('active', b.dataset.catVista === qual);
  });

  // Carrega sob demanda -- so busca conhecimentos/formacoes quando a aba e
  // aberta, e so uma vez por catequista (catConhecimentosCarregados /
  // catHistorico ficam nulos de novo em catAbrirCatequista).
  if (qual === 'conhecimentos' && !catConhecimentosCarregados) catCarregarConhecimentos();
  if (qual === 'formacoes' && catHistorico === null) catCarregarHistorico();
};

const catAbrirCatequista = async (idCatequista, nome) => {
  catAtual = { id: idCatequista, nome };
  catConhecimentosCarregados = false;
  catHistorico = null;

  document.getElementById('cat-tela-lista').hidden = true;
  document.getElementById('cat-tela-curriculo').hidden = false;
  // O nome ja vem da lista -- nao precisa esperar o fetch para titular a tela,
  // e o titulo agora e SO o nome (sem a palavra "Currículo": não é profissão).
  document.getElementById('cat-curriculo-titulo').textContent = nome || 'Catequista';
  document.getElementById('cat-curriculo-resumo').innerHTML = '';
  document.getElementById('cat-curriculo-corpo').innerHTML = '<p class="muted">Carregando…</p>';
  document.getElementById('cat-conhecimentos-lista').innerHTML = '';
  document.getElementById('cat-conhecimentos-status').innerHTML = '';
  document.getElementById('cat-formacoes-lista').innerHTML = '';
  document.getElementById('cat-formacoes-status').innerHTML = '';
  catMostrarVista('resumo');

  try {
    const resposta = await fetch(`/api/catequistas/${idCatequista}/curriculo`);
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      document.getElementById('cat-curriculo-corpo').innerHTML =
        `<div class="status error">${catEscape((corpo && corpo.erro) || 'Não foi possível carregar os dados do catequista.')}</div>`;
      return;
    }
    catDesenharCurriculo(await resposta.json());
  } catch (err) {
    document.getElementById('cat-curriculo-corpo').innerHTML =
      `<div class="status error">Erro de conexão: ${catEscape(err.message)}</div>`;
  }
};

const catFormacaoCard = (f) => {
  const encontros = f.encontros.length
    ? `<div class="cat-encontros">
         ${f.encontros.map((e) => `
           <span class="status ${CAT_SITUACAO_SELO[e.situacao] || 'neutro'}"
                 title="${e.justificativa ? catEscape(e.justificativa) : ''}">
             ${catDataBR(e.data)} · ${catEscape(CAT_SITUACAO_ROTULO[e.situacao] || e.situacao)}
           </span>`).join('')}
       </div>`
    : '<p class="muted">Nenhum encontro realizado ainda.</p>';

  return `
    <div class="cat-formacao">
      <div class="row" style="justify-content: space-between;">
        <strong>${catEscape(f.nome)}${f.ano ? ` (${catEscape(f.ano)})` : ''}</strong>
        <label class="cat-conhecimento">
          <input type="checkbox" disabled ${f.atingiuMinimo ? 'checked' : ''} />
          Tem o conhecimento
        </label>
      </div>
      <p class="muted">
        ${catPercentualTexto(f.percentual)} nesta formação (mínimo ${catEscape(f.percentualMinimo)}%)
      </p>
      ${encontros}
    </div>`;
};

const catColuna = (rotulo, formacoes) => `
  <div class="cat-coluna">
    <h3>${catEscape(rotulo)}</h3>
    ${formacoes.length
      ? formacoes.map(catFormacaoCard).join('')
      : '<p class="muted">Nenhuma inscrição neste nível, este ano.</p>'}
  </div>`;

const catDesenharCurriculo = (c) => {
  document.getElementById('cat-curriculo-resumo').innerHTML = `
    <div class="row" style="justify-content: space-between; align-items: center;">
      <span class="muted">${catEscape(c.comunidade || 'Sem comunidade')} · ${catEscape(c.ano)}</span>
      <span class="status ${CAT_SELO[c.estado] || 'neutro'}">
        Aproveitamento do ano: ${catPercentualTexto(c.percentualAgregado)} · ${catEscape(c.estadoRotulo)}
        (mínimo ${catEscape(c.minimoAgregado)}%)
      </span>
    </div>`;

  const semInscricao = !c.diocesana.length && !c.regional.length && !c.paroquial.length;
  const corpo = document.getElementById('cat-curriculo-corpo');
  if (semInscricao) {
    corpo.innerHTML =
      '<p class="muted">Você não está inscrito em nenhuma formação este ano — fale com a coordenação.</p>';
    return;
  }

  corpo.innerHTML = `
    <div class="cat-colunas">
      ${CAT_NIVEIS.map((n) => catColuna(n.rotulo, c[n.chave])).join('')}
    </div>`;
};

// ---- Aba "Conhecimentos" ----------------------------------------------------

const catCarregarConhecimentos = async () => {
  if (!catAtual) return;
  const alvo = document.getElementById('cat-conhecimentos-lista');
  alvo.innerHTML = '<p class="muted">Carregando…</p>';
  document.getElementById('cat-conhecimentos-status').innerHTML = '';

  try {
    const resposta = await fetch(`/api/catequistas/${catAtual.id}/conhecimentos`);
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      alvo.innerHTML = `<div class="status error">${catEscape((corpo && corpo.erro) || 'Não foi possível carregar os conhecimentos.')}</div>`;
      return;
    }
    catConhecimentosCarregados = true;
    catDesenharConhecimentos(await resposta.json());
  } catch (err) {
    alvo.innerHTML = `<div class="status error">Erro de conexão: ${catEscape(err.message)}</div>`;
  }
};

const catDesenharConhecimentos = (checklist) => {
  const alvo = document.getElementById('cat-conhecimentos-lista');

  if (!checklist.itens.length) {
    alvo.innerHTML = '<p class="muted">Nenhum conhecimento cadastrado ainda — cadastre em Configurações.</p>';
    return;
  }

  // podeEditar vem pronto do servidor (EscopoAcessoService.ehAdmin): a tela
  // so desenha o checkbox habilitado ou nao, sem recalcular quem e coordenador
  // paroquial -- mesma regra da Chamada e da Agenda.
  alvo.innerHTML = checklist.itens.map((item) => `
    <label class="cat-conhecimento-item">
      <input type="checkbox" data-id-requisito="${item.idRequisito}"
             ${item.possui ? 'checked' : ''} ${checklist.podeEditar ? '' : 'disabled'} />
      ${catEscape(item.nome)}
    </label>
  `).join('');

  if (!checklist.podeEditar) {
    document.getElementById('cat-conhecimentos-status').innerHTML =
      '<p class="muted">Somente o coordenador paroquial pode alterar este checklist.</p>';
  }

  alvo.querySelectorAll('input[type="checkbox"]').forEach((caixa) => {
    caixa.addEventListener('change', () => catMarcarConhecimento(caixa));
  });
};

const catMarcarConhecimento = async (caixa) => {
  const idRequisito = caixa.dataset.idRequisito;
  const possui = caixa.checked;
  caixa.disabled = true;
  document.getElementById('cat-conhecimentos-status').innerHTML = '';

  try {
    const resposta = await fetch(`/api/catequistas/${catAtual.id}/conhecimentos/${idRequisito}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ possui })
    });
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      caixa.checked = !possui; // desfaz o clique -- o servidor nao aceitou.
      catStatus('cat-conhecimentos-status', (corpo && corpo.erro) || 'Não foi possível salvar.', 'error');
      return;
    }
    catStatus('cat-conhecimentos-status', 'Salvo.', 'ok');
  } catch (err) {
    caixa.checked = !possui;
    catStatus('cat-conhecimentos-status', `Erro de conexão: ${err.message}`, 'error');
  } finally {
    caixa.disabled = false;
  }
};

// ---- Aba "Formações" (histórico completo, com filtros) --------------------

let catFiltroSituacao = null; // null = Todas
const CAT_SITUACOES_FILTRO = [
  { rotulo: 'Todas', valor: null },
  { rotulo: 'Presente', valor: 'PRESENTE' },
  { rotulo: 'Faltou', valor: 'FALTA' },
  { rotulo: 'Justificada', valor: 'JUSTIFICADA' }
];

const catMontarFiltroSituacao = () => {
  const caixa = document.getElementById('cat-formacoes-filtro-situacao');
  if (!caixa || caixa.dataset.pronto) return;
  caixa.dataset.pronto = '1';

  const chips = CAT_SITUACOES_FILTRO.map(({ rotulo, valor }) => {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'agenda-chip' + (valor === null ? ' ativo' : '');
    b.textContent = rotulo;
    b.dataset.valor = valor || '';
    return b;
  });

  chips.forEach((b) => {
    b.addEventListener('click', () => {
      catFiltroSituacao = b.dataset.valor || null;
      chips.forEach((outro) => outro.classList.toggle('ativo', outro === b));
      catDesenharHistorico();
    });
    caixa.appendChild(b);
  });
};

const catPopularFiltrosPeriodo = () => {
  const selectAno = document.getElementById('cat-formacoes-filtro-ano');
  const selectMes = document.getElementById('cat-formacoes-filtro-mes');
  if (!selectAno || !selectMes) return;

  const anos = [...new Set(catHistorico.map((h) => h.ano).filter((a) => a !== null && a !== undefined))]
    .sort((a, b) => b - a);
  selectAno.innerHTML = '<option value="">Todos os anos</option>'
    + anos.map((a) => `<option value="${a}">${a}</option>`).join('');

  selectMes.innerHTML = '<option value="">Todos os meses</option>'
    + CAT_MESES.map((nome, i) => `<option value="${i + 1}">${nome}</option>`).join('');
};

const catCarregarHistorico = async () => {
  if (!catAtual) return;
  const alvo = document.getElementById('cat-formacoes-lista');
  alvo.innerHTML = '<p class="muted">Carregando…</p>';
  document.getElementById('cat-formacoes-status').innerHTML = '';
  catMontarFiltroSituacao();

  try {
    const resposta = await fetch(`/api/catequistas/${catAtual.id}/formacoes`);
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      alvo.innerHTML = `<div class="status error">${catEscape((corpo && corpo.erro) || 'Não foi possível carregar o histórico.')}</div>`;
      return;
    }
    catHistorico = await resposta.json();
    catPopularFiltrosPeriodo();
    catDesenharHistorico();
  } catch (err) {
    alvo.innerHTML = `<div class="status error">Erro de conexão: ${catEscape(err.message)}</div>`;
  }
};

const catDesenharHistorico = () => {
  const alvo = document.getElementById('cat-formacoes-lista');
  if (!catHistorico) return;

  if (!catHistorico.length) {
    alvo.innerHTML = '<p class="muted">Nenhum encontro de formação registrado para este catequista.</p>';
    return;
  }

  const ano = document.getElementById('cat-formacoes-filtro-ano')?.value || '';
  const mes = document.getElementById('cat-formacoes-filtro-mes')?.value || '';

  const filtrado = catHistorico.filter((h) => {
    if (catFiltroSituacao && h.situacao !== catFiltroSituacao) return false;
    if (ano && String(h.ano) !== ano) return false;
    if (mes && (!h.data || String(Number(h.data.slice(5, 7))) !== mes)) return false;
    return true;
  });

  if (!filtrado.length) {
    alvo.innerHTML = '<p class="muted">Nenhum encontro com estes filtros.</p>';
    return;
  }

  alvo.innerHTML = filtrado.map((h) => `
    <div class="cat-historico-linha">
      <span>
        <strong>${catEscape(h.formacaoNome)}</strong>
        <span class="meta">${catEscape(h.nivelRotulo)}${h.ano ? ` · ${catEscape(h.ano)}` : ''}</span>
      </span>
      <span class="status ${CAT_SITUACAO_SELO[h.situacao] || 'neutro'}"
            title="${h.justificativa ? catEscape(h.justificativa) : ''}">
        ${catDataBR(h.data)} · ${catEscape(CAT_SITUACAO_ROTULO[h.situacao] || h.situacao)}
      </span>
    </div>
  `).join('');
};

// ---- Ligacoes ---------------------------------------------------------------

const catLigar = (id, evento, funcao) => {
  const el = document.getElementById(id);
  if (el) el.addEventListener(evento, funcao);
};

catLigar('cat-recarregar', 'click', catCarregarLista);
catLigar('cat-busca', 'input', catDesenharLista);
catLigar('cat-filtro-comunidade', 'change', catDesenharLista);
catLigar('cat-voltar', 'click', catMostrarLista);
catLigar('cat-formacoes-filtro-ano', 'change', catDesenharHistorico);
catLigar('cat-formacoes-filtro-mes', 'change', catDesenharHistorico);

document.querySelectorAll('#cat-tela-curriculo .adm-subnav-btn').forEach((b) => {
  b.addEventListener('click', () => catMostrarVista(b.dataset.catVista));
});

// script.js chama esta funcao ao abrir a aba.
window.carregarCatequistas = async () => {
  catMostrarLista();
  await catCarregarLista();
};
