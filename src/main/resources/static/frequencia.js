/*
 * Frequencia da turma -- consulta e relatorio para impressao.
 *
 * Carregada junto com script.js no index.html, entao todo nome aqui usa o
 * prefixo "freq" para nao colidir com o que ja existe no escopo global.
 *
 * Aba separada da chamada de proposito: marcar presenca e conferir
 * aproveitamento sao dois momentos diferentes do trabalho.
 *
 * A CONTA nao esta aqui. Quem apura e o backend (CalculoFrequencia +
 * FrequenciaService), que conhece as regras de cada categoria. Esta tela so
 * mostra o que veio -- se ela recalculasse qualquer coisa, um dia os dois
 * lados discordariam e ninguem saberia qual acreditar.
 */

let freqTurmas = [];
let freqDados = null;

const FREQ_ROTULO_SITUACAO = {
  REGULAR: 'Regular',
  EM_RISCO: 'Em risco',
  ABAIXO_DO_MINIMO: 'Abaixo do mínimo',
  SEM_APURACAO: 'Sem apuração',
  NAO_SE_APLICA: 'Não se aplica'
};

/** Reaproveita as tres cores que o CSS ja tem: ok, warning e error. */
const FREQ_CLASSE_SITUACAO = {
  REGULAR: 'ok',
  EM_RISCO: 'warning',
  ABAIXO_DO_MINIMO: 'error',
  SEM_APURACAO: 'neutro',
  NAO_SE_APLICA: 'neutro'
};

const FREQ_ROTULO_JANELA = {
  ANO: 'apuração por ano civil',
  SEMESTRE: 'apuração por semestre',
  ETAPA_CATECUMENATO: 'apuração por etapa do catecumenato',
  NENHUMA: 'sem controle de frequência'
};

const FREQ_ROTULO_CATEGORIA = {
  PRE_CATEQUESE: 'Pré-catequese',
  EUCARISTIA: 'Primeira Eucaristia',
  CRISMA: 'Crisma',
  ADULTOS: 'Adultos',
  CATECUMENATO: 'Catecumenato',
  PERSEVERANCA: 'Perseverança'
};

const FREQ_ROTULO_ETAPA = {
  PRE_CATECUMENATO: 'Pré-catecumenato',
  CATECUMENATO: 'Catecumenato',
  PURIFICACAO_ILUMINACAO: 'Purificação e iluminação',
  MISTAGOGIA: 'Mistagogia'
};

const freqEscape = (valor) => {
  const div = document.createElement('div');
  div.textContent = valor === null || valor === undefined ? '' : String(valor);
  return div.innerHTML;
};

const freqDataBR = (iso) => {
  if (!iso) return '';
  const p = String(iso).slice(0, 10).split('-');
  return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : '';
};

/** Sem apuracao o percentual e nulo -- e "—" diz isso melhor que "0%". */
const freqPercento = (valor) =>
  valor === null || valor === undefined ? '—' : `${valor}%`;

const freqStatus = (texto, tipo = '') => {
  const caixa = document.getElementById('freq-status');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${freqEscape(texto)}</div>` : '';
};

const freqErro = async (resposta, padrao) => {
  const corpo = await resposta.json().catch(() => null);
  return (corpo && corpo.erro) || padrao;
};

// ---- Carga inicial --------------------------------------------------------

const freqCarregarTurmas = async () => {
  const select = document.getElementById('freq-turma');
  if (!select) return;

  try {
    // Mesma origem da tela de chamada: ja vem recortada pelo papel do usuario.
    const resposta = await fetch('/api/chamada/minhas-turmas');
    if (!resposta.ok) {
      freqStatus(await freqErro(resposta, 'Não foi possível carregar as turmas.'), 'error');
      return;
    }
    freqTurmas = await resposta.json();

    if (!freqTurmas.length) {
      select.innerHTML = '<option value="">Nenhuma turma disponível</option>';
      freqStatus(
        'Nenhuma turma vinculada ao seu usuário. Peça ao coordenador paroquial ' +
        'para vincular você às turmas em que atua.',
        'warning'
      );
      return;
    }

    select.innerHTML = freqTurmas
      .map((t) => `<option value="${t.idTurma}">${freqEscape(t.nome)}</option>`)
      .join('');
  } catch (err) {
    freqStatus(`Falha de conexão ao carregar as turmas: ${err.message}`, 'error');
  }
};

// ---- Consulta -------------------------------------------------------------

const freqConsultar = async () => {
  const idTurma = (document.getElementById('freq-turma') || {}).value;
  const ano = (document.getElementById('freq-ano') || {}).value;
  if (!idTurma) {
    freqStatus('Escolha uma turma.', 'warning');
    return;
  }

  freqStatus('Consultando...', '');
  try {
    const resposta = await fetch(`/api/frequencia/turma/${idTurma}?ano=${encodeURIComponent(ano)}`);
    if (!resposta.ok) {
      freqStatus(await freqErro(resposta, 'Não foi possível consultar a frequência.'), 'error');
      freqEsconderPaineis();
      return;
    }
    freqDados = await resposta.json();
    freqStatus('', '');
    freqDesenhar();
  } catch (err) {
    freqStatus(`Falha de conexão: ${err.message}`, 'error');
    freqEsconderPaineis();
  }
};

const freqEsconderPaineis = () => {
  const resumo = document.getElementById('freq-painel-resumo');
  const lista = document.getElementById('freq-painel-lista');
  if (resumo) resumo.hidden = true;
  if (lista) lista.hidden = true;
};

// ---- Desenho --------------------------------------------------------------

const freqDesenhar = () => {
  if (!freqDados) return;
  document.getElementById('freq-painel-resumo').hidden = false;
  document.getElementById('freq-painel-lista').hidden = false;

  const d = freqDados;
  const categoria = FREQ_ROTULO_CATEGORIA[d.categoria] || 'sem categoria definida';
  const janela = FREQ_ROTULO_JANELA[d.janela] || '';

  document.getElementById('freq-resumo-titulo').textContent =
    `${d.nomeTurma} — ${d.ano}`;

  document.getElementById('freq-cabecalho').innerHTML = `
    <span class="tag">${freqEscape(categoria)}</span>
    <span class="muted">${freqEscape(janela)}</span>
    <span class="muted">Mínimo exigido: ${d.minimo}% · aviso a partir de ${d.alerta}%</span>
    <span class="muted">
      ${d.encontrosFechados} encontro(s) encerrado(s)${d.encontrosCancelados ? `, ${d.encontrosCancelados} cancelado(s)` : ''}${d.encontrosAbertos ? `, ${d.encontrosAbertos} em aberto` : ''}
    </span>
  `;

  const r = d.resumo;
  // Encontro cancelado nao entra na conta de ninguem; deixar isso a vista
  // evita a pergunta "por que o total nao bate com o calendario".
  document.getElementById('freq-contadores').innerHTML = [
    { rotulo: 'Regulares', valor: r.regulares, classe: 'ok' },
    { rotulo: 'Em risco', valor: r.emRisco, classe: 'warning' },
    { rotulo: 'Abaixo do mínimo', valor: r.abaixoDoMinimo, classe: 'error' },
    { rotulo: 'Sem apuração', valor: r.semApuracao, classe: 'neutro' },
    { rotulo: 'Não se aplica', valor: r.naoSeAplica, classe: 'neutro' }
  ]
    .filter((c) => c.valor > 0 || c.classe === 'error')
    .map((c) => `
      <div class="freq-contador">
        <strong class="status ${c.classe}">${c.valor}</strong>
        <span class="muted">${c.rotulo}</span>
      </div>
    `).join('');

  const alertas = document.getElementById('freq-alertas-turma');
  alertas.innerHTML = (d.alertas || [])
    .map((a) => `<div class="status warning">${freqEscape(a)}</div>`).join('');

  freqDesenharLista();
};

const freqFiltrar = (linhas) => {
  const filtro = (document.getElementById('freq-filtro') || {}).value || 'TODOS';
  if (filtro === 'TODOS') return linhas;
  if (filtro === 'ATENCAO') {
    return linhas.filter((l) => l.situacao === 'ABAIXO_DO_MINIMO' || l.situacao === 'EM_RISCO');
  }
  return linhas.filter((l) => l.situacao === filtro);
};

const freqLinhaHTML = (linha) => {
  const classe = FREQ_CLASSE_SITUACAO[linha.situacao] || '';
  const rotulo = FREQ_ROTULO_SITUACAO[linha.situacao] || linha.situacao;

  const periodos = (linha.periodos || []).map((p) => `
    <div class="freq-periodo">
      <span class="freq-periodo-rotulo">${freqEscape(p.rotulo)}${p.encerrado ? '' : ' (em andamento)'}</span>
      <span class="freq-periodo-numeros">
        ${freqPercento(p.percentual)} —
        ${p.presencas} presença(s) em ${p.encontrosConsiderados} encontro(s)${p.justificadas ? `, ${p.justificadas} justificada(s) fora da conta` : ''}
      </span>
      <span class="status ${FREQ_CLASSE_SITUACAO[p.situacao] || ''}">${freqEscape(FREQ_ROTULO_SITUACAO[p.situacao] || p.situacao)}</span>
    </div>
  `).join('');

  const avisos = (linha.alertas || [])
    .map((a) => `<div class="status warning">${freqEscape(a)}</div>`).join('');

  const etapa = linha.etapaAtual
    ? `<span class="tag">${freqEscape(FREQ_ROTULO_ETAPA[linha.etapaAtual] || linha.etapaAtual)}</span>`
    : '';

  // "Não conclui neste ano" é a consequência prática da regra dos adultos;
  // precisa aparecer junto do nome, não escondido num detalhe.
  const conclusao = linha.podeConcluir
    ? ''
    : '<span class="status error">Não conclui neste ano</span>';

  return `
    <details class="freq-item" data-id="${linha.idCatequisando}">
      <summary class="freq-item-topo">
        <span class="freq-item-nome">
          <button type="button" class="nome-link" data-ficha="${linha.idCatequisando}"
                  title="Abrir a ficha de ${freqEscape(linha.nome)}">${freqEscape(linha.nome)}</button>
          ${etapa}
        </span>
        <span class="freq-item-numeros">
          <strong class="freq-percentual">${freqPercento(linha.percentualAtual)}</strong>
          <span class="status ${classe}">${freqEscape(rotulo)}</span>
          ${conclusao}
        </span>
      </summary>
      <div class="freq-item-detalhe">
        ${linha.dataMatricula ? `<p class="muted">Matrícula em ${freqDataBR(linha.dataMatricula)} — a contagem começa nesta data.</p>` : ''}
        ${periodos || '<p class="muted">Nenhum período apurado para esta categoria.</p>'}
        ${avisos}
      </div>
    </details>
  `;
};

const freqDesenharLista = () => {
  if (!freqDados) return;
  const linhas = freqFiltrar(freqDados.linhas || []);
  const alvo = document.getElementById('freq-lista');

  document.getElementById('freq-contagem-lista').textContent =
    `${linhas.length} de ${(freqDados.linhas || []).length}`;

  if (!linhas.length) {
    alvo.innerHTML = '<div class="status ok">Nenhum catequisando neste filtro.</div>';
    return;
  }
  alvo.innerHTML = linhas.map(freqLinhaHTML).join('');

  alvo.querySelectorAll('[data-ficha]').forEach((botao) => {
    botao.addEventListener('click', (evento) => {
      // Sem isto o clique borbulha para o <summary> e abre/fecha o detalhe
      // junto, o que faria a lista pular sob o dedo de quem so queria a ficha.
      evento.preventDefault();
      evento.stopPropagation();
      if (window.abrirFichaCatequisando) {
        window.abrirFichaCatequisando(Number(botao.dataset.ficha), botao.textContent.trim());
      }
    });
  });
};

// ---- Impressao ------------------------------------------------------------

/*
 * O relatorio impresso e montado num container proprio, fora das abas.
 * A regra @media print que ja existia esconde .tab-content inteiro (foi
 * escrita para a ficha), entao imprimir a tela direto sairia em branco.
 *
 * Ele tambem e mais enxuto de proposito: no papel nao da para expandir
 * detalhe, entao os periodos vao todos abertos, em tabela.
 */
const freqMontarRelatorio = () => {
  if (!freqDados) return false;
  const d = freqDados;
  const linhas = freqFiltrar(d.linhas || []);
  const alvo = document.getElementById('freq-relatorio');
  if (!alvo) return false;

  const hoje = new Date().toLocaleDateString('pt-BR');
  const categoria = FREQ_ROTULO_CATEGORIA[d.categoria] || 'sem categoria definida';

  const corpo = linhas.map((l) => {
    const periodos = (l.periodos || []).map((p) =>
      `<div class="freq-rel-periodo">${freqEscape(p.rotulo)}: ${freqPercento(p.percentual)} ` +
      `(${p.presencas}/${p.encontrosConsiderados}${p.justificadas ? `, ${p.justificadas} just.` : ''})</div>`
    ).join('') || '<div class="freq-rel-periodo">—</div>';

    return `
      <tr>
        <td>${freqEscape(l.nome)}</td>
        <td class="freq-rel-num">${freqPercento(l.percentualAtual)}</td>
        <td>${freqEscape(FREQ_ROTULO_SITUACAO[l.situacao] || l.situacao)}${l.podeConcluir ? '' : ' — não conclui neste ano'}</td>
        <td>${periodos}</td>
      </tr>
    `;
  }).join('');

  alvo.innerHTML = `
    <div class="freq-rel-cabecalho">
      <h1>Frequência — ${freqEscape(d.nomeTurma)}</h1>
      <p>
        ${freqEscape(categoria)} · ${d.ano} · mínimo de ${d.minimo}% ·
        ${d.encontrosFechados} encontro(s) encerrado(s)${d.encontrosCancelados ? `, ${d.encontrosCancelados} cancelado(s) que não contam` : ''}
      </p>
      <p class="freq-rel-emissao">Emitido em ${hoje}</p>
    </div>
    <table class="freq-rel-tabela">
      <thead>
        <tr><th>Catequisando</th><th>Atual</th><th>Situação</th><th>Períodos apurados</th></tr>
      </thead>
      <tbody>${corpo}</tbody>
    </table>
    <p class="freq-rel-nota">
      Faltas justificadas saem da conta em vez de contar contra. Encontros
      cancelados não entram na apuração de ninguém.
    </p>
  `;
  return true;
};

const freqImprimir = () => {
  if (!freqMontarRelatorio()) {
    freqStatus('Consulte uma turma antes de imprimir.', 'warning');
    return;
  }
  window.print();
};

// ---- Ligações da tela -----------------------------------------------------

document.getElementById('freq-consultar')?.addEventListener('click', freqConsultar);
document.getElementById('freq-imprimir')?.addEventListener('click', freqImprimir);
document.getElementById('freq-filtro')?.addEventListener('change', freqDesenharLista);
document.getElementById('freq-turma')?.addEventListener('change', freqConsultar);

/** script.js chama isto ao entrar na aba. */
window.carregarFrequencia = async () => {
  const campoAno = document.getElementById('freq-ano');
  if (campoAno && !campoAno.value) campoAno.value = String(new Date().getFullYear());

  if (!freqTurmas.length) await freqCarregarTurmas();
  if (freqTurmas.length && !freqDados) await freqConsultar();
};
