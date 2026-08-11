/* Troca da propria senha. Serve para a troca obrigatoria do primeiro acesso
   e tambem para uma troca voluntaria depois. */

if (Auth.exigirLogin()) {
  const params = new URLSearchParams(window.location.search);
  const obrigatoria = params.get('obrigatoria') === '1' ||
    Boolean(Auth.usuario() && Auth.usuario().senhaProvisoria);

  const aviso = (texto, tipo = 'error') => {
    const caixa = document.getElementById('troca-aviso');
    caixa.innerHTML = texto ? `<div class="status ${tipo}">${texto}</div>` : '';
  };

  if (obrigatoria) {
    document.getElementById('titulo-troca').textContent = 'Defina sua nova senha';
    aviso(
      'Sua senha foi criada pelo sistema e precisa ser trocada antes de continuar.',
      'warning'
    );
    // Sem saida pela lateral: enquanto a senha for provisoria o backend barra
    // qualquer outra tela, entao um link "voltar" so levaria a um erro.
    document.getElementById('rodape-troca').hidden = true;
  }

  document.getElementById('form-troca').addEventListener('submit', async (evento) => {
    evento.preventDefault();
    const botao = document.getElementById('btn-trocar');
    const senhaAtual = document.getElementById('senha-atual').value;
    const novaSenha = document.getElementById('senha-nova').value;
    const repete = document.getElementById('senha-repete').value;

    if (novaSenha !== repete) {
      aviso('A nova senha e a repetição não conferem.');
      return;
    }

    // Confere aqui o que o servidor conferiria, para não gastar uma ida à rede
    // e para a mensagem apontar exatamente o critério que falta.
    const faltando = SenhaForte.problemas(novaSenha);
    if (faltando.length) {
      aviso(`A senha ainda não atende: ${faltando.join('; ')}.`);
      return;
    }

    botao.disabled = true;
    aviso('');

    try {
      const resposta = await fetch('/api/auth/trocar-senha', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ senhaAtual, novaSenha })
      });

      if (!resposta.ok) {
        const corpo = await resposta.json().catch(() => null);
        aviso((corpo && corpo.erro) || 'Não foi possível trocar a senha.');
        return;
      }

      // O backend devolve um token novo: o anterior deixa de valer na troca.
      const dados = await resposta.json();
      Auth.salvarSessao(dados);

      aviso('Senha alterada. Redirecionando...', 'ok');
      setTimeout(() => { window.location.href = 'index.html'; }, 1200);
    } catch (err) {
      aviso(`Erro de conexão: ${err.message}`);
    } finally {
      botao.disabled = false;
    }
  });
}
