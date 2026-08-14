/*
 * Ficha do catequisando para a area do catequista.
 *
 * Prefixo "fic" para nao colidir com o escopo global compartilhado.
 *
 * Nao confundir com ficha.js: aquela e a ficha de inscricao completa, com os
 * anexos, aberta em outra aba e usada por quem pode editar cadastro. Esta aqui
 * mostra apenas o STATUS de entrega de cada documento -- o backend nem envia o
 * caminho do arquivo. Foi pedido assim: o catequista precisa saber o que
 * falta, e nao ver documento de menor de idade.
 *
 * Vive como terceira tela da aba Frequencia. Substitui o resumo e a lista;
 * nunca aparece junto.
 */

let ficAtual = null;

const FIC_ROTULO_SITUACAO_MATRICULA = {
  CURSANDO: 'Cursando',
  CONCLUIDO: 'Concluído',
  NAO_CONCLUIDO: 'Não concluído',
  TRANSFERIDO: 'Transferido',
  DESISTENTE: 'Desistente'
};

/*
 * Os rotulos acentuados vivem no front. O backend manda um rotulo tambem, mas
 * em ASCII -- os .kt sao mantidos sem acento de proposito, para nao depender
 * de encoding correto em toda a cadeia (foi fonte de erro no passado). Aqui
 * usamos o mapa local e caimos no rotulo do backend so se aparecer uma etapa
 * que este front ainda nao conhece.
 */
const FIC_ROTULO_ETAPA = {
  PRE_CATECUMENATO: 'Pré-catecumenato',
  CATECUMENATO: 'Catecumenato',
  PURIFICACAO_ILUMINACAO: 'Purificação e iluminação',
  MISTAGOGIA: 'Mistagogia'
};

const FIC_CLASSE_SITUACAO_MATRICULA = {
  CURSANDO: 'ok',
  CONCLUIDO: 'ok',
  NAO_CONCLUIDO: 'error',
  TRANSFERIDO: 'neutro',
  DESISTENTE: 'neutro'
};

const ficEscape = (valor) => {
  const div = document.createElement('div');
  div.textContent = valor === null || valor === undefined ? '' : String(valor);
  return div.innerHTML;
};

const ficDataBR = (iso) => {
  if (!iso) return '';
  const p = String(iso).slice(0, 10).split('-');
  return p.length === 3 ? `${p[2]}/${p[1]}/${p[0]}` : '';
};

/** Campo vazio vira travessao: espaco em branco parece erro de carregamento. */
const ficValor = (v) => (v === null || v === undefined || String(v).trim() === '' ? '—' : v);

const ficCampo = (rotulo, valor) => `
  <div class="fic-campo">
    <span class="fic-campo-rotulo">${ficEscape(rotulo)}</span>
    <span class="fic-campo-valor">${ficEscape(ficValor(valor))}</span>
  </div>
`;

const ficErro = async (resposta, padrao) => {
  const corpo = await resposta.json().catch(() => null);
  return (corpo && corpo.erro) || padrao;
};

// ---- Abertura -------------------------------------------------------------

const ficMostrarFicha = (mostrar) => {
  const ficha = document.getElementById('freq-painel-ficha');
  const resumo = document.getElementById('freq-painel-resumo');
  const lista = document.getElementById('freq-painel-lista');
  const filtros = document.getElementById('freq-painel-filtros');
  if (ficha) ficha.hidden = !mostrar;
  if (resumo) resumo.hidden = mostrar;
  if (lista) lista.hidden = mostrar;
  if (filtros) filtros.hidden = mostrar;
};

/**
 * Duas consultas em paralelo, nao em sequencia: a ficha e a frequencia nao
 * dependem uma da outra, e enfileirar so somaria a espera das duas.
 */
const ficAbrir = async (idCatequisando, nomeProvisorio) => {
  const alvo = document.getElementById('ficha-conteudo');
  if (!alvo) return;

  ficMostrarFicha(true);
  document.getElementById('ficha-nome').textContent = nomeProvisorio || 'Ficha';
  alvo.innerHTML = '<p class="muted">Carregando a ficha...</p>';

  try {
    const [respFicha, respFreq] = await Promise.all([
      fetch(`/api/ficha-catequisando/${idCatequisando}`),
      fetch(`/api/frequencia/catequisando/${idCatequisando}/historico`)
    ]);

    if (!respFicha.ok) {
      alvo.innerHTML =
        `<div class="status error">${ficEscape(await ficErro(respFicha, 'Não foi possível abrir a ficha.'))}</div>`;
      return;
    }

    const ficha = await respFicha.json();
    // A frequencia e complemento: se ela falhar, a ficha ainda vale a pena.
    const historico = respFreq.ok ? await respFreq.json() : null;

    ficAtual = ficha;
    document.getElementById('ficha-nome').textContent = ficha.nome;
    alvo.innerHTML = ficDesenhar(ficha, historico);
  } catch (err) {
    alvo.innerHTML = `<div class="status error">Falha de conexão: ${ficEscape(err.message)}</div>`;
  }
};

// ---- Blocos ---------------------------------------------------------------

const ficBlocoDados = (f) => {
  const idade = f.idade === null || f.idade === undefined ? null : `${f.idade} anos`;
  const nascimento = f.dataNascimento
    ? `${ficDataBR(f.dataNascimento)}${idade ? ` (${idade})` : ''}`
    : null;

  const sacramentos = [
    f.foiBatizado ? 'Batizado' : null,
    f.fezPrimeiraEucaristia ? 'Primeira Eucaristia' : null
  ].filter(Boolean).join(' · ') || 'Nenhum registrado';

  return `
    <section class="fic-bloco">
      <h3>Dados</h3>
      <div class="fic-grid">
        ${ficCampo('Nascimento', nascimento)}
        ${ficCampo('Comunidade', f.nomeComunidade)}
        ${ficCampo('Turma atual', f.nomeTurmaAtual)}
        ${ficCampo('Inscrição', ficDataBR(f.dataInscricao))}
        ${ficCampo('Sacramentos', sacramentos)}
      </div>
      ${f.intoleranteGluten
        ? '<div class="status warning">Intolerante a glúten — atenção em retiros e confraternizações.</div>'
        : ''}
      ${f.ativo ? '' : '<div class="status neutro">Cadastro inativo.</div>'}
    </section>
  `;
};

/*
 * Contato. Fica em bloco proprio e no topo porque e o motivo mais comum de
 * alguem abrir a ficha: precisar avisar a familia de uma falta ou de uma
 * mudanca de horario.
 */
const ficBlocoContato = (f) => `
  <section class="fic-bloco">
    <h3>Contato</h3>
    <div class="fic-grid">
      ${ficCampo('Telefone', f.telefone)}
      ${ficCampo('E-mail', f.email)}
      ${ficCampo('Responsável', f.nomeResponsavel)}
      ${ficCampo('Telefone do responsável', f.telefoneResponsavel)}
      ${ficCampo('Endereço', f.endereco)}
    </div>
  </section>
`;

const ficBlocoDocumentos = (f) => {
  const docs = f.documentos || [];
  if (!docs.length) {
    return `
      <section class="fic-bloco">
        <h3>Documentos</h3>
        <p class="muted">Nenhum documento registrado para este catequisando.</p>
      </section>
    `;
  }

  const pendentes = docs.filter((d) => !d.entregue).length;
  const linhas = docs.map((d) => `
    <div class="fic-documento">
      <span>${ficEscape(d.tipo)}</span>
      <span class="status ${d.entregue ? 'ok' : 'warning'}">
        ${d.entregue ? `Entregue${d.dataEnvio ? ` em ${ficDataBR(d.dataEnvio)}` : ''}` : 'Pendente'}
      </span>
    </div>
  `).join('');

  return `
    <section class="fic-bloco">
      <h3>Documentos</h3>
      <p class="muted">
        ${pendentes === 0
          ? 'Todos os documentos foram entregues.'
          : `${pendentes} documento(s) ainda pendente(s).`}
        Aqui aparece apenas o que foi entregue — os arquivos ficam com a secretaria.
      </p>
      <div class="fic-documentos">${linhas}</div>
    </section>
  `;
};

const ficBlocoEtapas = (f) => {
  const etapas = f.historicoEtapas || [];
  if (!etapas.length && !f.etapaAtual) return '';

  const linhas = etapas.map((e) => `
    <div class="fic-etapa${e.emAndamento ? ' fic-etapa--atual' : ''}">
      <span class="fic-etapa-nome">
        ${ficEscape(FIC_ROTULO_ETAPA[e.etapa] || e.rotulo)}
        ${e.emAndamento ? '<span class="tag">etapa atual</span>' : ''}
        ${e.exigeFrequencia ? '' : '<span class="status neutro">sem exigência de frequência</span>'}
      </span>
      <span class="muted">
        ${ficDataBR(e.inicio) || '—'} até ${e.fim ? ficDataBR(e.fim) : 'hoje'}
        ${e.registradoPor ? ` · registrado por ${ficEscape(e.registradoPor)}` : ''}
      </span>
      ${e.observacao ? `<span class="muted">${ficEscape(e.observacao)}</span>` : ''}
    </div>
  `).join('');

  return `
    <section class="fic-bloco">
      <h3>Caminho no catecumenato</h3>
      ${linhas || '<p class="muted">Nenhuma etapa registrada ainda.</p>'}
    </section>
  `;
};

/**
 * Percurso e frequencia num bloco so: sao a mesma pergunta vista de dois
 * angulos -- "por onde passou" e "como foi em cada passagem".
 */
const ficBlocoPercurso = (f, historico) => {
  const matriculas = f.historicoMatriculas || [];
  if (!matriculas.length) {
    return `
      <section class="fic-bloco">
        <h3>Percurso</h3>
        <p class="muted">
          Nenhuma matrícula registrada. Sem matrícula não há frequência a apurar —
          quem matricula é o coordenador paroquial.
        </p>
      </section>
    `;
  }

  const porAnoTurma = new Map();
  (historico || []).forEach((h) => {
    porAnoTurma.set(`${h.ano}-${h.idTurma}`, h);
  });

  const linhas = matriculas.map((m) => {
    const freq = porAnoTurma.get(`${m.ano}-${m.idTurma}`);
    const situacao = FIC_ROTULO_SITUACAO_MATRICULA[m.situacao] || m.situacao;
    const classe = FIC_CLASSE_SITUACAO_MATRICULA[m.situacao] || '';

    const numeros = freq
      ? `<span class="fic-percurso-freq">
           <strong>${freq.percentualAtual === null || freq.percentualAtual === undefined ? '—' : `${freq.percentualAtual}%`}</strong>
           ${freq.podeConcluir ? '' : '<span class="status error">Não conclui neste ano</span>'}
         </span>`
      : '<span class="muted">frequência não apurada</span>';

    return `
      <div class="fic-percurso">
        <span class="fic-percurso-turma">
          <strong>${ficEscape(m.nomeTurma || 'Turma removida')}</strong>
          <span class="muted">${m.ano}</span>
        </span>
        ${numeros}
        <span class="status ${classe}">${ficEscape(situacao)}</span>
      </div>
    `;
  }).join('');

  return `
    <section class="fic-bloco">
      <h3>Percurso e frequência</h3>
      ${linhas}
      <p class="muted">
        O detalhe por período fica na tela de frequência da turma.
      </p>
    </section>
  `;
};

const ficDesenhar = (f, historico) => [
  ficBlocoContato(f),
  ficBlocoDados(f),
  ficBlocoDocumentos(f),
  ficBlocoEtapas(f),
  ficBlocoPercurso(f, historico)
].join('');

// ---- Ligações da tela -----------------------------------------------------

document.getElementById('ficha-voltar')?.addEventListener('click', () => {
  ficMostrarFicha(false);
  ficAtual = null;
});

/** frequencia.js e chamada.js chamam isto ao clicar num nome. */
window.abrirFichaCatequisando = ficAbrir;
