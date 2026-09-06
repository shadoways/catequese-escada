/*
 * Consultar Catequistas -- o curriculo de formacao (tela-catequistas.md).
 *
 * Nomes com prefixo "cat" para nao colidir com script.js e as demais telas,
 * que compartilham o mesmo escopo global no index.html (mesmo criterio de
 * "cham" em chamada.js e "cfg" em configuracoes.js).
 *
 * Duas telas, nunca as duas ao mesmo tempo -- mesmo padrao da Chamada:
 *   1. Lista -- quem EscopoAcessoService ja recortou para este usuario;
 *   2. Curriculo -- o detalhe de um catequista, aberto ao clicar na lista.
 */

let catLista = [];

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
    catDesenharLista();
  } catch (err) {
    catStatus('cat-lista', `Erro de conexão: ${err.message}`, 'error');
  }
};

const catDesenharLista = () => {
  const alvo = document.getElementById('cat-lista');
  if (!alvo) return;

  const termo = (document.getElementById('cat-busca')?.value || '').trim().toLowerCase();
  const filtrada = termo ? catLista.filter((c) => c.nome.toLowerCase().includes(termo)) : catLista;

  if (!catLista.length) {
    alvo.innerHTML = '<p class="muted">Nenhum catequista visível com o seu acesso.</p>';
    return;
  }
  if (!filtrada.length) {
    alvo.innerHTML = '<p class="muted">Nenhum catequista com este nome.</p>';
    return;
  }

  alvo.innerHTML = filtrada.map((c) => `
    <button type="button" class="result-item" data-id="${c.idCatequista}">
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
    botao.addEventListener('click', () => catAbrirCurriculo(Number(botao.dataset.id))));
};

// ---- Tela 2: curriculo ------------------------------------------------------

const catMostrarLista = () => {
  document.getElementById('cat-tela-lista').hidden = false;
  document.getElementById('cat-tela-curriculo').hidden = true;
};

const catAbrirCurriculo = async (idCatequista) => {
  document.getElementById('cat-tela-lista').hidden = true;
  document.getElementById('cat-tela-curriculo').hidden = false;
  document.getElementById('cat-curriculo-titulo').textContent = 'Currículo';
  document.getElementById('cat-curriculo-resumo').innerHTML = '';
  document.getElementById('cat-curriculo-corpo').innerHTML = '<p class="muted">Carregando…</p>';

  try {
    const resposta = await fetch(`/api/catequistas/${idCatequista}/curriculo`);
    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      document.getElementById('cat-curriculo-corpo').innerHTML =
        `<div class="status error">${catEscape((corpo && corpo.erro) || 'Não foi possível carregar o currículo.')}</div>`;
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
  document.getElementById('cat-curriculo-titulo').textContent = `Currículo — ${c.nome}`;

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

// ---- Ligacoes ---------------------------------------------------------------

const catLigar = (id, evento, funcao) => {
  const el = document.getElementById(id);
  if (el) el.addEventListener(evento, funcao);
};

catLigar('cat-recarregar', 'click', catCarregarLista);
catLigar('cat-busca', 'input', catDesenharLista);
catLigar('cat-voltar', 'click', catMostrarLista);

// script.js chama esta funcao ao abrir a aba.
window.carregarCatequistas = async () => {
  catMostrarLista();
  await catCarregarLista();
};
