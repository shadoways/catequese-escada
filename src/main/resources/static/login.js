/* Tela de login + pedido de recuperacao de senha. */

const params = new URLSearchParams(window.location.search);
const destino = params.get('destino');

const aviso = (idCaixa, texto, tipo = 'error') => {
  const caixa = document.getElementById(idCaixa);
  caixa.innerHTML = texto ? `<div class="status ${tipo}">${texto}</div>` : '';
};

const lerErro = async (resposta, padrao) => {
  const corpo = await resposta.json().catch(() => null);
  return (corpo && (corpo.erro || corpo.mensagem)) || padrao;
};

/** Para onde ir depois de entrar. */
const seguir = (usuario) => {
  if (usuario && usuario.senhaProvisoria) {
    window.location.href = 'trocar-senha.html?obrigatoria=1';
    return;
  }
  window.location.href = destino ? `index.html?tab=${encodeURIComponent(destino)}` : 'index.html';
};

// Quem chegou aqui por sessao expirada merece saber o motivo.
if (params.get('expirou')) {
  aviso('login-aviso', 'Sua sessão expirou. Entre novamente.', 'warning');
}
if (params.get('senhaRedefinida')) {
  aviso('login-aviso', 'Senha redefinida. Entre com a nova senha.', 'ok');
}

document.getElementById('form-login').addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const botao = document.getElementById('btn-entrar');
  const username = document.getElementById('login-username').value.trim();
  const senha = document.getElementById('login-senha').value;

  if (!username || !senha) {
    aviso('login-aviso', 'Informe usuário e senha.');
    return;
  }

  botao.disabled = true;
  aviso('login-aviso', '');

  try {
    const resposta = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password: senha })
    });

    if (!resposta.ok) {
      // 423 = bloqueio por tentativas; a mensagem do backend ja diz quanto falta.
      aviso('login-aviso', await lerErro(resposta, 'Não foi possível entrar.'));
      document.getElementById('login-senha').value = '';
      return;
    }

    const dados = await resposta.json();
    Auth.salvarSessao(dados);
    seguir(dados);
  } catch (err) {
    aviso('login-aviso', `Erro de conexão: ${err.message}`);
  } finally {
    botao.disabled = false;
  }
});

// ---- Recuperacao de senha ----
const painelLogin = document.getElementById('painel-login');
const painelEsqueci = document.getElementById('painel-esqueci');

const mostrar = (qualPainel) => {
  painelLogin.hidden = qualPainel !== 'login';
  painelEsqueci.hidden = qualPainel !== 'esqueci';
};

document.getElementById('link-esqueci').addEventListener('click', () => {
  aviso('esqueci-aviso', '');
  mostrar('esqueci');
  document.getElementById('esqueci-email').focus();
});

document.getElementById('link-voltar-login').addEventListener('click', () => mostrar('login'));

document.getElementById('form-esqueci').addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const botao = document.getElementById('btn-enviar-link');
  const email = document.getElementById('esqueci-email').value.trim();
  if (!email) return;

  botao.disabled = true;
  aviso('esqueci-aviso', '');

  try {
    const resposta = await fetch('/api/auth/esqueci-senha', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email })
    });
    const corpo = await resposta.json().catch(() => null);

    // A resposta e sempre a mesma, exista o e-mail ou nao: a tela nao pode
    // revelar quais endereços tem conta.
    aviso(
      'esqueci-aviso',
      (corpo && corpo.mensagem) ||
        'Se houver uma conta com esse e-mail, enviaremos as instruções.',
      'ok'
    );
    document.getElementById('esqueci-email').value = '';
  } catch (err) {
    aviso('esqueci-aviso', `Erro de conexão: ${err.message}`);
  } finally {
    botao.disabled = false;
  }
});
