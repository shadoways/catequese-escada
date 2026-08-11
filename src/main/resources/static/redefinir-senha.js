/* Redefinicao de senha pelo link recebido por e-mail.
   Esta tela e publica: quem chega aqui ainda nao consegue entrar no sistema. */

const token = new URLSearchParams(window.location.search).get('token');

const aviso = (texto, tipo = 'error') => {
  const caixa = document.getElementById('redefinir-aviso');
  caixa.innerHTML = texto ? `<div class="status ${tipo}">${texto}</div>` : '';
};

const formulario = document.getElementById('form-redefinir');

// Sessao antiga nao faz sentido aqui: quem redefine a senha vai entrar de novo.
Auth.limpar();

if (!token) {
  aviso('Link inválido. Peça a recuperação de senha novamente.');
  formulario.hidden = true;
}

formulario.addEventListener('submit', async (evento) => {
  evento.preventDefault();
  const botao = document.getElementById('btn-redefinir');
  const novaSenha = document.getElementById('nova-senha').value;
  const repete = document.getElementById('repete-senha').value;

  if (novaSenha !== repete) {
    aviso('A nova senha e a repetição não conferem.');
    return;
  }

  // Mesma conferência do servidor, feita antes de enviar.
  const faltando = SenhaForte.problemas(novaSenha);
  if (faltando.length) {
    aviso(`A senha ainda não atende: ${faltando.join('; ')}.`);
    return;
  }

  botao.disabled = true;
  aviso('');

  try {
    const resposta = await fetch('/api/auth/redefinir-senha', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, novaSenha })
    });

    if (!resposta.ok) {
      const corpo = await resposta.json().catch(() => null);
      aviso((corpo && corpo.erro) || 'Não foi possível redefinir a senha.');
      // Link gasto ou expirado: nao adianta insistir no mesmo formulario.
      if (corpo && corpo.codigo === 'TOKEN_INVALIDO') formulario.hidden = true;
      return;
    }

    window.location.href = 'login.html?senhaRedefinida=1';
  } catch (err) {
    aviso(`Erro de conexão: ${err.message}`);
  } finally {
    botao.disabled = false;
  }
});
