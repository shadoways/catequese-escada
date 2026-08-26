/*
 * Area do coordenador paroquial: turmas, matriculas e correcao de chamada.
 *
 * Prefixo "adm" para nao colidir com o escopo global compartilhado.
 * (usuarios.js usa "usr" e cuida de CONTAS de acesso; este arquivo cuida da
 * catequese em si -- sao coisas diferentes com publicos diferentes.)
 *
 * Esta e a tela que destrava as outras: sem categoria na turma a frequencia
 * nao e apurada, e sem matricula nao existe lista de chamada. Por isso as
 * turmas pendentes de classificacao aparecem primeiro e com aviso.
 *
 * Tres contextos, um de cada vez: turmas, matriculas de uma turma, correcao
 * de uma chamada encerrada.
 */

let admTurmas = [];
let admTurmaAtual = null;
let admCatequisandos = [];
let admEncontroAtual = null;
let admCorrecoes = new Map();

const ADM_CATEGORIAS = [
  { valor: '', rotulo: 'Sem categoria' },
  { valor: 'PRE_CATEQUESE', rotulo: 'Pré-catequese' },
  { valor: 'EUCARISTIA', rotulo: 'Primeira Eucaristia' },
  { valor: 'CRISMA', rotulo: 'Crisma' },
  { valor: 'ADULTOS', rotulo: 'Adultos' },
  { valor: 'CATECUMENATO', rotulo: 'Catecumenato' },
  { valor: 'PERSEVERANCA', rotulo: 'Perseverança' }
];

const ADM_JANELAS = {
  ANO: 'apura por ano civil',
  SEMESTRE: 'apura por semestre',
  ETAPA_CATECUMENATO: 'apura por etapa do catecumenato',
  NENHUMA: 'sem apuração de frequência'
};

const ADM_SITUACOES_MATRICULA = [
  { valor: 'CURSANDO', rotulo: 'Cursando' },
  { valor: 'CONCLUIDO', rotulo: 'Concluído' },
  { valor: 'NAO_CONCLUIDO', rotulo: 'Não concluído' },
  { valor: 'DESISTENTE', rotulo: 'Desistente' }
];

const ADM_SITUACOES_PRESENCA = [
  { valor: 'PRESENTE', rotulo: 'Presente', curto: 'P' },
  { valor: 'FALTA', rotulo: 'Falta', curto: 'F' },
  { valor: 'JUSTIFICADA', rotulo: 'Justificada', curto: 'J' }
];

const admEscape = (valor) => {
  const div = document.createElement('div');
  div.textContent = valor === null || valor === undefined ? '' : String(valor);
  return div.innerHTML;
};

const admDataBR = (iso) => {
  if (!iso) return '';
  const p = String(iso).slice(0, 10).split('-');
  return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : '';
};

const admHojeISO = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

const admAviso = (id, texto, tipo = '') => {
  const caixa = document.getElementById(id);
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${admEscape(texto)}</div>` : '';
};

const admErro = async (resposta, padrao) => {
  const corpo = await resposta.json().catch(() => null);
  return (corpo && corpo.erro) || padrao;
};

const admAno = () => {
  const campo = document.getElementById('adm-ano');
  return (campo && campo.value) || String(new Date().getFullYear());
};

const admTela = (qual) => {
  document.getElementById('adm-tela-turmas').hidden = qual !== 'turmas';
  document.getElementById('adm-tela-matriculas').hidden = qual !== 'matriculas';
  document.getElementById('adm-tela-correcao').hidden = qual !== 'correcao';
  document.getElementById('adm-tela-encerramento').hidden = qual !== 'encerramento';
};

// ---- Tela 1: turmas -------------------------------------------------------

const admCarregarTurmas = async () => {
  const alvo = document.getElementById('adm-turmas-lista');
  if (!alvo) return;
  alvo.innerHTML = '<p class="muted">Carregando turmas...</p>';

  try {
    const resposta = await fetch(`/api/admin/turmas?ano=${encodeURIComponent(admAno())}`);
    if (!resposta.ok) {
      admAviso('adm-status', await admErro(resposta, 'Não foi possível carregar as turmas.'), 'error');
      alvo.innerHTML = '';
      return;
    }
    admTurmas = await resposta.json();
    admAviso('adm-status', '');

    const pendentes = admTurmas.filter((t) => t.pendenteDeClassificacao).length;
    if (pendentes > 0) {
      admAviso(
        'adm-status',
        `${pendentes} turma(s) ainda sem categoria. Enquanto isso, a frequência delas não é apurada.`,
        'warning'
      );
    }

    alvo.innerHTML = admTurmas.map(admCartaoTurma).join('');
    admLigarTurmas(alvo);
  } catch (err) {
    admAviso('adm-status', `Falha de conexão: ${err.message}`, 'error');
    alvo.innerHTML = '';
  }
};

const admCartaoTurma = (turma) => {
  // Comunidade dona da turma: e o que decide qual coordenador pode mexer nos
  // eventos dela na agenda.
  const opcoesComunidade = [{ v: '', r: 'Sem comunidade definida' }]
    .concat(ADM_COMUNIDADES.map((c) => ({ v: String(c.idComunidade), r: c.nome })))
    .map((c) =>
      `<option value="${c.v}"${String(turma.idComunidade || '') === c.v ? ' selected' : ''}>${admEscape(c.r)}</option>`
    ).join('');

  const opcoesCategoria = ADM_CATEGORIAS.map((c) =>
    `<option value="${c.valor}"${(turma.categoria || '') === c.valor ? ' selected' : ''}>${admEscape(c.rotulo)}</option>`
  ).join('');

  const opcoesEtapa = [
    { v: '', r: 'Sem ano definido' },
    { v: '1', r: '1º ano' },
    { v: '2', r: '2º ano' }
  ].map((e) =>
    `<option value="${e.v}"${String(turma.etapa || '') === e.v ? ' selected' : ''}>${e.r}</option>`
  ).join('');

  return `
    <div class="adm-turma${turma.pendenteDeClassificacao ? ' adm-turma--pendente' : ''}"
         data-turma="${turma.idTurma}">
      <div class="adm-turma-topo">
        <strong>${admEscape(turma.nome)}</strong>
        <span class="status ${turma.pendenteDeClassificacao ? 'warning' : 'ok'}">
          ${admEscape(ADM_JANELAS[turma.janela] || '')}
        </span>
        <span class="muted">${turma.matriculadosNoAno} matriculado(s)</span>
        ${turma.nomeCatequista ? `<span class="muted">${admEscape(turma.nomeCatequista)}</span>` : ''}
      </div>
      <div class="adm-turma-acoes">
        <label>
          Categoria
          <select data-categoria="${turma.idTurma}">${opcoesCategoria}</select>
        </label>
        <label>
          Ano do percurso
          <select data-etapa="${turma.idTurma}">${opcoesEtapa}</select>
        </label>
        <label>
          Comunidade
          <select data-comunidade="${turma.idTurma}">${opcoesComunidade}</select>
        </label>
        <button type="button" data-salvar="${turma.idTurma}">Salvar</button>
        <button type="button" class="secondary" data-matriculas="${turma.idTurma}">
          Matrículas
        </button>
        <button type="button" class="secondary" data-corrigir="${turma.idTurma}">
          Corrigir chamada
        </button>
      </div>
    </div>
  `;
};

const admLigarTurmas = (alvo) => {
  alvo.querySelectorAll('[data-salvar]').forEach((b) => {
    b.addEventListener('click', () => admClassificar(Number(b.dataset.salvar)));
  });
  alvo.querySelectorAll('[data-matriculas]').forEach((b) => {
    b.addEventListener('click', () => admAbrirMatriculas(Number(b.dataset.matriculas)));
  });
  alvo.querySelectorAll('[data-corrigir]').forEach((b) => {
    b.addEventListener('click', () => admAbrirCorrecao(Number(b.dataset.corrigir)));
  });
};

const admClassificar = async (idTurma) => {
  const categoria = document.querySelector(`[data-categoria="${idTurma}"]`).value || null;
  const etapaBruta = document.querySelector(`[data-etapa="${idTurma}"]`).value;
  const etapa = etapaBruta ? Number(etapaBruta) : null;
  const comunidadeBruta = document.querySelector(`[data-comunidade="${idTurma}"]`);
  const idComunidade = comunidadeBruta && comunidadeBruta.value
    ? Number(comunidadeBruta.value)
    : null;

  try {
    const resposta = await fetch(`/api/admin/turmas/${idTurma}/classificacao`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ categoria, etapa, idComunidade })
    });
    if (!resposta.ok) {
      admAviso('adm-status', await admErro(resposta, 'Não foi possível salvar.'), 'error');
      return;
    }
    const turma = await resposta.json();
    admAviso('adm-status', `${turma.nome}: ${ADM_JANELAS[turma.janela] || 'atualizada'}.`, 'ok');
    await admCarregarTurmas();
  } catch (err) {
    admAviso('adm-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

// ---- Tela 2: matrículas ---------------------------------------------------

const admAbrirMatriculas = async (idTurma) => {
  admTurmaAtual = admTurmas.find((t) => t.idTurma === idTurma) || null;
  if (!admTurmaAtual) return;

  admTela('matriculas');
  document.getElementById('adm-matriculas-titulo').textContent =
    `Matrículas — ${admTurmaAtual.nome} (${admAno()})`;
  const campoData = document.getElementById('adm-nova-data');
  if (campoData) campoData.value = admHojeISO();

  await Promise.all([admCarregarMatriculas(), admCarregarCatequisandos()]);
};

const admCarregarCatequisandos = async () => {
  const select = document.getElementById('adm-novo-catequisando');
  if (!select) return;
  try {
    const resposta = await fetch('/api/catequisandos');
    if (!resposta.ok) return;
    admCatequisandos = await resposta.json();
    select.innerHTML = admCatequisandos
      .slice()
      .sort((a, b) => String(a.nome).localeCompare(String(b.nome), 'pt-BR'))
      .map((c) => `<option value="${c.idCatequisando}">${admEscape(c.nome)}</option>`)
      .join('');
  } catch (err) {
    // Sem a lista, o resto da tela continua util.
    admAviso('adm-matriculas-status', `Não foi possível carregar os catequisandos: ${err.message}`, 'warning');
  }
};

const admCarregarMatriculas = async () => {
  if (!admTurmaAtual) return;
  const alvo = document.getElementById('adm-matriculas-lista');
  alvo.innerHTML = '<p class="muted">Carregando...</p>';

  try {
    const resposta = await fetch(
      `/api/admin/turmas/${admTurmaAtual.idTurma}/matriculas?ano=${encodeURIComponent(admAno())}`
    );
    if (!resposta.ok) {
      admAviso('adm-matriculas-status', await admErro(resposta, 'Não foi possível carregar.'), 'error');
      alvo.innerHTML = '';
      return;
    }
    const matriculas = await resposta.json();
    if (!matriculas.length) {
      alvo.innerHTML =
        '<div class="status warning">Nenhuma matrícula neste ano. Sem matrícula não há lista de chamada.</div>';
      return;
    }
    alvo.innerHTML = matriculas.map(admLinhaMatricula).join('');
    admLigarMatriculas(alvo);
  } catch (err) {
    admAviso('adm-matriculas-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admLinhaMatricula = (m) => {
  const opcoes = ADM_SITUACOES_MATRICULA.map((s) =>
    `<option value="${s.valor}"${m.situacao === s.valor ? ' selected' : ''}>${s.rotulo}</option>`
  ).join('');

  // Transferido nao volta atras por aqui: a matricula ja foi encerrada e
  // existe outra ativa no destino. Mexer nela criaria duas ativas.
  const transferido = m.situacao === 'TRANSFERIDO';
  const destinos = admTurmas
    .filter((t) => t.idTurma !== (admTurmaAtual && admTurmaAtual.idTurma))
    .map((t) => `<option value="${t.idTurma}">${admEscape(t.nome)}</option>`)
    .join('');

  return `
    <div class="adm-matricula" data-matricula="${m.idMatricula}">
      <div class="adm-matricula-topo">
        <strong>${admEscape(m.nomeCatequisando)}</strong>
        <span class="muted">desde ${admDataBR(m.dataMatricula) || '—'}</span>
        ${m.atualizadoPor ? `<span class="muted">por ${admEscape(m.atualizadoPor)}</span>` : ''}
      </div>
      ${transferido
        ? '<span class="status neutro">Transferido — a matrícula ativa está na turma de destino.</span>'
        : `<div class="adm-matricula-acoes">
             <label>
               Situação
               <select data-situacao="${m.idMatricula}">${opcoes}</select>
             </label>
             <button type="button" data-salvar-situacao="${m.idMatricula}">Salvar</button>
             <label>
               Transferir para
               <select data-destino="${m.idMatricula}">${destinos}</select>
             </label>
             <button type="button" class="secondary" data-transferir="${m.idMatricula}">
               Transferir
             </button>
           </div>`}
      ${m.observacao ? `<span class="muted">${admEscape(m.observacao)}</span>` : ''}
    </div>
  `;
};

const admLigarMatriculas = (alvo) => {
  alvo.querySelectorAll('[data-salvar-situacao]').forEach((b) => {
    b.addEventListener('click', () => admSalvarSituacao(Number(b.dataset.salvarSituacao)));
  });
  alvo.querySelectorAll('[data-transferir]').forEach((b) => {
    b.addEventListener('click', () => admTransferir(Number(b.dataset.transferir)));
  });
};

const admSalvarSituacao = async (idMatricula) => {
  const situacao = document.querySelector(`[data-situacao="${idMatricula}"]`).value;
  try {
    const resposta = await fetch(`/api/admin/matriculas/${idMatricula}/situacao`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ situacao })
    });
    if (!resposta.ok) {
      admAviso('adm-matriculas-status', await admErro(resposta, 'Não foi possível salvar.'), 'error');
      return;
    }
    admAviso('adm-matriculas-status', 'Situação atualizada.', 'ok');
    await admCarregarMatriculas();
  } catch (err) {
    admAviso('adm-matriculas-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admTransferir = async (idMatricula) => {
  const idTurmaDestino = Number(document.querySelector(`[data-destino="${idMatricula}"]`).value);
  if (!idTurmaDestino) {
    admAviso('adm-matriculas-status', 'Escolha a turma de destino.', 'warning');
    return;
  }

  const motivo = window.prompt(
    'Transferência de turma.\n\n' +
    'A matrícula atual será encerrada como transferida e uma nova será criada\n' +
    'na turma de destino, contando a frequência a partir de hoje.\n\n' +
    'Motivo (opcional):'
  );
  if (motivo === null) return;

  try {
    const resposta = await fetch(`/api/admin/matriculas/${idMatricula}/transferir`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idTurmaDestino, data: admHojeISO(), motivo: motivo.trim() || null })
    });
    if (!resposta.ok) {
      admAviso('adm-matriculas-status', await admErro(resposta, 'Não foi possível transferir.'), 'error');
      return;
    }
    admAviso('adm-matriculas-status', 'Transferência registrada nas duas turmas.', 'ok');
    await admCarregarMatriculas();
  } catch (err) {
    admAviso('adm-matriculas-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admMatricular = async () => {
  if (!admTurmaAtual) return;
  const idCatequisando = Number((document.getElementById('adm-novo-catequisando') || {}).value);
  const dataMatricula = (document.getElementById('adm-nova-data') || {}).value || null;
  if (!idCatequisando) {
    admAviso('adm-matriculas-status', 'Escolha o catequisando.', 'warning');
    return;
  }

  try {
    const resposta = await fetch('/api/admin/matriculas', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        idCatequisando,
        idTurma: admTurmaAtual.idTurma,
        ano: Number(admAno()),
        dataMatricula
      })
    });
    if (!resposta.ok) {
      admAviso('adm-matriculas-status', await admErro(resposta, 'Não foi possível matricular.'), 'error');
      return;
    }
    const criada = await resposta.json();
    admAviso('adm-matriculas-status', `${criada.nomeCatequisando} matriculado(a).`, 'ok');
    await admCarregarMatriculas();
  } catch (err) {
    admAviso('adm-matriculas-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

// ---- Tela 3: correção de chamada encerrada --------------------------------

const admAbrirCorrecao = async (idTurma) => {
  admTurmaAtual = admTurmas.find((t) => t.idTurma === idTurma) || null;
  if (!admTurmaAtual) return;

  admTela('correcao');
  document.getElementById('adm-correcao-titulo').textContent =
    `Corrigir chamada — ${admTurmaAtual.nome}`;
  document.getElementById('adm-correcao-area').hidden = true;
  admAviso('adm-correcao-status', '');

  const alvo = document.getElementById('adm-encontros-lista');
  alvo.innerHTML = '<p class="muted">Carregando encontros...</p>';

  try {
    const resposta = await fetch(`/api/chamada/turma/${idTurma}/encontros`);
    if (!resposta.ok) {
      admAviso('adm-correcao-status', await admErro(resposta, 'Não foi possível carregar.'), 'error');
      alvo.innerHTML = '';
      return;
    }
    const encontros = await resposta.json();
    const encerrados = encontros.filter((e) => !e.editavel);
    if (!encerrados.length) {
      alvo.innerHTML =
        '<div class="status neutro">Nenhum encontro encerrado nesta turma. Só há o que corrigir depois que a chamada é encerrada.</div>';
      return;
    }

    alvo.innerHTML = encerrados.map((e) => `
      <button type="button" class="adm-encontro" data-encontro="${e.idEncontro}">
        <strong>${admDataBR(e.data)}</strong>
        <span class="status ${e.situacao === 'CANCELADO' ? 'neutro' : 'ok'}">
          ${e.situacao === 'CANCELADO' ? 'Cancelado' : `${e.presentes} presente(s), ${e.faltas} falta(s)`}
        </span>
        ${e.fechamentoAutomatico ? '<span class="muted">encerrado automaticamente</span>' : ''}
        ${e.motivoCancelamento ? `<span class="muted">${admEscape(e.motivoCancelamento)}</span>` : ''}
      </button>
    `).join('');

    alvo.querySelectorAll('[data-encontro]').forEach((b) => {
      b.addEventListener('click', () => admCarregarChamada(Number(b.dataset.encontro)));
    });
  } catch (err) {
    admAviso('adm-correcao-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admCarregarChamada = async (idEncontro) => {
  try {
    const resposta = await fetch(`/api/chamada/encontro/${idEncontro}`);
    if (!resposta.ok) {
      admAviso('adm-correcao-status', await admErro(resposta, 'Não foi possível abrir a chamada.'), 'error');
      return;
    }
    const dados = await resposta.json();
    admEncontroAtual = dados.encontro;
    admCorrecoes = new Map();
    dados.itens.forEach((i) => {
      if (i.situacao) admCorrecoes.set(i.idCatequisando, { situacao: i.situacao, justificativa: i.justificativa || '' });
    });

    document.getElementById('adm-correcao-area').hidden = false;
    document.getElementById('adm-correcao-motivo').value = '';
    admDesenharCorrecao(dados.itens);
    admAviso(
      'adm-correcao-status',
      `Encontro de ${admDataBR(admEncontroAtual.data)}. Altere apenas o que estiver errado.`,
      ''
    );
  } catch (err) {
    admAviso('adm-correcao-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admDesenharCorrecao = (itens) => {
  const alvo = document.getElementById('adm-correcao-itens');
  alvo.innerHTML = itens.map((item) => {
    const atual = admCorrecoes.get(item.idCatequisando);
    const situacao = atual ? atual.situacao : null;
    const botoes = ADM_SITUACOES_PRESENCA.map((s) => `
      <button type="button"
              class="cham-opcao cham-opcao--${s.valor.toLowerCase()}${situacao === s.valor ? ' ativa' : ''}"
              data-corr="${item.idCatequisando}" data-sit="${s.valor}"
              aria-pressed="${situacao === s.valor}"
              aria-label="${s.rotulo} — ${admEscape(item.nome)}">
        <span class="cham-opcao-curto" aria-hidden="true">${s.curto}</span>
        <span class="cham-opcao-rotulo">${s.rotulo}</span>
      </button>
    `).join('');

    return `
      <div class="chamada-item" data-linha-corr="${item.idCatequisando}">
        <div class="chamada-item-nome">
          <strong>${admEscape(item.nome)}</strong>
          ${item.marcadoPor ? `<span class="cham-autoria">marcado por ${admEscape(item.marcadoPor)}</span>` : ''}
        </div>
        <div class="chamada-item-opcoes">${botoes}</div>
        <input type="text" class="cham-justificativa" data-just-corr="${item.idCatequisando}"
               maxlength="255" placeholder="Motivo da falta justificada"
               value="${admEscape(atual ? atual.justificativa : '')}"
               ${situacao === 'JUSTIFICADA' ? '' : 'hidden'} />
      </div>
    `;
  }).join('');

  alvo.querySelectorAll('[data-corr]').forEach((b) => {
    b.addEventListener('click', () => {
      const id = Number(b.dataset.corr);
      const anterior = admCorrecoes.get(id);
      admCorrecoes.set(id, {
        situacao: b.dataset.sit,
        justificativa: anterior ? anterior.justificativa : ''
      });
      const linha = document.querySelector(`[data-linha-corr="${id}"]`);
      linha.querySelectorAll('[data-corr]').forEach((outro) => {
        const ativa = outro.dataset.sit === b.dataset.sit;
        outro.classList.toggle('ativa', ativa);
        outro.setAttribute('aria-pressed', String(ativa));
      });
      const campo = linha.querySelector('[data-just-corr]');
      if (campo) campo.hidden = b.dataset.sit !== 'JUSTIFICADA';
    });
  });

  alvo.querySelectorAll('[data-just-corr]').forEach((campo) => {
    campo.addEventListener('input', () => {
      const id = Number(campo.dataset.justCorr);
      const atual = admCorrecoes.get(id);
      if (atual) admCorrecoes.set(id, { ...atual, justificativa: campo.value });
    });
  });
};

const admSalvarCorrecao = async () => {
  if (!admEncontroAtual) return;
  const motivo = (document.getElementById('adm-correcao-motivo') || {}).value || '';
  if (!motivo.trim()) {
    admAviso('adm-correcao-status', 'O motivo da correção é obrigatório.', 'error');
    return;
  }

  const correcoes = [];
  for (const [idCatequisando, c] of admCorrecoes.entries()) {
    const justificativa = (c.justificativa || '').trim();
    if (c.situacao === 'JUSTIFICADA' && !justificativa) {
      admAviso('adm-correcao-status', 'Informe o motivo de cada falta justificada.', 'error');
      return;
    }
    correcoes.push({ idCatequisando, situacao: c.situacao, justificativa: justificativa || null });
  }

  try {
    const resposta = await fetch(`/api/chamada/encontro/${admEncontroAtual.idEncontro}/corrigir`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ motivo: motivo.trim(), correcoes })
    });
    if (!resposta.ok) {
      admAviso('adm-correcao-status', await admErro(resposta, 'Não foi possível corrigir.'), 'error');
      return;
    }
    const dados = await resposta.json();
    admDesenharCorrecao(dados.itens);
    admAviso('adm-correcao-status', 'Correção registrada com o motivo e o seu nome.', 'ok');
  } catch (err) {
    admAviso('adm-correcao-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admReabrir = async () => {
  if (!admEncontroAtual) return;
  const confirma = window.confirm(
    'Reabrir o encontro devolve a chamada ao catequista, que poderá alterá-la.\n\n' +
    'Se você só quer ajustar uma presença, use "Salvar correção" — é mais seguro,\n' +
    'porque o encontro não fica aberto.\n\nReabrir mesmo assim?'
  );
  if (!confirma) return;

  try {
    const resposta = await fetch(`/api/chamada/encontro/${admEncontroAtual.idEncontro}/reabrir`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    });
    if (!resposta.ok) {
      admAviso('adm-correcao-status', await admErro(resposta, 'Não foi possível reabrir.'), 'error');
      return;
    }
    admAviso('adm-correcao-status', 'Encontro reaberto. O catequista já pode alterar a chamada.', 'ok');
    document.getElementById('adm-correcao-area').hidden = true;
    await admAbrirCorrecao(admTurmaAtual.idTurma);
  } catch (err) {
    admAviso('adm-correcao-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

// ---- Tela 4: encerramento do ano ------------------------------------------

/*
 * Encerrar o ano decide quem concluiu a catequese. E a operacao mais
 * destrutiva do sistema, entao a tela trabalha em duas fases: previa e
 * aplicacao. A previa nao altera nada, e a aplicacao so toca no que foi
 * marcado -- um "aplicar tudo" implicito faria o administrador encerrar
 * linhas que ele nem chegou a ler.
 */

let admPrevia = null;

const ADM_ROTULO_PROPOSTA = {
  CONCLUIDO: 'Conclui',
  NAO_CONCLUIDO: 'Não conclui'
};

const admAbrirEncerramento = async () => {
  admTela('encerramento');
  document.getElementById('adm-enc-titulo').textContent = `Encerrar ano ${admAno()}`;
  document.getElementById('adm-enc-controles').hidden = true;
  document.getElementById('adm-enc-rodape').hidden = true;
  document.getElementById('adm-enc-lista').innerHTML = '<p class="muted">Calculando a prévia...</p>';
  admAviso('adm-enc-status', '');

  try {
    const resposta = await fetch(`/api/admin/encerramento/previa?ano=${encodeURIComponent(admAno())}`);
    if (!resposta.ok) {
      admAviso('adm-enc-status', await admErro(resposta, 'Não foi possível calcular a prévia.'), 'error');
      document.getElementById('adm-enc-lista').innerHTML = '';
      return;
    }
    admPrevia = await resposta.json();
    admDesenharPrevia();
  } catch (err) {
    admAviso('adm-enc-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admDesenharPrevia = () => {
  if (!admPrevia) return;
  const r = admPrevia.resumo;
  const lista = document.getElementById('adm-enc-lista');

  document.getElementById('adm-enc-resumo').innerHTML = [
    { rotulo: 'Concluem', valor: r.concluem, classe: 'ok' },
    { rotulo: 'Não concluem', valor: r.naoConcluem, classe: 'error' },
    { rotulo: 'Sem base para decidir', valor: r.semBase, classe: 'neutro' },
    { rotulo: 'Encerram o percurso', valor: r.concluemPercurso, classe: 'ok' },
    { rotulo: 'Promoções de etapa', valor: r.promocoesDeEtapa, classe: 'neutro' }
  ].map((c) => `
    <div class="freq-contador">
      <strong class="status ${c.classe}">${c.valor}</strong>
      <span class="muted">${c.rotulo}</span>
    </div>
  `).join('');

  document.getElementById('adm-enc-alertas').innerHTML = (admPrevia.alertas || [])
    .map((a) => `<div class="status warning">${admEscape(a)}</div>`).join('');

  if (!admPrevia.linhas.length) {
    lista.innerHTML =
      '<div class="status neutro">Nenhuma matrícula em andamento neste ano. Não há o que encerrar.</div>';
    return;
  }

  document.getElementById('adm-enc-controles').hidden = false;
  document.getElementById('adm-enc-rodape').hidden = false;

  lista.innerHTML = admPrevia.linhas.map((l) => {
    const proposta = ADM_ROTULO_PROPOSTA[l.situacaoProposta] || 'Sem decisão';
    const classe = l.situacaoProposta === 'CONCLUIDO'
      ? 'ok'
      : (l.situacaoProposta === 'NAO_CONCLUIDO' ? 'error' : 'neutro');

    return `
      <div class="adm-enc-linha${l.aplicavel ? '' : ' adm-enc-linha--sem-base'}"
           data-previa="${l.idMatricula}">
        <label class="adm-enc-marca">
          <input type="checkbox" data-marca="${l.idMatricula}" ${l.aplicavel ? '' : 'disabled'} />
          <span class="adm-enc-nome">
            <strong>${admEscape(l.nome)}</strong>
            <span class="muted">${admEscape(l.nomeTurma || '')}</span>
          </span>
        </label>
        <span class="adm-enc-numeros">
          <strong>${l.percentual === null || l.percentual === undefined ? '—' : `${l.percentual}%`}</strong>
          <span class="status ${classe}">${admEscape(proposta)}</span>
          ${l.concluiPercurso ? '<span class="status ok">encerra o percurso</span>' : ''}
        </span>
        <span class="muted adm-enc-motivo">${admEscape(l.motivo)}</span>
      </div>
    `;
  }).join('');
};

const admMarcarPrevia = (marcar) => {
  document.querySelectorAll('[data-marca]').forEach((c) => {
    if (!c.disabled) c.checked = marcar;
  });
};

const admAplicarEncerramento = async () => {
  if (!admPrevia) return;
  const ids = Array.from(document.querySelectorAll('[data-marca]:checked'))
    .map((c) => Number(c.dataset.marca));

  if (!ids.length) {
    admAviso('adm-enc-status', 'Marque pelo menos uma linha para aplicar.', 'warning');
    return;
  }

  const naoConcluem = admPrevia.linhas
    .filter((l) => ids.includes(l.idMatricula) && l.situacaoProposta === 'NAO_CONCLUIDO').length;
  const promover = document.getElementById('adm-enc-promover').checked;

  // Confirmacao com os numeros, e nao um "tem certeza?" generico: o que
  // importa e quantas pessoas ficam sem concluir a catequese.
  const confirma = window.confirm(
    `Encerrar ${ids.length} matrícula(s) de ${admPrevia.ano}.\n\n` +
    `${naoConcluem} pessoa(s) ficarão como NÃO CONCLUÍDO.\n` +
    (promover ? 'As etapas dos catecúmenos aprovados também serão avançadas.\n' : '') +
    '\nO histórico fica registrado. Confirmar?'
  );
  if (!confirma) return;

  try {
    const resposta = await fetch('/api/admin/encerramento/aplicar', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ano: admPrevia.ano, idsMatricula: ids, promoverEtapas: promover })
    });
    if (!resposta.ok) {
      admAviso('adm-enc-status', await admErro(resposta, 'Não foi possível encerrar.'), 'error');
      return;
    }
    const resultado = await resposta.json();
    const ignoradas = resultado.ignoradas || [];

    // Recarrega ANTES de avisar: admAbrirEncerramento limpa o status, entao
    // avisar primeiro faria a confirmacao sumir na frente do usuario --
    // logo depois da operacao que ele mais precisa ver confirmada.
    await admAbrirEncerramento();
    admAviso(
      'adm-enc-status',
      `${resultado.matriculasAtualizadas} matrícula(s) encerrada(s)` +
      (resultado.etapasPromovidas ? `, ${resultado.etapasPromovidas} etapa(s) avançada(s)` : '') +
      (ignoradas.length ? `. ${ignoradas.length} ignorada(s): ${ignoradas.join(' ')}` : '.'),
      ignoradas.length ? 'warning' : 'ok'
    );
  } catch (err) {
    admAviso('adm-enc-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

// ---- Ligações da tela -----------------------------------------------------

document.getElementById('adm-recarregar')?.addEventListener('click', admCarregarTurmas);
document.getElementById('adm-ano')?.addEventListener('change', admCarregarTurmas);
document.getElementById('adm-voltar-turmas')?.addEventListener('click', () => admTela('turmas'));
document.getElementById('adm-voltar-correcao')?.addEventListener('click', () => admTela('turmas'));
document.getElementById('adm-btn-matricular')?.addEventListener('click', admMatricular);
document.getElementById('adm-btn-corrigir')?.addEventListener('click', admSalvarCorrecao);
document.getElementById('adm-btn-reabrir')?.addEventListener('click', admReabrir);
document.getElementById('adm-abrir-encerramento')?.addEventListener('click', admAbrirEncerramento);
document.getElementById('adm-voltar-encerramento')?.addEventListener('click', () => admTela('turmas'));
document.getElementById('adm-enc-marcar-todos')?.addEventListener('click', () => admMarcarPrevia(true));
document.getElementById('adm-enc-desmarcar')?.addEventListener('click', () => admMarcarPrevia(false));
document.getElementById('adm-btn-encerrar')?.addEventListener('click', admAplicarEncerramento);

/** script.js chama isto ao entrar na aba. */
window.carregarAdminCatequese = () => {
  const campoAno = document.getElementById('adm-ano');
  if (campoAno && !campoAno.value) campoAno.value = String(new Date().getFullYear());
  admTela('turmas');
  admCarregarTurmas();
};


// ---- Comunidades disponiveis -----------------------------------------------
// Carregadas uma vez e guardadas: cada cartao de turma monta o proprio <select>
// na hora de renderizar, e buscar a lista por cartao seria uma chamada por
// turma na tela.
let ADM_COMUNIDADES = [];

const admCarregarComunidades = async () => {
  try {
    const resposta = await fetch('/api/comunidades');
    if (!resposta.ok) return;
    const lista = await resposta.json();
    ADM_COMUNIDADES = lista
      .filter((c) => c.ativo !== false)
      .sort((a, b) => String(a.nome).localeCompare(String(b.nome), 'pt-BR'));
  } catch (err) {
    // Sem a lista o <select> fica so com "Sem comunidade definida"; o resto da
    // classificacao (categoria e etapa) continua funcionando normalmente.
  }
};

document.addEventListener('DOMContentLoaded', admCarregarComunidades);
