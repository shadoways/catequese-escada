/*
 * Chamada dos encontros -- a tela de trabalho do catequista.
 *
 * Carregada junto com script.js no index.html, entao todo nome aqui usa o
 * prefixo "cham" para nao colidir com o que ja existe no escopo global.
 *
 * Sao duas telas, nunca as duas ao mesmo tempo:
 *   1. Minhas turmas  -- onde ele escolhe a turma;
 *   2. Chamada do dia -- onde ele marca presenca e encerra.
 *
 * A separacao foi pedida: o catequista abre o sistema para fazer uma coisa so,
 * e misturar a lista de turmas com a lista de presenca deixaria a tela pesada
 * justamente no momento em que ele esta com a turma na frente dele.
 *
 * Quem barra de verdade e o backend. Aqui as regras aparecem cedo so para o
 * catequista nao descobrir no envio que perdeu o trabalho: o botao de encerrar
 * ja avisa que depois nao da para mudar, e a falta justificada ja pede o
 * motivo na hora de marcar.
 */

let chamTurmas = [];
let chamTurmaAtual = null;
let chamEncontroAtual = null;
/** idCatequisando -> {situacao, justificativa}. O que esta na tela, ainda nao salvo. */
let chamMarcacoes = new Map();

const CHAM_SITUACOES = [
  { valor: 'PRESENTE', rotulo: 'Presente', curto: 'P' },
  { valor: 'FALTA', rotulo: 'Falta', curto: 'F' },
  { valor: 'JUSTIFICADA', rotulo: 'Justificada', curto: 'J' }
];

const CHAM_ROTULO_CATEGORIA = {
  PRE_CATEQUESE: 'Pré-catequese',
  EUCARISTIA: 'Primeira Eucaristia',
  CRISMA: 'Crisma',
  ADULTOS: 'Adultos',
  CATECUMENATO: 'Catecumenato',
  PERSEVERANCA: 'Perseverança'
};

const chamEscape = (valor) => {
  const div = document.createElement('div');
  div.textContent = valor === null || valor === undefined ? '' : String(valor);
  return div.innerHTML;
};

const chamDataBR = (iso) => {
  if (!iso) return '';
  const partes = String(iso).slice(0, 10).split('-');
  if (partes.length !== 3) return '';
  return `${partes[2]}/${partes[1]}/${partes[0]}`;
};

/** Data de hoje no formato que o <input type="date"> espera. */
const chamHojeISO = () => {
  const agora = new Date();
  const mes = String(agora.getMonth() + 1).padStart(2, '0');
  const dia = String(agora.getDate()).padStart(2, '0');
  return `${agora.getFullYear()}-${mes}-${dia}`;
};

const chamStatus = (texto, tipo = '') => {
  const caixa = document.getElementById('cham-encontro-estado');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${chamEscape(texto)}</div>` : '';
};

const chamAvisoTurmas = (texto, tipo = '') => {
  const caixa = document.getElementById('cham-turmas-lista');
  if (caixa) caixa.innerHTML = `<div class="status ${tipo}">${chamEscape(texto)}</div>`;
};

/** O backend responde sempre {erro: "..."}; sem isso o usuario veria "[object Object]". */
const chamErro = async (resposta, padrao) => {
  const corpo = await resposta.json().catch(() => null);
  return (corpo && corpo.erro) || padrao;
};

// ---- Tela 1: minhas turmas ------------------------------------------------

const chamMostrarTurmas = () => {
  document.getElementById('cham-tela-turmas').hidden = false;
  document.getElementById('cham-tela-eventos').hidden = false;
  document.getElementById('cham-tela-encontro').hidden = true;
  chamTurmaAtual = null;
  chamEncontroAtual = null;
  chamMarcacoes = new Map();
};

const chamCartaoTurma = (turma) => {
  const categoria = CHAM_ROTULO_CATEGORIA[turma.categoria] || null;
  const etapa = turma.etapa ? ` ${'I'.repeat(Math.min(turma.etapa, 3))}` : '';

  // O estado da turma e a informacao mais util do cartao: decide se o
  // catequista continua uma chamada ou comeca outra.
  let estado;
  let classeEstado;
  if (turma.encontroAberto) {
    estado = `Chamada em aberto de ${chamDataBR(turma.encontroAberto.data)}`;
    classeEstado = 'warning';
  } else if (turma.ultimoEncontro) {
    estado = `Último encontro: ${chamDataBR(turma.ultimoEncontro)}`;
    classeEstado = 'ok';
  } else {
    estado = 'Nenhum encontro registrado ainda';
    classeEstado = 'neutro';
  }

  const aviso = turma.categoria
    ? ''
    : '<p class="muted">Turma sem categoria definida: a frequência ainda não é apurada.</p>';

  return `
    <button type="button" class="turma-chamada-card" data-id-turma="${turma.idTurma}">
      <span class="turma-chamada-topo">
        <strong>${chamEscape(turma.nome)}</strong>
        ${categoria ? `<span class="tag">${chamEscape(categoria + etapa)}</span>` : ''}
      </span>
      <span class="muted">${turma.matriculados} matriculado(s) em ${turma.ano}</span>
      <span class="status ${classeEstado}">${chamEscape(estado)}</span>
      ${aviso}
      <span class="menu-card-cta">${turma.encontroAberto ? 'Continuar chamada' : 'Fazer chamada'} →</span>
    </button>
  `;
};

const chamCarregarTurmas = async () => {
  const alvo = document.getElementById('cham-turmas-lista');
  if (!alvo) return;
  alvo.innerHTML = '<p class="muted">Carregando suas turmas...</p>';

  try {
    const resposta = await fetch('/api/chamada/minhas-turmas');
    if (!resposta.ok) {
      chamAvisoTurmas(await chamErro(resposta, 'Não foi possível carregar as turmas.'), 'error');
      return;
    }

    chamTurmas = await resposta.json();
    if (!chamTurmas.length) {
      // Diferenciar "nao tem turma" de "erro" evita chamado desnecessario:
      // o caso comum e o vinculo do catequista ainda nao ter sido feito.
      chamAvisoTurmas(
        'Nenhuma turma vinculada ao seu usuário. Peça ao coordenador paroquial ' +
        'para vincular você às turmas em que atua.',
        'warning'
      );
      return;
    }

    alvo.innerHTML = chamTurmas.map(chamCartaoTurma).join('');
    alvo.querySelectorAll('[data-id-turma]').forEach((card) => {
      card.addEventListener('click', () => chamAbrirTurma(Number(card.dataset.idTurma)));
    });
  } catch (err) {
    chamAvisoTurmas(`Falha de conexão ao carregar as turmas: ${err.message}`, 'error');
  }
};

// ---- Tela 2: chamada do dia ----------------------------------------------

const chamAbrirTurma = async (idTurma) => {
  chamTurmaAtual = chamTurmas.find((t) => t.idTurma === idTurma) || null;
  if (!chamTurmaAtual) return;

  document.getElementById('cham-tela-turmas').hidden = true;
  document.getElementById('cham-tela-eventos').hidden = true;
  document.getElementById('cham-tela-encontro').hidden = false;
  document.getElementById('cham-encontro-titulo').textContent =
    `Chamada — ${chamTurmaAtual.nome}`;

  const campoData = document.getElementById('cham-data');
  if (campoData) {
    campoData.value = chamHojeISO();
    // Encontro futuro o backend recusa; o campo ja impede de tentar.
    campoData.max = chamHojeISO();
  }
  const campoTema = document.getElementById('cham-tema');
  if (campoTema) campoTema.value = '';

  if (chamTurmaAtual.encontroAberto) {
    await chamCarregarChamada(chamTurmaAtual.encontroAberto.idEncontro);
  } else {
    chamModoAbertura();
  }
};

const chamModoAbertura = () => {
  chamEncontroAtual = null;
  chamMarcacoes = new Map();
  document.getElementById('cham-abrir').hidden = false;
  document.getElementById('cham-lista-area').hidden = true;
  chamStatus(
    'Nenhuma chamada em aberto nesta turma. Confirme a data e abra o encontro.',
    ''
  );
};

const chamCarregarChamada = async (idEncontro) => {
  chamStatus('Carregando a lista...', '');
  try {
    const resposta = await fetch(`/api/chamada/encontro/${idEncontro}`);
    if (!resposta.ok) {
      chamStatus(await chamErro(resposta, 'Não foi possível carregar a chamada.'), 'error');
      return;
    }
    chamAplicarChamada(await resposta.json());
  } catch (err) {
    chamStatus(`Falha de conexão: ${err.message}`, 'error');
  }
};

const chamAplicarChamada = (dados) => {
  chamEncontroAtual = dados.encontro;
  chamMarcacoes = new Map();
  dados.itens.forEach((item) => {
    if (item.situacao) {
      chamMarcacoes.set(item.idCatequisando, {
        situacao: item.situacao,
        justificativa: item.justificativa || ''
      });
    }
  });

  document.getElementById('cham-abrir').hidden = true;
  document.getElementById('cham-lista-area').hidden = false;
  chamDesenharItens(dados.itens);

  const e = chamEncontroAtual;
  if (e.editavel) {
    chamStatus(
      `Encontro de ${chamDataBR(e.data)} em aberto. ` +
      'Marque as presenças e clique em Encerrar chamada ao final.',
      ''
    );
  } else {
    chamStatus(
      `Encontro de ${chamDataBR(e.data)} já encerrado. A lista não aceita mais alteração.`,
      'warning'
    );
  }
  chamAtualizarControles();
};

const chamDesenharItens = (itens) => {
  const alvo = document.getElementById('cham-itens');
  if (!alvo) return;

  if (!itens.length) {
    alvo.innerHTML =
      '<div class="status warning">Nenhum catequisando matriculado nesta turma neste ano. ' +
      'A matrícula é feita pelo coordenador paroquial.</div>';
    return;
  }

  alvo.innerHTML = itens.map((item) => {
    const marcado = chamMarcacoes.get(item.idCatequisando);
    const atual = marcado ? marcado.situacao : null;
    const justificativa = marcado ? marcado.justificativa : '';

    const botoes = CHAM_SITUACOES.map((s) => `
      <button type="button"
              class="cham-opcao cham-opcao--${s.valor.toLowerCase()}${atual === s.valor ? ' ativa' : ''}"
              data-marcar="${item.idCatequisando}" data-situacao="${s.valor}"
              aria-pressed="${atual === s.valor}"
              title="${s.rotulo}"
              aria-label="${s.rotulo} — ${chamEscape(item.nome)}">
        <span class="cham-opcao-curto" aria-hidden="true">${s.curto}</span>
        <span class="cham-opcao-rotulo">${s.rotulo}</span>
      </button>
    `).join('');

    // Quem marcou fica a vista: era pedido explicito haver controle de quem
    // lancou a presenca, e escondido num log ninguem confere.
    const autoria = item.marcadoPor
      ? `<span class="cham-autoria">marcado por ${chamEscape(item.marcadoPor)}</span>`
      : '';

    return `
      <div class="chamada-item" data-linha="${item.idCatequisando}">
        <div class="chamada-item-nome">
          <strong>${chamEscape(item.nome)}</strong>
          ${autoria}
        </div>
        <div class="chamada-item-opcoes">${botoes}</div>
        <input type="text" class="cham-justificativa" data-justificativa="${item.idCatequisando}"
               maxlength="255" placeholder="Motivo da falta justificada"
               value="${chamEscape(justificativa)}" ${atual === 'JUSTIFICADA' ? '' : 'hidden'} />
      </div>
    `;
  }).join('');

  alvo.querySelectorAll('[data-marcar]').forEach((btn) => {
    btn.addEventListener('click', () => {
      chamMarcar(Number(btn.dataset.marcar), btn.dataset.situacao);
    });
  });
  alvo.querySelectorAll('[data-justificativa]').forEach((campo) => {
    campo.addEventListener('input', () => {
      const id = Number(campo.dataset.justificativa);
      const atual = chamMarcacoes.get(id);
      if (atual) chamMarcacoes.set(id, { ...atual, justificativa: campo.value });
    });
  });

  chamAtualizarContagem();
};

const chamMarcar = (idCatequisando, situacao) => {
  if (!chamEncontroAtual || !chamEncontroAtual.editavel) return;

  const atual = chamMarcacoes.get(idCatequisando);
  // Clicar de novo na mesma opcao desmarca: o catequista pode desmarcar,
  // e sem isso ele nao teria como voltar atras de um toque errado.
  if (atual && atual.situacao === situacao) {
    chamMarcacoes.delete(idCatequisando);
  } else {
    chamMarcacoes.set(idCatequisando, {
      situacao,
      justificativa: atual ? atual.justificativa : ''
    });
  }

  const linha = document.querySelector(`[data-linha="${idCatequisando}"]`);
  if (!linha) return;
  const agora = chamMarcacoes.get(idCatequisando);

  linha.querySelectorAll('[data-marcar]').forEach((btn) => {
    const ativa = Boolean(agora) && btn.dataset.situacao === agora.situacao;
    btn.classList.toggle('ativa', ativa);
    btn.setAttribute('aria-pressed', String(ativa));
  });

  const campo = linha.querySelector('[data-justificativa]');
  if (campo) {
    const precisa = Boolean(agora) && agora.situacao === 'JUSTIFICADA';
    campo.hidden = !precisa;
    if (precisa) campo.focus();
  }

  chamAtualizarContagem();
};

const chamAtualizarContagem = () => {
  const alvo = document.getElementById('cham-contagem');
  if (!alvo) return;
  const valores = Array.from(chamMarcacoes.values());
  const total = document.querySelectorAll('[data-linha]').length;
  const presentes = valores.filter((m) => m.situacao === 'PRESENTE').length;
  const faltas = valores.filter((m) => m.situacao === 'FALTA').length;
  const justificadas = valores.filter((m) => m.situacao === 'JUSTIFICADA').length;
  const semMarcar = total - valores.length;

  alvo.textContent =
    `${presentes} presente(s), ${faltas} falta(s), ${justificadas} justificada(s)` +
    (semMarcar > 0 ? ` — ${semMarcar} sem marcar (contam como falta ao encerrar)` : '');
};

const chamAtualizarControles = () => {
  const editavel = Boolean(chamEncontroAtual && chamEncontroAtual.editavel);
  ['cham-btn-salvar', 'cham-btn-encerrar', 'cham-btn-cancelar', 'cham-todos-presentes']
    .forEach((id) => {
      const btn = document.getElementById(id);
      if (btn) btn.disabled = !editavel;
    });
};

// ---- Envio ---------------------------------------------------------------

/** Monta o corpo do POST e valida o que o backend exigiria de qualquer forma. */
const chamCorpoMarcacoes = () => {
  const marcacoes = [];
  for (const [idCatequisando, m] of chamMarcacoes.entries()) {
    const justificativa = (m.justificativa || '').trim();
    if (m.situacao === 'JUSTIFICADA' && !justificativa) {
      const linha = document.querySelector(`[data-linha="${idCatequisando}"] strong`);
      const nome = linha ? linha.textContent : 'um catequisando';
      return { erro: `Informe o motivo da falta justificada de ${nome}.` };
    }
    marcacoes.push({ idCatequisando, situacao: m.situacao, justificativa: justificativa || null });
  }
  return { marcacoes };
};

const chamSalvar = async (silencioso = false) => {
  if (!chamEncontroAtual) return false;
  const corpo = chamCorpoMarcacoes();
  if (corpo.erro) {
    chamStatus(corpo.erro, 'error');
    return false;
  }

  try {
    const resposta = await fetch(`/api/chamada/encontro/${chamEncontroAtual.idEncontro}/marcar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ marcacoes: corpo.marcacoes })
    });
    if (!resposta.ok) {
      chamStatus(await chamErro(resposta, 'Não foi possível salvar a chamada.'), 'error');
      return false;
    }
    const dados = await resposta.json();
    chamAplicarChamada(dados);
    if (!silencioso) chamStatus('Chamada salva. Ela continua aberta para ajustes.', 'ok');
    return true;
  } catch (err) {
    chamStatus(`Falha de conexão ao salvar: ${err.message}`, 'error');
    return false;
  }
};

const chamEncerrar = async () => {
  if (!chamEncontroAtual) return;

  const presentes = Array.from(chamMarcacoes.values())
    .filter((m) => m.situacao === 'PRESENTE').length;

  let motivo = null;
  if (presentes === 0) {
    // Regra do backend, antecipada aqui: encontro sem ninguem presente e um
    // encontro que nao aconteceu, e cancelamento exige motivo registrado.
    motivo = window.prompt(
      'Nenhuma presença foi marcada. Isso registra o encontro como cancelado.\n' +
      'Informe o motivo (obrigatório):'
    );
    if (motivo === null) return;
    if (!motivo.trim()) {
      chamStatus('O motivo é obrigatório para registrar o cancelamento.', 'error');
      return;
    }
  } else {
    const confirma = window.confirm(
      `Encerrar a chamada com ${presentes} presente(s)?\n\n` +
      'Quem não foi marcado será registrado como falta, e depois de encerrada ' +
      'a lista não pode mais ser alterada.'
    );
    if (!confirma) return;
  }

  // Grava as marcacoes antes de fechar: o fechar nao recebe a lista, ele
  // apenas encerra o que ja esta gravado.
  if (presentes > 0 && !(await chamSalvar(true))) return;

  const tema = (document.getElementById('cham-tema') || {}).value || null;
  try {
    const resposta = await fetch(`/api/chamada/encontro/${chamEncontroAtual.idEncontro}/fechar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ motivo: motivo ? motivo.trim() : null, tema })
    });
    if (!resposta.ok) {
      chamStatus(await chamErro(resposta, 'Não foi possível encerrar a chamada.'), 'error');
      return;
    }
    const encontro = await resposta.json();
    chamEncontroAtual = encontro;
    chamAtualizarControles();
    chamStatus(
      encontro.situacao === 'CANCELADO'
        ? `Encontro de ${chamDataBR(encontro.data)} registrado como cancelado.`
        : `Chamada de ${chamDataBR(encontro.data)} encerrada com ${encontro.presentes} presente(s).`,
      'ok'
    );
    await chamCarregarTurmas();
    await chamCarregarEventos();
  } catch (err) {
    chamStatus(`Falha de conexão ao encerrar: ${err.message}`, 'error');
  }
};

const chamCancelar = async () => {
  if (!chamEncontroAtual) return;
  const motivo = window.prompt(
    'O encontro não aconteceu. Informe o motivo (obrigatório):\n' +
    'Ex.: feriado, chuva forte, catequista doente.'
  );
  if (motivo === null) return;
  if (!motivo.trim()) {
    chamStatus('O motivo do cancelamento é obrigatório.', 'error');
    return;
  }

  try {
    const resposta = await fetch(`/api/chamada/encontro/${chamEncontroAtual.idEncontro}/cancelar`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ motivo: motivo.trim() })
    });
    if (!resposta.ok) {
      chamStatus(await chamErro(resposta, 'Não foi possível cancelar o encontro.'), 'error');
      return;
    }
    chamEncontroAtual = await resposta.json();
    chamAtualizarControles();
    chamStatus(
      `Encontro de ${chamDataBR(chamEncontroAtual.data)} cancelado. ` +
      'Ele não conta como falta para ninguém.',
      'ok'
    );
    await chamCarregarTurmas();
  } catch (err) {
    chamStatus(`Falha de conexão ao cancelar: ${err.message}`, 'error');
  }
};

const chamAbrirEncontro = async () => {
  if (!chamTurmaAtual) return;
  const data = (document.getElementById('cham-data') || {}).value || null;
  const tema = (document.getElementById('cham-tema') || {}).value || null;

  chamStatus('Abrindo o encontro...', '');
  try {
    const resposta = await fetch('/api/chamada/abrir', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idTurma: chamTurmaAtual.idTurma, data, tema })
    });
    if (!resposta.ok) {
      chamStatus(await chamErro(resposta, 'Não foi possível abrir o encontro.'), 'error');
      return;
    }
    const encontro = await resposta.json();
    await chamCarregarChamada(encontro.idEncontro);
    await chamCarregarTurmas();
  } catch (err) {
    chamStatus(`Falha de conexão ao abrir: ${err.message}`, 'error');
  }
};

// ---- Eventos (retiro, missa) ----------------------------------------------

/*
 * A presenca no evento e gravada como um Encontro comum, ligado ao evento
 * pelo id. Isso deixa marcar, encerrar e auditar funcionando igual, sem
 * codigo duplicado -- a tela de chamada abaixo e literalmente a mesma.
 *
 * A diferenca esta no calculo: o backend ignora encontros de evento nos 80%.
 * Foi decisao de projeto e nao de implementacao: retiro e atividade extra, e
 * faltar nele nao pode reprovar quem cumpriu os encontros da catequese.
 */

let chamEventos = [];

const chamCarregarEventos = async () => {
  const alvo = document.getElementById('cham-eventos-lista');
  if (!alvo) return;
  alvo.innerHTML = '<p class="muted">Carregando eventos...</p>';

  try {
    const resposta = await fetch('/api/chamada/eventos');
    if (!resposta.ok) {
      alvo.innerHTML = `<div class="status error">${chamEscape(await chamErro(resposta, 'Não foi possível carregar os eventos.'))}</div>`;
      return;
    }
    chamEventos = await resposta.json();
    if (!chamEventos.length) {
      alvo.innerHTML =
        '<div class="status neutro">Nenhum evento cadastrado para este ano.</div>';
      return;
    }

    alvo.innerHTML = chamEventos.map(chamCartaoEvento).join('');
    alvo.querySelectorAll('[data-abrir-evento]').forEach((b) => {
      b.addEventListener('click', () => {
        chamAbrirChamadaDeEvento(Number(b.dataset.abrirEvento), Number(b.dataset.turma));
      });
    });
  } catch (err) {
    alvo.innerHTML = `<div class="status error">Falha de conexão: ${chamEscape(err.message)}</div>`;
  }
};

const chamPeriodoEvento = (evento) => {
  const inicio = chamDataBR(evento.dataInicio);
  const fim = chamDataBR(evento.dataFim);
  if (inicio && fim && inicio !== fim) return `${inicio} a ${fim}`;
  return inicio || fim || 'sem data definida';
};

const chamCartaoEvento = (evento) => {
  const turmas = (evento.turmas || []).map((t) => {
    if (!t.idEncontro) {
      return `
        <div class="evento-turma">
          <span>${chamEscape(t.nomeTurma)}</span>
          <span class="muted">${t.matriculados} matriculado(s)</span>
          <button type="button" class="secondary"
                  data-abrir-evento="${evento.idEvento}" data-turma="${t.idTurma}">
            Abrir chamada
          </button>
        </div>
      `;
    }
    const encerrada = !t.editavel;
    return `
      <div class="evento-turma">
        <span>${chamEscape(t.nomeTurma)}</span>
        <span class="status ${encerrada ? 'ok' : 'warning'}">
          ${encerrada ? `${t.presentes} presente(s)` : 'chamada em aberto'}
        </span>
        <button type="button" class="secondary"
                data-abrir-evento="${evento.idEvento}" data-turma="${t.idTurma}">
          ${encerrada ? 'Ver' : 'Continuar'}
        </button>
      </div>
    `;
  }).join('');

  return `
    <div class="evento-card" data-evento="${evento.idEvento}">
      <div class="evento-topo">
        <strong>${chamEscape(evento.titulo)}</strong>
        <span class="muted">${chamEscape(chamPeriodoEvento(evento))}</span>
        ${evento.local ? `<span class="muted">${chamEscape(evento.local)}</span>` : ''}
      </div>
      ${evento.publicoAlvo ? `<span class="muted">Público: ${chamEscape(evento.publicoAlvo)}</span>` : ''}
      <div class="evento-turmas">${turmas}</div>
    </div>
  `;
};

const chamAbrirChamadaDeEvento = async (idEvento, idTurma) => {
  const evento = chamEventos.find((e) => e.idEvento === idEvento);
  const turmaDoEvento = evento && (evento.turmas || []).find((t) => t.idTurma === idTurma);
  chamTurmaAtual = chamTurmas.find((t) => t.idTurma === idTurma) || null;
  if (!evento || !turmaDoEvento) return;

  document.getElementById('cham-tela-turmas').hidden = true;
  document.getElementById('cham-tela-eventos').hidden = true;
  document.getElementById('cham-tela-encontro').hidden = false;
  document.getElementById('cham-encontro-titulo').textContent =
    `${evento.titulo} — ${turmaDoEvento.nomeTurma}`;

  // Chamada ja aberta: vai direto para a lista.
  if (turmaDoEvento.idEncontro) {
    await chamCarregarChamada(turmaDoEvento.idEncontro);
    return;
  }

  chamStatus('Abrindo a chamada do evento...', '');
  try {
    const resposta = await fetch('/api/chamada/evento/abrir', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idEvento, idTurma })
    });
    if (!resposta.ok) {
      chamStatus(await chamErro(resposta, 'Não foi possível abrir a chamada do evento.'), 'error');
      return;
    }
    const encontro = await resposta.json();
    await chamCarregarChamada(encontro.idEncontro);
    await chamCarregarEventos();
  } catch (err) {
    chamStatus(`Falha de conexão: ${err.message}`, 'error');
  }
};

// ---- Ligações da tela ----------------------------------------------------

document.getElementById('cham-voltar')?.addEventListener('click', chamMostrarTurmas);
document.getElementById('cham-recarregar')?.addEventListener('click', chamCarregarTurmas);
document.getElementById('cham-btn-abrir')?.addEventListener('click', chamAbrirEncontro);
document.getElementById('cham-btn-salvar')?.addEventListener('click', () => chamSalvar(false));
document.getElementById('cham-btn-encerrar')?.addEventListener('click', chamEncerrar);
document.getElementById('cham-btn-cancelar')?.addEventListener('click', chamCancelar);

document.getElementById('cham-todos-presentes')?.addEventListener('click', () => {
  if (!chamEncontroAtual || !chamEncontroAtual.editavel) return;
  document.querySelectorAll('[data-linha]').forEach((linha) => {
    const id = Number(linha.dataset.linha);
    const atual = chamMarcacoes.get(id);
    // Nao sobrescreve quem ja foi marcado: se o catequista lancou uma falta
    // justificada, o atalho nao pode apaga-la sem ele perceber.
    if (!atual) chamMarcar(id, 'PRESENTE');
  });
});

/** script.js chama isto ao entrar na aba. */
window.carregarChamada = () => {
  chamMostrarTurmas();
  chamCarregarTurmas();
  chamCarregarEventos();
};
