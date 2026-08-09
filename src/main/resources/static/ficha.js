/*
 * Página da ficha — abre em aba própria, separada da tela de consulta.
 *
 * Parâmetros aceitos na URL:
 *   ?id=123              uma ficha
 *   ?ids=1,2,3           várias fichas
 *   ?turma=5             todas as fichas de uma turma
 *   ?comunidade=2        todas as fichas de uma comunidade
 *   &print=1             abre a janela de impressão automaticamente
 */

const params = new URLSearchParams(window.location.search);

const DOC_TYPE_LABELS = {
  ASSINATURA: 'Assinatura',
  DOCUMENTO: 'Documento (RG/CPF)',
  CERTIDAO: 'Certidão de Batismo',
  FOTO: 'Foto do Catequisando'
};

// A assinatura NÃO entra na lista de anexos: ela é parte integrante da ficha
// e é exibida logo após os dados, indicando a ciência do catequisando.
const ORDEM_DOCUMENTOS = { DOCUMENTO: 0, CERTIDAO: 1, FOTO: 2 };

const ESTADO_CONJUGAL_LABELS = {
  SOLTEIRO: 'Solteiro(a)',
  CASADO_IGREJA: 'Casado(a) na Igreja',
  CASADO_CIVIL: 'Casado(a) apenas no civil',
  UNIAO_ESTAVEL: 'União estável',
  VIVE_COMPANHEIRO: 'Vive com companheiro(a)',
  SEGUNDA_UNIAO: 'Segunda união'
};

const escapeHtml = (value) => {
  const div = document.createElement('div');
  div.textContent = value === null || value === undefined ? '' : String(value);
  return div.innerHTML;
};

const formatDateSimple = (dateStr) => {
  if (!dateStr) return '—';
  const [ano, mes, dia] = String(dateStr).split('-');
  if (!ano || !mes || !dia) return dateStr;
  return `${dia}/${mes}/${ano}`;
};

const fetchJson = async (url) => {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  if (res.status === 204) return null;
  return res.json();
};

const setStatus = (texto, tipo = '') => {
  const el = document.getElementById('ficha-status');
  el.className = tipo ? `status ${tipo}` : 'muted';
  el.textContent = texto;
};

// Sem window.print() antes de as imagens estarem decodificadas, a folha sai em branco.
const aguardarImagens = (container, timeoutMs = 30000) => {
  const pendentes = Array.from(container.querySelectorAll('img'))
    .filter((img) => !(img.complete && img.naturalWidth > 0));
  if (!pendentes.length) return Promise.resolve();

  const carregadas = Promise.all(pendentes.map((img) => new Promise((resolve) => {
    img.addEventListener('load', resolve, { once: true });
    img.addEventListener('error', resolve, { once: true });
  })));
  const limite = new Promise((resolve) => setTimeout(resolve, timeoutMs));
  return Promise.race([carregadas, limite]);
};

const campo = (rotulo, valor, largo = false) => {
  const vazio = valor === null || valor === undefined || valor === '';
  return `
    <div class="ficha-campo${largo ? ' largo' : ''}">
      <span class="rotulo">${escapeHtml(rotulo)}</span>
      <span class="valor">${vazio ? '—' : escapeHtml(valor)}</span>
    </div>`;
};

const marca = (ativo, texto) =>
  `<span class="ficha-marca${ativo ? ' ativa' : ''}">${ativo ? '☑' : '☐'} ${escapeHtml(texto)}</span>`;

const buildCabecalhoFicha = (c) => `
  <header class="ficha-cabecalho">
    <div>
      <h1>FICHA DE CATEQUESE</h1>
      <p class="ficha-sub">${escapeHtml(c.nome)} · ${escapeHtml(c.turma?.nome || 'Sem turma')} · ${escapeHtml(c.comunidade?.nome || 'Sem comunidade')}</p>
    </div>
    <img src="logo.png" alt="Logo Catequese" />
  </header>
`;

const buildDadosCadastraisHtml = (c) => `
  <section class="ficha-bloco">
    <h2>Dados do catequisando</h2>
    <div class="ficha-grid">
      ${campo('Nome completo', c.nome, true)}
      ${campo('Telefone', c.telefone)}
      ${campo('Email', c.email)}
      ${campo('Data de nascimento', formatDateSimple(c.dataNascimento))}
      ${campo('Nome do responsável', c.nomeResponsavel)}
      ${campo('Telefone do responsável', c.telefoneResponsavel)}
      ${campo('Endereço', c.endereco, true)}
      ${campo('Tipo de documento', c.tipoDocumento)}
      ${campo('Número do documento', c.numeroDocumento)}
      ${campo('Turma', c.turma?.nome)}
      ${campo('Comunidade', c.comunidade?.nome)}
      ${campo('Estado civil / convivência conjugal', ESTADO_CONJUGAL_LABELS[c.estadoConjugal] || c.estadoConjugal, true)}
    </div>
    <div class="ficha-marcas">
      <span class="rotulo">Sacramentos e observações de saúde</span>
      <div class="marcas-linha">
        ${marca(c.foiBatizado, 'Batismo')}
        ${marca(c.fezPrimeiraEucaristia, 'Primeira Eucaristia')}
        ${marca(c.intoleranteGluten, 'Intolerante a glúten')}
      </div>
    </div>
  </section>
`;

const buildFichaInscricaoHtml = (fichas) => {
  const ficha = fichas[0];
  return `
    <section class="ficha-bloco">
      <h2>Ficha de inscrição</h2>
      <div class="ficha-grid">
        ${campo('Data de inscrição', ficha ? formatDateSimple(ficha.dataInscricao) : null)}
        ${campo('Nº da ficha', ficha ? ficha.idFicha : null)}
        ${campo('Observações', ficha ? ficha.observacoes : null, true)}
      </div>
    </section>
  `;
};

// Busca o anexo pelo backend (que lê do bucket) e devolve uma URL local.
const carregarArquivoDocumento = async (idDocumento) => {
  const res = await fetch(`/api/documentos/${idDocumento}/arquivo`);
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`);
  const blob = await res.blob();
  return { url: URL.createObjectURL(blob), contentType: blob.type };
};

/*
 * Assinatura: fica na mesma página dos dados, em tamanho reduzido, porque
 * vale como o "de acordo" do catequisando sobre o que foi declarado — e não
 * como um documento anexo (esses continuam ocupando a folha inteira).
 */
const buildAssinaturaHtml = async (docAssinatura, catequisando) => {
  let conteudo = '<span class="muted">Sem assinatura registrada.</span>';

  if (docAssinatura) {
    try {
      const arquivo = await carregarArquivoDocumento(docAssinatura.idDocumento);
      conteudo = arquivo.contentType && arquivo.contentType.startsWith('image/')
        ? `<img src="${arquivo.url}" alt="Assinatura de ${escapeHtml(catequisando.nome)}" />`
        : `<span class="muted">Assinatura em formato ${escapeHtml(arquivo.contentType || 'desconhecido')} — <a href="${arquivo.url}" target="_blank" rel="noopener">abrir em nova aba</a>.</span>`;
    } catch (err) {
      conteudo = `<span class="status error">Não foi possível carregar a assinatura: ${escapeHtml(err.message)}</span>`;
    }
  }

  const data = docAssinatura ? formatDateSimple(docAssinatura.dataEnvio) : null;

  return `
    <section class="ficha-bloco ficha-assinatura">
      <h2>Assinatura do catequisando</h2>
      <div class="assinatura-box">
        <div class="assinatura-traco">${conteudo}</div>
        <span class="assinatura-legenda">${escapeHtml(catequisando.nome)}${data ? ' · assinado em ' + data : ''}</span>
      </div>
    </section>`;
};

const buildDocumentosHtml = async (documentos) => {
  if (!documentos.length) {
    return `
      <section class="ficha-bloco">
        <h2>Documentos e anexos</h2>
        <p class="muted">Nenhum documento enviado.</p>
      </section>`;
  }

  const ordenados = documentos.slice().sort((a, b) =>
    (ORDEM_DOCUMENTOS[a.tipoDocumento] ?? 99) - (ORDEM_DOCUMENTOS[b.tipoDocumento] ?? 99));

  let html = '<section class="ficha-bloco"><h2>Documentos e anexos</h2><div class="ficha-docs">';

  for (const doc of ordenados) {
    const titulo = DOC_TYPE_LABELS[doc.tipoDocumento] || doc.tipoDocumento;

    html += `
      <div class="doc-item">
        <div class="doc-cabecalho">
          <span class="doc-titulo">${escapeHtml(titulo)}</span>
          <span class="doc-data">Enviado em ${formatDateSimple(doc.dataEnvio)}</span>
        </div>`;

    try {
      const arquivo = await carregarArquivoDocumento(doc.idDocumento);
      if (arquivo.contentType && arquivo.contentType.startsWith('image/')) {
        html += `<div class="doc-preview"><img src="${arquivo.url}" alt="${escapeHtml(titulo)}" /></div>`;
      } else {
        // Sem iframe: um PDF não é imagem e não pode ser exibido aqui.
        html += `
          <div class="doc-preview doc-preview-link">
            <p>Anexo em formato ${escapeHtml(arquivo.contentType || 'desconhecido')} — não é uma imagem.</p>
            <p><a href="${arquivo.url}" target="_blank" rel="noopener">Abrir em nova aba</a> para visualizar e imprimir separadamente.</p>
          </div>`;
      }
    } catch (err) {
      html += `<div class="doc-preview doc-preview-link status error">Não foi possível carregar o arquivo: ${escapeHtml(err.message)}</div>`;
    }

    html += '</div>';
  }

  html += '</div></section>';
  return html;
};

const montarFicha = async (idCatequisando) => {
  const [catequisando, fichas, documentos] = await Promise.all([
    fetchJson(`/api/catequisandos/${idCatequisando}`),
    fetchJson(`/api/fichas/catequisando/${idCatequisando}`),
    fetchJson(`/api/documentos/catequisando/${idCatequisando}`)
  ]);

  // A assinatura sai da lista de anexos e vai para o corpo da ficha.
  const assinatura = documentos.find((d) => d.tipoDocumento === 'ASSINATURA');
  const anexos = documentos.filter((d) => d.tipoDocumento !== 'ASSINATURA');

  const assinaturaHtml = await buildAssinaturaHtml(assinatura, catequisando);
  const documentosHtml = await buildDocumentosHtml(anexos);

  return `
    <div class="ficha-print">
      <article class="ficha-doc">
        ${buildCabecalhoFicha(catequisando)}
        ${buildDadosCadastraisHtml(catequisando)}
        ${buildFichaInscricaoHtml(fichas)}
        ${assinaturaHtml}
        ${documentosHtml}
      </article>
    </div>`;
};

// Resolve quais catequisandos devem ser exibidos, conforme os parâmetros da URL.
const resolverIds = async () => {
  if (params.get('id')) return [Number(params.get('id'))];

  if (params.get('ids')) {
    return params.get('ids').split(',').map(Number).filter((n) => !Number.isNaN(n) && n > 0);
  }

  const turma = params.get('turma');
  const comunidade = params.get('comunidade');
  const todos = await fetchJson('/api/catequisandos');

  return todos
    .filter((c) => !turma || String(c.turma?.idTurma ?? '') === turma)
    .filter((c) => !comunidade || String(c.comunidade?.idComunidade ?? '') === comunidade)
    .map((c) => c.idCatequisando);
};

const iniciar = async () => {
  const container = document.getElementById('fichas-container');

  let ids;
  try {
    ids = await resolverIds();
  } catch (err) {
    setStatus(`Erro ao carregar a lista: ${err.message}`, 'error');
    return;
  }

  if (!ids.length) {
    setStatus('Nenhum catequisando encontrado para os filtros informados.', 'warning');
    container.innerHTML = '';
    return;
  }

  let html = '';
  const falhas = [];

  for (let i = 0; i < ids.length; i += 1) {
    setStatus(`Carregando ficha ${i + 1} de ${ids.length}...`);
    try {
      html += await montarFicha(ids[i]);
    } catch (err) {
      falhas.push(`ID ${ids[i]}: ${err.message}`);
      html += `
        <div class="ficha-print">
          <article class="ficha-doc">
            <section class="ficha-bloco">
              <p class="status error">Não foi possível carregar a ficha do catequisando ${escapeHtml(ids[i])}: ${escapeHtml(err.message)}</p>
            </section>
          </article>
        </div>`;
    }
  }

  container.innerHTML = html;

  setStatus(`Carregando anexos de ${ids.length} ficha(s)...`);
  await aguardarImagens(container);

  if (falhas.length) {
    setStatus(`${ids.length} ficha(s) carregada(s), ${falhas.length} com erro. Confira antes de arquivar.`, 'warning');
    console.warn('Fichas com erro:', falhas);
  } else {
    setStatus(`${ids.length} ficha(s) carregada(s).`, 'ok');
  }

  document.title = ids.length === 1 ? 'Ficha de Catequese' : `Fichas de Catequese (${ids.length})`;

  if (params.get('print') === '1') window.print();
};

document.getElementById('btn-imprimir').addEventListener('click', async () => {
  const container = document.getElementById('fichas-container');
  await aguardarImagens(container);
  window.print();
});

document.getElementById('btn-voltar').addEventListener('click', () => {
  if (window.history.length > 1) window.history.back();
  else window.location.href = 'index.html';
});

iniciar();
