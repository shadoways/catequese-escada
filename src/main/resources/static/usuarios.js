/*
 * Tela de gestao de usuarios (somente coordenador paroquial).
 *
 * Carregada junto com script.js no index.html, entao todo nome aqui usa o
 * prefixo "usr" para nao colidir com o que ja existe no escopo global.
 *
 * A tela esconde o que o usuario nao pode fazer, mas quem realmente bloqueia e
 * o backend: /api/usuarios/** exige administrador mesmo com as restricoes
 * gerais desligadas.
 */

let usrLista = [];
let usrEditandoId = null;

const usrRotuloTipo = {
  CATEQUISTA: 'Catequista',
  COORDENADOR: 'Coordenador',
  COORDENADOR_PAROQUIAL: 'Coordenador paroquial'
};

const usrEscape = (valor) => {
  const div = document.createElement('div');
  div.textContent = valor === null || valor === undefined ? '' : String(valor);
  return div.innerHTML;
};

const usrStatus = (texto, tipo = '') => {
  const caixa = document.getElementById('usuarios-status');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${usrEscape(texto)}</div>` : '';
};

const usrAvisoForm = (texto, tipo = 'error') => {
  const caixa = document.getElementById('form-usuario-aviso');
  if (caixa) caixa.innerHTML = texto ? `<div class="status ${tipo}">${usrEscape(texto)}</div>` : '';
};

const usrDataCurta = (iso) => {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d.toLocaleDateString('pt-BR');
};

/** Le a resposta de erro do backend, que sempre vem como {erro: "..."} */
const usrErro = async (resposta, padrao) => {
  const corpo = await resposta.json().catch(() => null);
  return (corpo && corpo.erro) || padrao;
};

// ---- Carregamento e listagem ----------------------------------------------

const carregarUsuarios = async () => {
  usrStatus('Carregando usuários...');
  try {
    const resposta = await fetch('/api/usuarios');
    if (!resposta.ok) {
      usrStatus(await usrErro(resposta, 'Não foi possível carregar os usuários.'), 'error');
      return;
    }
    usrLista = await resposta.json();
    usrStatus('');
    usrRenderLista();
  } catch (err) {
    usrStatus(`Erro de conexão: ${err.message}`, 'error');
  }
};
// script.js chama esta função ao abrir a aba.
window.carregarUsuarios = carregarUsuarios;

const usrFiltrados = () => {
  const busca = (document.getElementById('usuario-busca').value || '').trim().toLowerCase();
  const situacao = document.getElementById('usuario-filtro-situacao').value;

  return usrLista
    .filter((u) => {
      if (situacao === 'ativos') return u.ativo;
      if (situacao === 'inativos') return !u.ativo;
      return true;
    })
    .filter((u) => {
      if (!busca) return true;
      return `${u.nome} ${u.username}`.toLowerCase().includes(busca);
    });
};

const usrEtiquetas = (u) => {
  const etiquetas = [];
  if (!u.ativo) etiquetas.push('<span class="doc-status faltando">Inativo</span>');
  if (u.bloqueado) etiquetas.push('<span class="doc-status faltando">Bloqueado</span>');
  if (u.senhaProvisoria) etiquetas.push('<span class="doc-status">Senha provisória</span>');
  if (!u.email) etiquetas.push('<span class="doc-status faltando">Sem e-mail</span>');
  return etiquetas.join(' ');
};

const usrRenderLista = () => {
  const container = document.getElementById('usuarios-lista');
  const itens = usrFiltrados();

  if (!itens.length) {
    container.innerHTML = '<p class="muted">Nenhum usuário encontrado para este filtro.</p>';
    return;
  }

  container.innerHTML = itens.map((u) => {
    const ultimo = usrDataCurta(u.ultimoLogin);
    return `
      <div class="result-item usuario-item">
        <div class="usuario-dados">
          <span class="nome">${usrEscape(u.nome)}</span>
          <span class="meta">
            ${usrEscape(u.username)} ·
            ${usrEscape(usrRotuloTipo[u.tipo] || u.tipo)}
            ${u.email ? ' · ' + usrEscape(u.email) : ''}
            ${ultimo ? ' · último acesso em ' + ultimo : ' · nunca acessou'}
          </span>
          <span class="docs">${usrEtiquetas(u)}</span>
        </div>
        <div class="usuario-acoes">
          <button type="button" class="secondary" data-editar="${u.idUsuario}">Editar</button>
          <button type="button" class="secondary" data-resetar="${u.idUsuario}">Resetar senha</button>
          ${u.bloqueado
            ? `<button type="button" class="secondary" data-desbloquear="${u.idUsuario}">Desbloquear</button>`
            : ''}
        </div>
      </div>
    `;
  }).join('');

  container.querySelectorAll('[data-editar]').forEach((b) =>
    b.addEventListener('click', () => usrAbrirFormulario(Number(b.dataset.editar))));
  container.querySelectorAll('[data-resetar]').forEach((b) =>
    b.addEventListener('click', () => usrResetarSenha(Number(b.dataset.resetar))));
  container.querySelectorAll('[data-desbloquear]').forEach((b) =>
    b.addEventListener('click', () => usrDesbloquear(Number(b.dataset.desbloquear))));
};

// ---- Formulario ------------------------------------------------------------

const usrAbrirFormulario = (id) => {
  usrEditandoId = id || null;
  const usuario = id ? usrLista.find((u) => u.idUsuario === id) : null;

  document.getElementById('titulo-form-usuario').textContent =
    usuario ? `Editar ${usuario.nome}` : 'Novo usuário';
  document.getElementById('usuario-nome').value = usuario ? usuario.nome : '';
  document.getElementById('usuario-username').value = usuario ? usuario.username : '';
  document.getElementById('usuario-email').value = (usuario && usuario.email) || '';
  document.getElementById('usuario-telefone').value = (usuario && usuario.telefone) || '';
  document.getElementById('usuario-tipo').value = usuario ? usuario.tipo : 'CATEQUISTA';
  document.getElementById('usuario-ativo').value = usuario ? String(usuario.ativo) : 'true';

  // O login identifica o usuario e nao muda depois de criado.
  document.getElementById('usuario-username').disabled = Boolean(usuario);
  // Situacao e senha so fazem sentido em contextos diferentes.
  document.getElementById('campo-usuario-ativo').hidden = !usuario;
  document.getElementById('dica-senha-nova').hidden = Boolean(usuario);

  usrAvisoForm('');
  document.getElementById('painel-form-usuario').hidden = false;
  document.getElementById('painel-senha-gerada').hidden = true;
  document.getElementById('usuario-nome').focus();
};

const usrFecharFormulario = () => {
  document.getElementById('painel-form-usuario').hidden = true;
  usrEditandoId = null;
};

const usrSalvar = async () => {
  const botao = document.getElementById('btn-salvar-usuario');
  const corpo = {
    nome: document.getElementById('usuario-nome').value.trim(),
    email: document.getElementById('usuario-email').value.trim() || null,
    telefone: document.getElementById('usuario-telefone').value.trim() || null,
    tipo: document.getElementById('usuario-tipo').value
  };

  if (!corpo.nome) {
    usrAvisoForm('Informe o nome do usuário.');
    return;
  }

  let url = '/api/usuarios';
  let metodo = 'POST';
  if (usrEditandoId) {
    url = `/api/usuarios/${usrEditandoId}`;
    metodo = 'PUT';
    corpo.ativo = document.getElementById('usuario-ativo').value === 'true';
  } else {
    corpo.username = document.getElementById('usuario-username').value.trim();
    if (!corpo.username) {
      usrAvisoForm('Informe o login do usuário.');
      return;
    }
  }

  botao.disabled = true;
  usrAvisoForm('');

  try {
    const resposta = await fetch(url, {
      method: metodo,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(corpo)
    });

    if (!resposta.ok) {
      usrAvisoForm(await usrErro(resposta, 'Não foi possível salvar.'));
      return;
    }

    const dados = await resposta.json();
    usrFecharFormulario();
    await carregarUsuarios();

    // Na criação o backend devolve a senha provisória — mostrada uma única vez.
    if (dados.senhaProvisoria && dados.usuario) {
      usrMostrarSenha(dados, `Usuário ${dados.usuario.nome} criado.`);
    } else {
      usrStatus('Usuário atualizado.', 'ok');
    }
  } catch (err) {
    usrAvisoForm(`Erro de conexão: ${err.message}`);
  } finally {
    botao.disabled = false;
  }
};

// ---- Senha provisoria ------------------------------------------------------

const usrMostrarSenha = (dados, contexto) => {
  document.getElementById('senha-gerada-aviso').textContent = `${contexto} ${dados.aviso || ''}`.trim();
  document.getElementById('senha-gerada-valor').textContent = dados.senhaProvisoria;
  document.getElementById('painel-senha-gerada').hidden = false;
  window.scrollTo({ top: 0, behavior: 'smooth' });
};

const usrResetarSenha = async (id) => {
  const usuario = usrLista.find((u) => u.idUsuario === id);
  const nome = usuario ? usuario.nome : 'este usuário';
  if (!window.confirm(
    `Gerar uma nova senha provisória para ${nome}?\n\n` +
    'A senha atual deixa de funcionar e a pessoa será desconectada.'
  )) return;

  try {
    const resposta = await fetch(`/api/usuarios/${id}/resetar-senha`, { method: 'POST' });
    if (!resposta.ok) {
      usrStatus(await usrErro(resposta, 'Não foi possível resetar a senha.'), 'error');
      return;
    }
    const dados = await resposta.json();
    await carregarUsuarios();
    usrMostrarSenha(dados, `Senha de ${nome} redefinida.`);
  } catch (err) {
    usrStatus(`Erro de conexão: ${err.message}`, 'error');
  }
};

const usrDesbloquear = async (id) => {
  try {
    const resposta = await fetch(`/api/usuarios/${id}/desbloquear`, { method: 'POST' });
    if (!resposta.ok) {
      usrStatus(await usrErro(resposta, 'Não foi possível desbloquear.'), 'error');
      return;
    }
    await carregarUsuarios();
    usrStatus('Usuário desbloqueado.', 'ok');
  } catch (err) {
    usrStatus(`Erro de conexão: ${err.message}`, 'error');
  }
};

// ---- Ligacoes de tela ------------------------------------------------------

const usrLigar = (id, evento, funcao) => {
  const el = document.getElementById(id);
  if (el) el.addEventListener(evento, funcao);
};

usrLigar('btn-novo-usuario', 'click', () => usrAbrirFormulario(null));
usrLigar('btn-salvar-usuario', 'click', usrSalvar);
usrLigar('btn-cancelar-usuario', 'click', usrFecharFormulario);
usrLigar('usuario-busca', 'input', usrRenderLista);
usrLigar('usuario-filtro-situacao', 'change', usrRenderLista);
usrLigar('btn-fechar-senha', 'click', () => {
  document.getElementById('painel-senha-gerada').hidden = true;
});
usrLigar('btn-copiar-senha', 'click', async () => {
  const senha = document.getElementById('senha-gerada-valor').textContent;
  try {
    await navigator.clipboard.writeText(senha);
    document.getElementById('btn-copiar-senha').textContent = 'Copiada!';
    setTimeout(() => {
      const b = document.getElementById('btn-copiar-senha');
      if (b) b.textContent = 'Copiar senha';
    }, 1500);
  } catch (err) {
    // Sem permissão de área de transferência: a senha continua visível na tela.
    usrStatus('Não foi possível copiar. Selecione e copie manualmente.', 'warning');
  }
});
