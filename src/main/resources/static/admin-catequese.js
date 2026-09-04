/*
 * Area do coordenador paroquial: turmas, inscricoes e correcao de chamada.
 *
 * Prefixo "adm" para nao colidir com o escopo global compartilhado.
 * (usuarios.js usa "usr" e cuida de CONTAS de acesso; este arquivo cuida da
 * catequese em si -- sao coisas diferentes com publicos diferentes.)
 *
 * Esta e a tela que destrava as outras: sem categoria na turma a frequencia
 * nao e apurada, e sem inscricao nao existe lista de chamada.
 *
 * LISTAR E EDITAR SAO TELAS SEPARADAS. A listagem responde "onde esta cada
 * turma?" e nao tem um unico campo editavel; a edicao responde "o que muda
 * nesta turma?". Enquanto os selects de classificacao ficavam dentro de cada
 * cartao da lista, ver e alterar eram o mesmo gesto, e a lista inteira era um
 * formulario que ninguem tinha pedido para abrir.
 */

let admTurmas = [];
let admTurmaAtual = null;
let admMatriculas = [];
let admEncontroAtual = null;
let admCorrecoes = new Map();

/** A listagem so aparece depois do "Consultar". Ver admCarregarTurmas. */
let admConsultado = false;

const ADM_CATEGORIAS = [
  { valor: '', rotulo: 'Sem categoria' },
  { valor: 'PRE_CATEQUESE', rotulo: 'Pré-catequese' },
  { valor: 'EUCARISTIA', rotulo: 'Primeira Eucaristia' },
  { valor: 'CRISMA', rotulo: 'Crisma' },
  { valor: 'ADULTOS', rotulo: 'Adultos' },
  { valor: 'CATECUMENATO', rotulo: 'Catecumenato' },
  { valor: 'PERSEVERANCA', rotulo: 'Perseverança' }
];

/**
 * So Eucaristia e Crisma tem FASE.
 *
 * Espelha `RegrasDeMovimentacao.temFases` no servidor -- ali esta a regra que
 * manda. Aqui ela existe porque a tela precisa decidir ANTES de perguntar: um
 * combo com "1a fase / 2a fase" numa turma de adultos convida a responder uma
 * pergunta que nao existe, e o valor escolhido ficava gravado.
 *
 * Adultos tambem dura dois anos, e por isso `anosPrevistos` nao serve de
 * criterio: durar dois anos nao e dividir-se em duas fases.
 */
const ADM_COM_FASE = ['EUCARISTIA', 'CRISMA'];

const admTemFases = (categoria) => ADM_COM_FASE.includes(categoria || '');

/** "1º ano"/"2º ano" viraram fase: percurso de catequese nao e serie escolar. */
const ADM_ROTULO_FASE = { 1: 'Primeira fase', 2: 'Segunda fase' };

const admRotuloFase = (turma) => {
  if (!turma || !admTemFases(turma.categoria)) return '—';
  return ADM_ROTULO_FASE[turma.etapa] || '—';
};

const admRotuloCategoria = (categoria) => {
  const achada = ADM_CATEGORIAS.find((c) => c.valor === (categoria || ''));
  return achada ? achada.rotulo : 'Sem categoria';
};

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

// ---- Tela 1: listagem de turmas (so consulta) -----------------------------

/**
 * Busca as turmas do ano.
 *
 * `renderizar = false` e o carregamento silencioso da abertura da aba: os
 * dados sao necessarios para montar o filtro de turma e para saber os destinos
 * possiveis de uma transferencia, mas a LISTA so aparece quando a pessoa pede.
 * Despejar tudo de cara obrigava quem entrou atras de uma comunidade a
 * descartar o resto no olho.
 */
const admCarregarTurmas = async (renderizar = true) => {
  const alvo = document.getElementById('adm-turmas-lista');
  if (!alvo) return;
  if (renderizar) alvo.innerHTML = '<p class="muted">Carregando turmas...</p>';

  try {
    const resposta = await fetch(`/api/admin/turmas?ano=${encodeURIComponent(admAno())}`);
    if (!resposta.ok) {
      admAviso('adm-status', await admErro(resposta, 'Não foi possível carregar as turmas.'), 'error');
      if (renderizar) alvo.innerHTML = '';
      return;
    }
    admTurmas = await resposta.json();
    admPreencherFiltros();

    if (!renderizar) return;
    admConsultado = true;
    admDesenharTurmas();
  } catch (err) {
    admAviso('adm-status', `Falha de conexão: ${err.message}`, 'error');
    if (renderizar) alvo.innerHTML = '';
  }
};

const admDesenharTurmas = () => {
  const alvo = document.getElementById('adm-turmas-lista');
  if (!alvo) return;

  if (!admConsultado) {
    admAviso('adm-status', '');
    alvo.innerHTML =
      '<p class="muted">Escolha a comunidade e a turma e clique em <strong>Consultar</strong>.</p>';
    return;
  }

  const visiveis = admTurmasFiltradas();
  const pendentes = visiveis.filter((t) => t.pendenteDeClassificacao).length;
  admAviso(
    'adm-status',
    pendentes > 0
      ? `${pendentes} turma(s) ainda sem classificação. Enquanto isso, a frequência delas não é apurada.`
      : '',
    'warning'
  );

  if (!visiveis.length) {
    alvo.innerHTML = '<div class="status neutro">Nenhuma turma com este filtro.</div>';
    return;
  }

  // Tabela, e nao cartao com campos: aqui nao se altera nada. Quatro colunas --
  // turma, fase, comunidade e inscritos -- porque sao as perguntas que se faz
  // olhando uma lista. O resto (janela de apuracao, catequista) e detalhe da
  // turma e mora na tela de edicao.
  alvo.innerHTML = `
    <div class="ind-tabela-rolagem">
      <table class="ind-tabela adm-lista">
        <thead>
          <tr>
            <th>Turma</th><th>Fase</th><th>Comunidade</th>
            <th class="adm-lista-num">Inscritos</th>
          </tr>
        </thead>
        <tbody>${visiveis.map(admLinhaTurma).join('')}</tbody>
      </table>
    </div>
    <p class="muted">Clique numa linha para abrir a turma.</p>
  `;
  admLigarTurmas(alvo);
};

const admLinhaTurma = (turma) => {
  const comunidade = (ADM_COMUNIDADES.find((c) => c.idComunidade === turma.idComunidade) || {}).nome;
  return `
    <tr class="adm-lista-linha${turma.pendenteDeClassificacao ? ' adm-lista-linha--pendente' : ''}"
        data-abrir-turma="${turma.idTurma}" tabindex="0" role="button"
        aria-label="Abrir a turma ${admEscape(turma.nome)}">
      <td>
        <strong>${admEscape(turma.nome)}</strong>
        <span class="muted adm-lista-sub">${admEscape(admRotuloCategoria(turma.categoria))}</span>
      </td>
      <td>${admEscape(admRotuloFase(turma))}</td>
      <td>${admEscape(comunidade || '—')}</td>
      <td class="adm-lista-num">${turma.matriculadosNoAno}</td>
    </tr>
  `;
};

/** Filtro do topo: comunidade e turma. Vale para a lista de turmas. */
const admFiltro = { idComunidade: '', idTurma: '' };

const admPreencherFiltros = () => {
  const selCom = document.getElementById('adm-filtro-comunidade');
  const selTurma = document.getElementById('adm-filtro-turma');
  if (!selCom || !selTurma) return;

  selCom.innerHTML = '<option value="">Todas as comunidades</option>' +
    ADM_COMUNIDADES.map((c) =>
      `<option value="${c.idComunidade}"${String(c.idComunidade) === admFiltro.idComunidade ? ' selected' : ''}>${admEscape(c.nome)}</option>`
    ).join('');

  // A lista de turmas encolhe com a comunidade escolhida: select com todas as
  // turmas da paroquia nao e filtro, e obstaculo.
  const visiveis = admTurmas.filter((t) =>
    !admFiltro.idComunidade || String(t.idComunidade || '') === admFiltro.idComunidade
  );
  selTurma.innerHTML = '<option value="">Todas as turmas</option>' +
    visiveis.map((t) =>
      `<option value="${t.idTurma}"${String(t.idTurma) === admFiltro.idTurma ? ' selected' : ''}>${admEscape(t.nome)}</option>`
    ).join('');
};

const admTurmasFiltradas = () => admTurmas.filter((t) => {
  if (admFiltro.idComunidade && String(t.idComunidade || '') !== admFiltro.idComunidade) return false;
  if (admFiltro.idTurma && String(t.idTurma) !== admFiltro.idTurma) return false;
  return true;
});

const admLigarTurmas = (alvo) => {
  alvo.querySelectorAll('[data-abrir-turma]').forEach((linha) => {
    const abrir = () => admAbrirTurma(Number(linha.dataset.abrirTurma));
    linha.addEventListener('click', abrir);
    // A linha e um alvo de clique; sem isto ela sai do alcance de quem navega
    // pelo teclado, e a tela de edicao fica inacessivel.
    linha.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        abrir();
      }
    });
  });
};

// ---- Tela 2: edição da turma ----------------------------------------------

/*
 * Uma turma, tres coisas: como ela e classificada, quem esta nela, e quem sai
 * dela. As duas ultimas viram abas -- ver a explicacao em admVistaTurma.
 */

const admAbrirTurma = async (idTurma) => {
  admTurmaAtual = admTurmas.find((t) => t.idTurma === idTurma) || null;
  if (!admTurmaAtual) return;

  admTela('matriculas');
  admAviso('adm-matriculas-status', '');
  document.getElementById('adm-matriculas-titulo').textContent =
    `${admTurmaAtual.nome} (${admAno()})`;
  admDesenharClassificacao();
  admVistaTurma('inscricoes');
  await admCarregarMatriculas();
};

// ---- 2a. Classificação da turma -------------------------------------------

/*
 * Grava na MUDANCA do select, sem botao.
 *
 * O "Salvar" que existia aqui era lido como "salvar o filtro" -- e nao era:
 * gravava a classificacao da turma, que e o que destrava a comunidade nos
 * Indicadores. Apagar sem mais nada tiraria a unica forma de classificar
 * turma; gravar na mudanca tira o botao E mantem a funcao.
 */

const admDesenharClassificacao = () => {
  const turma = admTurmaAtual;
  if (!turma) return;

  document.getElementById('adm-edit-categoria').innerHTML = ADM_CATEGORIAS.map((c) =>
    `<option value="${c.valor}"${(turma.categoria || '') === c.valor ? ' selected' : ''}>${admEscape(c.rotulo)}</option>`
  ).join('');

  // Comunidade dona da turma: e o que decide qual coordenador pode mexer nos
  // eventos dela na agenda.
  document.getElementById('adm-edit-comunidade').innerHTML =
    [{ v: '', r: 'Sem comunidade definida' }]
      .concat(ADM_COMUNIDADES.map((c) => ({ v: String(c.idComunidade), r: c.nome })))
      .map((c) =>
        `<option value="${c.v}"${String(turma.idComunidade || '') === c.v ? ' selected' : ''}>${admEscape(c.r)}</option>`
      ).join('');

  admDesenharFase(turma.categoria, turma.etapa);
};

/**
 * O campo de fase existe apenas nas categorias que tem fase.
 *
 * Some em vez de desabilitar: campo desabilitado ainda e uma pergunta na tela,
 * e a pergunta "qual fase?" nao existe para adultos, catecumenato,
 * pre-catequese e perseveranca.
 */
const admDesenharFase = (categoria, etapaAtual) => {
  const campo = document.getElementById('adm-edit-fase-campo');
  const select = document.getElementById('adm-edit-etapa');
  if (!campo || !select) return;

  const tem = admTemFases(categoria);
  campo.hidden = !tem;
  if (!tem) {
    select.innerHTML = '';
    return;
  }
  select.innerHTML = [{ v: '', r: 'Sem fase definida' }, { v: '1', r: ADM_ROTULO_FASE[1] }, { v: '2', r: ADM_ROTULO_FASE[2] }]
    .map((e) => `<option value="${e.v}"${String(etapaAtual || '') === e.v ? ' selected' : ''}>${e.r}</option>`)
    .join('');
};

const admClassificar = async () => {
  if (!admTurmaAtual) return;
  const idTurma = admTurmaAtual.idTurma;
  const categoria = document.getElementById('adm-edit-categoria').value || null;

  // Categoria sem fase nao guarda fase. Sem esta linha, uma turma que era
  // Eucaristia 2 e virou Adultos continuaria com etapa=2 no banco -- um valor
  // que a tela nao mostra mais e que ninguem consegue corrigir.
  const etapaBruta = admTemFases(categoria) ? document.getElementById('adm-edit-etapa').value : '';
  const etapa = etapaBruta ? Number(etapaBruta) : null;

  const comunidadeBruta = document.getElementById('adm-edit-comunidade').value;
  const idComunidade = comunidadeBruta ? Number(comunidadeBruta) : null;

  try {
    const resposta = await fetch(`/api/admin/turmas/${idTurma}/classificacao`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ categoria, etapa, idComunidade })
    });
    if (!resposta.ok) {
      admAviso('adm-matriculas-status', await admErro(resposta, 'Não foi possível salvar.'), 'error');
      return;
    }
    const turma = await resposta.json();

    // A listagem tem estes mesmos dados em memoria: sem atualizar aqui, voltar
    // para a lista mostraria a classificacao antiga sem nenhum aviso.
    admTurmaAtual = { ...admTurmaAtual, ...turma };
    const i = admTurmas.findIndex((t) => t.idTurma === idTurma);
    if (i >= 0) admTurmas[i] = admTurmaAtual;

    const salvo = document.getElementById('adm-edit-salvo');
    if (salvo) {
      salvo.hidden = false;
      clearTimeout(salvo.dataset.timer);
      salvo.dataset.timer = setTimeout(() => { salvo.hidden = true; }, 2500);
    }
    // Os destinos de transferencia dependem da categoria e da fase desta turma.
    admDesenharMatriculas();
  } catch (err) {
    admAviso('adm-matriculas-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

// ---- 2b. Abas: inscrições e transferências --------------------------------

/*
 * Transferir tirou-se da linha de cada catequisando e virou aba.
 *
 * Cada linha tinha "Salvar" e "Transferir" lado a lado, duas acoes de peso
 * muito diferente disputando o mesmo clique: uma corrige a situacao de quem
 * ficou, a outra tira a pessoa da turma. Separadas, cada aba faz uma coisa e
 * o clique errado deixa de ser possivel.
 */
const admVistaTurma = (qual) => {
  document.getElementById('adm-vista-inscricoes').hidden = qual !== 'inscricoes';
  document.getElementById('adm-vista-transferencias').hidden = qual !== 'transferencias';
  document.querySelectorAll('.adm-subnav-btn').forEach((b) => {
    b.classList.toggle('active', b.dataset.admVista === qual);
  });
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
    admMatriculas = await resposta.json();
    admDesenharMatriculas();
  } catch (err) {
    admAviso('adm-matriculas-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

const admDesenharMatriculas = () => {
  const lista = document.getElementById('adm-matriculas-lista');
  const transf = document.getElementById('adm-transferencias-lista');
  if (!lista || !transf) return;

  if (!admMatriculas.length) {
    const vazio =
      '<div class="status warning">Nenhuma inscrição neste ano. Sem inscrição não há lista de chamada.</div>';
    lista.innerHTML = vazio;
    transf.innerHTML = vazio;
    return;
  }

  lista.innerHTML = admMatriculas.map(admLinhaMatricula).join('');
  lista.querySelectorAll('[data-salvar-situacao]').forEach((b) => {
    b.addEventListener('click', () => admSalvarSituacao(Number(b.dataset.salvarSituacao)));
  });

  // Quem ja saiu nao aparece na aba de transferencia: a inscricao dele foi
  // encerrada e a ativa esta no destino. Oferecer "transferir" ali criaria
  // duas inscricoes ativas para a mesma pessoa.
  const moveis = admMatriculas.filter((m) => m.situacao !== 'TRANSFERIDO');
  transf.innerHTML = moveis.length
    ? moveis.map(admLinhaTransferencia).join('')
    : '<div class="status neutro">Ninguém nesta turma pode ser transferido agora.</div>';
  transf.querySelectorAll('[data-transferir]').forEach((b) => {
    b.addEventListener('click', () => admTransferir(Number(b.dataset.transferir)));
  });
};

/**
 * Este destino faz sentido para quem esta nesta turma?
 *
 * Espelha RegrasDeMovimentacao no servidor. As duas podem divergir com o
 * tempo -- por isso a versao do servidor e a que MANDA, e esta aqui so
 * encurta a lista.
 */
const admDestinoPlausivel = (origem, destino) => {
  if (!destino.categoria) return false;
  if (origem.categoria === 'PRE_CATEQUESE') {
    return destino.categoria === 'EUCARISTIA' && (destino.etapa || 1) === 1;
  }
  if (origem.categoria === 'PERSEVERANCA') {
    return destino.categoria === 'CRISMA' && (destino.etapa || 1) === 1;
  }
  if (origem.categoria === 'CATECUMENATO') {
    // O servidor confere se os quatro ritos foram cumpridos; aqui a opcao
    // aparece para nao esconder um caminho que existe.
    if (destino.categoria === 'ADULTOS') return true;
  }
  if (origem.categoria !== destino.categoria) return false;
  if ((origem.etapa || null) !== (destino.etapa || null)) return false;
  // Mesma fase e mesma comunidade nao e transferencia, e troca de turma.
  return !origem.idComunidade || origem.idComunidade !== destino.idComunidade;
};

/** Aba "Inscrições": situação de cada um, e só. Um botão por linha. */
const admLinhaMatricula = (m) => {
  const opcoes = ADM_SITUACOES_MATRICULA.map((s) =>
    `<option value="${s.valor}"${m.situacao === s.valor ? ' selected' : ''}>${s.rotulo}</option>`
  ).join('');

  // Transferido nao volta atras por aqui: a inscricao ja foi encerrada e
  // existe outra ativa no destino. Mexer nela criaria duas ativas.
  const transferido = m.situacao === 'TRANSFERIDO';

  return `
    <div class="adm-matricula" data-matricula="${m.idMatricula}">
      <div class="adm-matricula-topo">
        <strong>${admEscape(m.nomeCatequisando)}</strong>
        <span class="muted">desde ${admDataBR(m.dataMatricula) || '—'}</span>
        ${m.atualizadoPor ? `<span class="muted">por ${admEscape(m.atualizadoPor)}</span>` : ''}
      </div>
      ${transferido
        ? `<span class="status neutro">Transferido${m.paroquiaDestino ? ` para ${admEscape(m.paroquiaDestino)}` : ' — a inscrição ativa está na turma de destino'}.</span>`
        : `<div class="adm-matricula-acoes">
             <label>
               Situação
               <select data-situacao="${m.idMatricula}">${opcoes}</select>
             </label>
             <button type="button" data-salvar-situacao="${m.idMatricula}">Salvar</button>
           </div>`}
      ${m.observacao ? `<span class="muted">${admEscape(m.observacao)}</span>` : ''}
    </div>
  `;
};

/** Aba "Transferências": destino e um botão por linha. */
const admLinhaTransferencia = (m) => {
  // A lista de destino ja vem PODADA pela regra: mesma categoria, mesma fase,
  // outra comunidade -- mais as duas saidas de percurso (pre-catequese para
  // Eucaristia 1, perseveranca para Crisma 1) e o catecumeno que concluiu.
  //
  // Oferecer tudo e deixar o servidor recusar seria tecnicamente correto e
  // ruim de usar: a pessoa escolhe, confirma, e so entao descobre que aquele
  // destino nunca foi possivel. O servidor continua validando -- a poda aqui
  // e conforto, nao seguranca.
  const atual = admTurmaAtual || {};
  const destinos = admTurmas
    .filter((t) => t.idTurma !== atual.idTurma)
    .filter((t) => admDestinoPlausivel(atual, t))
    .map((t) => `<option value="${t.idTurma}">${admEscape(t.nome)}</option>`)
    .join('');

  return `
    <div class="adm-matricula" data-transferencia="${m.idMatricula}">
      <div class="adm-matricula-topo">
        <strong>${admEscape(m.nomeCatequisando)}</strong>
        <span class="muted">desde ${admDataBR(m.dataMatricula) || '—'}</span>
      </div>
      <div class="adm-matricula-acoes">
        <label>
          Transferir para
          <select data-destino="${m.idMatricula}">
            <option value="">Escolha o destino</option>
            ${destinos}
            <option value="OUTRA_PAROQUIA">— outra paróquia —</option>
          </select>
        </label>
        <button type="button" class="secondary" data-transferir="${m.idMatricula}">
          Transferir
        </button>
      </div>
      ${destinos ? '' : '<span class="muted">Nenhuma turma daqui recebe esta pessoa: só resta a saída para outra paróquia.</span>'}
    </div>
  `;
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

/**
 * Movimentacao do catequisando: outra turma daqui, ou outra paroquia.
 *
 * Os dois destinos sao exclusivos, e a tela pergunta na ordem certa -- primeiro
 * "saiu da paroquia?", porque quem saiu nao tem turma de destino nenhuma. As
 * regras de para-onde-pode-ir sao do servidor: a tela nao repete a validacao,
 * so mostra o motivo quando ele recusa.
 */
const admTransferir = async (idMatricula) => {
  const campo = document.querySelector(`[data-destino="${idMatricula}"]`);
  const escolha = campo ? campo.value : '';

  let corpo = null;

  if (escolha === 'OUTRA_PAROQUIA') {
    const paroquia = window.prompt(
      'Transferência para outra paróquia.\n\n' +
      'A inscrição atual será encerrada como transferida. Não é criada\n' +
      'inscrição nova aqui — a dela passa a ser de outro lugar.\n\n' +
      'Nome da paróquia de destino:'
    );
    if (paroquia === null) return;
    if (!paroquia.trim()) {
      admAviso('adm-matriculas-status', 'Diga para qual paróquia a pessoa foi.', 'warning');
      return;
    }
    corpo = { paroquiaDestino: paroquia.trim(), data: admHojeISO() };
  } else {
    const idTurmaDestino = Number(escolha);
    if (!idTurmaDestino) {
      admAviso('adm-matriculas-status', 'Escolha o destino.', 'warning');
      return;
    }
    const motivo = window.prompt(
      'Transferência de turma.\n\n' +
      'A inscrição atual será encerrada como transferida e uma nova será criada\n' +
      'na turma de destino, contando a frequência a partir de hoje.\n\n' +
      'Só é possível para a MESMA fase, em outra comunidade.\n\n' +
      'Motivo (opcional):'
    );
    if (motivo === null) return;
    corpo = { idTurmaDestino, data: admHojeISO(), motivo: motivo.trim() || null };
  }

  try {
    const resposta = await fetch(`/api/admin/matriculas/${idMatricula}/transferir`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo)
    });
    if (!resposta.ok) {
      // O servidor explica POR QUE recusou (fase diferente, idade, catecumenato
      // incompleto). Repassar a frase dele e melhor do que inventar outra aqui.
      admAviso('adm-matriculas-status', await admErro(resposta, 'Não foi possível transferir.'), 'error');
      return;
    }
    admAviso(
      'adm-matriculas-status',
      corpo.paroquiaDestino
        ? `Saída registrada: transferido para ${corpo.paroquiaDestino}.`
        : 'Transferência registrada nas duas turmas.',
      'ok'
    );
    await admCarregarMatriculas();
  } catch (err) {
    admAviso('adm-matriculas-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

/*
 * Inscrever saiu daqui de proposito.
 *
 * O que existia era um <select> com todos os catequisandos da paroquia mais uma
 * data -- e inscricao nao e isso: exige nascimento, responsavel, sacramentos e
 * a conferencia de idade do percurso. Quem chegava por este atalho criava um
 * vinculo sem nada disso. A porta e a tela de cadastro, que faz as perguntas.
 *
 * POST /api/admin/matriculas continua existindo e e usado por la.
 */

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
      '<div class="status neutro">Nenhuma inscrição em andamento neste ano. Não há o que encerrar.</div>';
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
    `Encerrar ${ids.length} inscrição(ões) de ${admPrevia.ano}.\n\n` +
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
      `${resultado.matriculasAtualizadas} inscrição(ões) encerrada(s)` +
      (resultado.etapasPromovidas ? `, ${resultado.etapasPromovidas} etapa(s) avançada(s)` : '') +
      (ignoradas.length ? `. ${ignoradas.length} ignorada(s): ${ignoradas.join(' ')}` : '.'),
      ignoradas.length ? 'warning' : 'ok'
    );
  } catch (err) {
    admAviso('adm-enc-status', `Falha de conexão: ${err.message}`, 'error');
  }
};

// ---- Ligações da tela -----------------------------------------------------

document.getElementById('adm-consultar')?.addEventListener('click', () => admCarregarTurmas(true));

// Trocar de ano invalida o que esta na tela, e recarregar sozinho traria de
// volta a lista inteira -- justamente o que o "Consultar" existe para evitar.
document.getElementById('adm-ano')?.addEventListener('change', () => {
  admConsultado = false;
  admCarregarTurmas(false).then(admDesenharTurmas);
});

document.getElementById('adm-voltar-turmas')?.addEventListener('click', () => {
  admTela('turmas');
  // A classificacao pode ter mudado enquanto a turma estava aberta.
  admDesenharTurmas();
});
document.getElementById('adm-voltar-correcao')?.addEventListener('click', () => admTela('turmas'));
document.getElementById('adm-btn-corrigir')?.addEventListener('click', admSalvarCorrecao);
document.getElementById('adm-btn-reabrir')?.addEventListener('click', admReabrir);
document.getElementById('adm-abrir-encerramento')?.addEventListener('click', admAbrirEncerramento);
document.getElementById('adm-voltar-encerramento')?.addEventListener('click', () => admTela('turmas'));
document.getElementById('adm-enc-marcar-todos')?.addEventListener('click', () => admMarcarPrevia(true));
document.getElementById('adm-enc-desmarcar')?.addEventListener('click', () => admMarcarPrevia(false));
document.getElementById('adm-btn-encerrar')?.addEventListener('click', admAplicarEncerramento);

// Abas da tela de edicao.
document.querySelectorAll('.adm-subnav-btn').forEach((b) => {
  b.addEventListener('click', () => admVistaTurma(b.dataset.admVista));
});

// Classificacao: grava na mudanca. A categoria redesenha a fase antes de
// gravar -- trocar para uma categoria sem fase precisa APAGAR a fase, e ler o
// campo velho gravaria o valor que acabou de deixar de existir.
document.getElementById('adm-edit-categoria')?.addEventListener('change', (e) => {
  admDesenharFase(e.target.value, admTurmaAtual && admTurmaAtual.etapa);
  admClassificar();
});
document.getElementById('adm-edit-etapa')?.addEventListener('change', admClassificar);
document.getElementById('adm-edit-comunidade')?.addEventListener('change', admClassificar);

/** script.js chama isto ao entrar na aba. */
window.carregarAdminCatequese = async () => {
  const campoAno = document.getElementById('adm-ano');
  if (campoAno && !campoAno.value) campoAno.value = String(new Date().getFullYear());
  admTela('turmas');
  admConsultado = false;
  // Carrega calado: os dados alimentam o filtro de turma e os destinos de
  // transferencia. A lista so aparece no "Consultar".
  await admCarregarComunidades();
  await admCarregarTurmas(false);
  admDesenharTurmas();
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

/*
 * Mudar o filtro NAO consulta.
 *
 * A tela pergunta primeiro e responde depois: quem esta montando o recorte
 * costuma mexer nos dois campos, e redesenhar a lista no meio disso mostra um
 * resultado que ninguem pediu -- as vezes vazio, e parecendo erro. O
 * "Consultar" e o unico gatilho.
 *
 * A consulta tambem nao vai ao servidor de novo: as turmas do ano ja estao em
 * memoria, e uma ida ao banco a cada clique deixaria a tela lenta sem ganhar
 * nada em correcao.
 */
document.getElementById('adm-filtro-comunidade')?.addEventListener('change', (e) => {
  admFiltro.idComunidade = e.target.value;
  // Trocar de comunidade invalida a turma escolhida: manter um vinculo que nao
  // pertence mais ao recorte devolveria uma lista vazia sem explicacao.
  admFiltro.idTurma = '';
  admPreencherFiltros();
});

document.getElementById('adm-filtro-turma')?.addEventListener('change', (e) => {
  admFiltro.idTurma = e.target.value;
});
