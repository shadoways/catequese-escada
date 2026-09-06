/*
 * Aba de configuracoes (somente coordenador paroquial).
 * Hoje so tem o interruptor do cadastro publico.
 *
 * Nomes com prefixo cfg para nao colidir com script.js e usuarios.js, que
 * compartilham o mesmo escopo global no index.html.
 */

const cfgStatus = (texto, tipo = '') => {
  const caixa = document.getElementById('config-status');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${texto}</div>` : '';
};

const cfgAtualizarRotulo = () => {
  const marcado = document.getElementById('config-cadastro-aberto').checked;
  document.getElementById('config-cadastro-rotulo').textContent =
    marcado ? 'Inscrições abertas' : 'Inscrições encerradas';
};

const carregarConfiguracoes = async () => {
  cfgStatus('Carregando...');
  try {
    const resposta = await fetch('/api/config/cadastro');
    if (!resposta.ok) {
      cfgStatus('Não foi possível carregar a configuração.', 'error');
      return;
    }
    const dados = await resposta.json();
    document.getElementById('config-cadastro-aberto').checked = Boolean(dados.cadastroAberto);
    cfgAtualizarRotulo();
    cfgStatus('');
    await Promise.all([carregarChaves(), cfgCarregarFormacao(), cfgCarregarConhecimentosExigidos()]);
  } catch (err) {
    cfgStatus(`Erro de conexão: ${err.message}`, 'error');
  }
};
// script.js chama esta função ao abrir a aba.
window.carregarConfiguracoes = carregarConfiguracoes;

const cfgSalvar = async () => {
  const botao = document.getElementById('btn-salvar-config');
  const aberto = document.getElementById('config-cadastro-aberto').checked;

  // Fechar o cadastro é visível para qualquer pessoa que acesse o site:
  // vale confirmar antes.
  if (!aberto && !window.confirm(
    'Encerrar as inscrições?\n\n' +
    'A tela pública de cadastro deixará de aceitar novas fichas até você reabrir.'
  )) {
    document.getElementById('config-cadastro-aberto').checked = true;
    cfgAtualizarRotulo();
    return;
  }

  botao.disabled = true;
  cfgStatus('');

  try {
    const resposta = await fetch('/api/config/cadastro', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ aberto })
    });

    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      cfgStatus((corpo && corpo.erro) || 'Não foi possível salvar.', 'error');
      return;
    }

    const dados = await resposta.json();
    // Mantém a tela de cadastro coerente sem precisar recarregar a página.
    if (typeof window.definirCadastroAberto === 'function') {
      window.definirCadastroAberto(Boolean(dados.cadastroAberto));
    }
    cfgStatus(
      dados.cadastroAberto
        ? 'Inscrições abertas. O cadastro público está no ar.'
        : 'Inscrições encerradas. O cadastro público não aceita novas fichas.',
      'ok'
    );
  } catch (err) {
    cfgStatus(`Erro de conexão: ${err.message}`, 'error');
  } finally {
    botao.disabled = false;
  }
};

const cfgEl = document.getElementById('config-cadastro-aberto');
if (cfgEl) cfgEl.addEventListener('change', cfgAtualizarRotulo);

const cfgBotao = document.getElementById('btn-salvar-config');
if (cfgBotao) cfgBotao.addEventListener('click', cfgSalvar);

// ---- Chaves de inscrição -----------------------------------------------
// Liberam o formulário público. Ficam aqui, junto do interruptor de
// inscrições, porque as duas coisas governam o mesmo cadastro público.

const cfgEscape = (valor) => {
  const div = document.createElement('div');
  div.textContent = valor === null || valor === undefined ? '' : String(valor);
  return div.innerHTML;
};

const cfgStatusChaves = (texto, tipo = '') => {
  const caixa = document.getElementById('chaves-status');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${cfgEscape(texto)}</div>` : '';
};

const cfgDataHora = (iso) => {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });
};

const carregarChaves = async () => {
  cfgStatusChaves('Carregando chaves...');
  try {
    const resposta = await fetch('/api/chaves');
    if (!resposta.ok) {
      cfgStatusChaves('Não foi possível carregar as chaves.', 'error');
      return;
    }
    cfgRenderChaves(await resposta.json());
    cfgStatusChaves('');
  } catch (err) {
    cfgStatusChaves(`Erro de conexão: ${err.message}`, 'error');
  }
};

const cfgRenderChaves = (chaves) => {
  const lista = document.getElementById('chaves-lista');
  if (!lista) return;

  if (!chaves.length) {
    lista.innerHTML = '<p class="muted">Nenhuma chave criada ainda.</p>';
    return;
  }

  lista.innerHTML = chaves.map((c) => {
    const usos = c.limiteUsos ? `${c.usos} de ${c.limiteUsos} cadastros` : `${c.usos} cadastros`;
    return `
      <div class="result-item usuario-item">
        <div class="usuario-dados">
          <span class="nome codigo-chave">${cfgEscape(c.codigo)}</span>
          <span class="meta">
            ${c.descricao ? cfgEscape(c.descricao) + ' · ' : ''}
            vale até ${cfgEscape(cfgDataHora(c.expiraEm))} · ${cfgEscape(usos)}
          </span>
          <span class="docs">
            <span class="doc-status ${c.utilizavel ? '' : 'faltando'}">${cfgEscape(c.situacao)}</span>
          </span>
        </div>
        <div class="usuario-acoes">
          <button type="button" class="secondary" data-copiar="${cfgEscape(c.link)}">Copiar link</button>
          ${c.utilizavel
            ? `<button type="button" class="secondary" data-revogar="${c.idChave}">Revogar</button>`
            : ''}
        </div>
      </div>`;
  }).join('');

  lista.querySelectorAll('[data-copiar]').forEach((b) =>
    b.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(b.dataset.copiar);
        b.textContent = 'Copiado!';
        setTimeout(() => { b.textContent = 'Copiar link'; }, 1500);
      } catch (err) {
        cfgStatusChaves(`Copie manualmente: ${b.dataset.copiar}`, 'warning');
      }
    }));

  lista.querySelectorAll('[data-revogar]').forEach((b) =>
    b.addEventListener('click', () => cfgRevogarChave(Number(b.dataset.revogar))));
};

const cfgRevogarChave = async (id) => {
  // Revogar derruba na hora quem estiver com o link: vale confirmar.
  if (!window.confirm(
    'Revogar esta chave?\n\nQuem tiver o link deixa de conseguir enviar o cadastro.'
  )) return;

  try {
    const resposta = await fetch(`/api/chaves/${id}/revogar`, { method: 'POST' });
    if (!resposta.ok) {
      cfgStatusChaves('Não foi possível revogar a chave.', 'error');
      return;
    }
    await carregarChaves();
    cfgStatusChaves('Chave revogada.', 'ok');
  } catch (err) {
    cfgStatusChaves(`Erro de conexão: ${err.message}`, 'error');
  }
};

const cfgCriarChave = async () => {
  const botao = document.getElementById('btn-criar-chave');
  const limite = document.getElementById('chave-limite').value.trim();
  const corpo = {
    descricao: document.getElementById('chave-descricao').value.trim() || null,
    validadeDias: Number(document.getElementById('chave-validade').value) || 30,
    limiteUsos: limite ? Number(limite) : null
  };

  botao.disabled = true;
  try {
    const resposta = await fetch('/api/chaves', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo)
    });
    if (!resposta.ok) {
      const erro = await resposta.json().catch(() => null);
      cfgStatusChaves((erro && erro.erro) || 'Não foi possível criar a chave.', 'error');
      return;
    }
    const chave = await resposta.json();
    document.getElementById('form-chave').hidden = true;
    document.getElementById('chave-descricao').value = '';
    document.getElementById('chave-limite').value = '';
    await carregarChaves();
    cfgStatusChaves(`Chave ${chave.codigo} criada. Use "Copiar link" para divulgar.`, 'ok');
  } catch (err) {
    cfgStatusChaves(`Erro de conexão: ${err.message}`, 'error');
  } finally {
    botao.disabled = false;
  }
};

const cfgLigar = (id, evento, funcao) => {
  const el = document.getElementById(id);
  if (el) el.addEventListener(evento, funcao);
};

cfgLigar('btn-nova-chave', 'click', () => {
  document.getElementById('form-chave').hidden = false;
  document.getElementById('chave-descricao').focus();
});
cfgLigar('btn-cancelar-chave', 'click', () => {
  document.getElementById('form-chave').hidden = true;
});
cfgLigar('btn-criar-chave', 'click', cfgCriarChave);

// ---- Conhecimento mínimo do catequista (Consultar Catequistas) ------------
// Configurável a pedido do Gabriel -- diferente do mínimo de frequência de
// turma, que é regra da catequese e não muda por tela nenhuma.

const cfgStatusFormacao = (texto, tipo = '') => {
  const caixa = document.getElementById('cfg-formacao-status');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${cfgEscape(texto)}</div>` : '';
};

const cfgCarregarFormacao = async () => {
  try {
    const resposta = await fetch('/api/config/formacao');
    if (!resposta.ok) {
      cfgStatusFormacao('Não foi possível carregar esta configuração.', 'error');
      return;
    }
    const dados = await resposta.json();
    document.getElementById('cfg-formacao-minimo').value = dados.minimoAgregado;
    document.getElementById('cfg-formacao-fechamento-mes').value = dados.fechamentoMes;
    document.getElementById('cfg-formacao-alerta').value = dados.alertaMesesAntes;
  } catch (err) {
    cfgStatusFormacao(`Erro de conexão: ${err.message}`, 'error');
  }
};

const cfgSalvarFormacao = async () => {
  const botao = document.getElementById('btn-salvar-config-formacao');
  const corpo = {
    minimoAgregado: Number(document.getElementById('cfg-formacao-minimo').value),
    fechamentoMes: Number(document.getElementById('cfg-formacao-fechamento-mes').value),
    alertaMesesAntes: Number(document.getElementById('cfg-formacao-alerta').value)
  };

  botao.disabled = true;
  cfgStatusFormacao('');
  try {
    const resposta = await fetch('/api/config/formacao', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo)
    });
    if (!resposta.ok) {
      const erro = await resposta.json().catch(() => null);
      cfgStatusFormacao((erro && erro.erro) || 'Não foi possível salvar.', 'error');
      return;
    }
    cfgStatusFormacao('Configuração salva.', 'ok');
  } catch (err) {
    cfgStatusFormacao(`Erro de conexão: ${err.message}`, 'error');
  } finally {
    botao.disabled = false;
  }
};

cfgLigar('btn-salvar-config-formacao', 'click', cfgSalvarFormacao);

// ---- Conhecimentos exigidos do catequista (Consultar Catequistas) ---------
// Catálogo à parte de `tb_conhecimento_catequista` (entidade antiga, não
// usada em nenhuma tela) -- ver a KDoc de RequisitoConhecimento.kt. Aqui o
// coordenador paroquial cadastra, renomeia e inativa o que a paróquia exige
// de todo catequista; "inativar" nunca apaga a marcação de quem já tinha
// aquele conhecimento (regra do projeto, nada é apagado de verdade).

let cfgConhecimentosExigidos = [];

const cfgStatusConhecimentos = (texto, tipo = '') => {
  const caixa = document.getElementById('cfg-conhecimentos-status');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${cfgEscape(texto)}</div>` : '';
};

const cfgCarregarConhecimentosExigidos = async () => {
  try {
    const resposta = await fetch('/api/conhecimentos-exigidos');
    if (!resposta.ok) {
      cfgStatusConhecimentos('Não foi possível carregar os conhecimentos exigidos.', 'error');
      return;
    }
    cfgConhecimentosExigidos = await resposta.json();
    cfgRenderConhecimentosExigidos();
  } catch (err) {
    cfgStatusConhecimentos(`Erro de conexão: ${err.message}`, 'error');
  }
};

const cfgRenderConhecimentosExigidos = () => {
  const lista = document.getElementById('cfg-conhecimentos-lista');
  if (!lista) return;

  if (!cfgConhecimentosExigidos.length) {
    lista.innerHTML = '<p class="muted">Nenhum conhecimento cadastrado ainda.</p>';
    return;
  }

  lista.innerHTML = cfgConhecimentosExigidos.map((c) => `
    <div class="result-item usuario-item">
      <div class="usuario-dados">
        <span class="nome">${cfgEscape(c.nome)}</span>
        <span class="status ${c.ativo ? 'ok' : 'neutro'}">${c.ativo ? 'Ativo' : 'Inativo'}</span>
      </div>
      <div class="usuario-acoes">
        <button type="button" class="secondary" data-alternar="${c.idRequisito}">
          ${c.ativo ? 'Inativar' : 'Reativar'}
        </button>
      </div>
    </div>`).join('');

  lista.querySelectorAll('[data-alternar]').forEach((b) =>
    b.addEventListener('click', () => cfgAlternarConhecimentoExigido(Number(b.dataset.alternar))));
};

const cfgCriarConhecimentoExigido = async () => {
  const campo = document.getElementById('cfg-conhecimento-nome');
  const botao = document.getElementById('btn-criar-conhecimento');
  const nome = campo.value.trim();
  if (!nome) {
    cfgStatusConhecimentos('Digite o nome do conhecimento.', 'error');
    return;
  }

  botao.disabled = true;
  cfgStatusConhecimentos('');
  try {
    const resposta = await fetch('/api/conhecimentos-exigidos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome })
    });
    if (!resposta.ok) {
      const erro = await resposta.json().catch(() => null);
      cfgStatusConhecimentos((erro && erro.erro) || 'Não foi possível criar o conhecimento.', 'error');
      return;
    }
    campo.value = '';
    await cfgCarregarConhecimentosExigidos();
    cfgStatusConhecimentos('Conhecimento adicionado.', 'ok');
  } catch (err) {
    cfgStatusConhecimentos(`Erro de conexão: ${err.message}`, 'error');
  } finally {
    botao.disabled = false;
  }
};

const cfgAlternarConhecimentoExigido = async (id) => {
  const atual = cfgConhecimentosExigidos.find((c) => c.idRequisito === id);
  if (!atual) return;

  // Inativar tira o item do checklist de quem ainda não tinha marcado --
  // vale confirmar, porque o efeito aparece na hora na tela de outra pessoa.
  if (atual.ativo && !window.confirm(
    `Inativar "${atual.nome}"?\n\nEle sai do checklist de Consultar Catequistas até ser reativado.`
  )) return;

  cfgStatusConhecimentos('');
  try {
    const resposta = await fetch(`/api/conhecimentos-exigidos/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome: atual.nome, ativo: !atual.ativo })
    });
    if (!resposta.ok) {
      const erro = await resposta.json().catch(() => null);
      cfgStatusConhecimentos((erro && erro.erro) || 'Não foi possível salvar.', 'error');
      return;
    }
    await cfgCarregarConhecimentosExigidos();
    cfgStatusConhecimentos(atual.ativo ? 'Conhecimento inativado.' : 'Conhecimento reativado.', 'ok');
  } catch (err) {
    cfgStatusConhecimentos(`Erro de conexão: ${err.message}`, 'error');
  }
};

cfgLigar('btn-criar-conhecimento', 'click', cfgCriarConhecimentoExigido);
