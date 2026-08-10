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
