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

  // O Chrome preenche campos de senha sozinho com credenciais salvas para o
  // mesmo endereco, e localhost:8080 costuma ser reaproveitado por varios
  // projetos. Aqui isso e sempre errado: a senha atual e a provisoria, que o
  // navegador nao tem como conhecer. Sem limpar, o usuario envia um valor que
  // nem sabe que esta no campo e leva "Senha atual incorreta".
  // O preenchimento acontece logo depois do load, por isso o pequeno atraso.
  // Lida com o preenchimento automático do navegador.
  //
  // O Chrome preenche campos de senha com credenciais salvas para o mesmo
  // endereço, e localhost:8080 costuma ser reaproveitado por vários projetos.
  // Aqui isso é sempre errado: a senha atual é a provisória, que o navegador
  // não tem como conhecer.
  //
  // Limpar uma vez só não bastou: o Chrome repõe o valor depois. Por isso a
  // correção é reaplicada algumas vezes, e para assim que o usuário digitar —
  // a partir daí a tela não encosta mais no que ele escreveu.
  const CAMPOS = ['senha-atual', 'senha-nova', 'senha-repete'];
  const senhaProvisoria = Auth.consumirSenhaProvisoria();
  let usuarioDigitou = false;

  CAMPOS.forEach((id) => {
    const campo = document.getElementById(id);
    if (!campo) return;
    // keydown e paste são ações de gente; preenchimento automático não dispara.
    ['keydown', 'paste'].forEach((evento) =>
      campo.addEventListener(evento, () => { usuarioDigitou = true; }));
  });

  const ajustarCampos = () => {
    if (usuarioDigitou) return;

    CAMPOS.forEach((id) => {
      const campo = document.getElementById(id);
      if (campo) campo.value = '';
    });
    document.querySelectorAll('[data-criterios-de], [data-conferencia-de]')
      .forEach((caixa) => { caixa.innerHTML = ''; });

    // Só depois de limpar é que devolvemos a senha provisória digitada no
    // login — na ordem inversa, a limpeza apagaria justamente o que queremos.
    if (senhaProvisoria) {
      const campo = document.getElementById('senha-atual');
      if (campo) campo.value = senhaProvisoria;
    }
  };

  window.addEventListener('load', () => {
    [100, 350, 800, 1500].forEach((atraso) => setTimeout(ajustarCampos, atraso));
    // O usuário já tem a senha atual preenchida: o que falta é a nova.
    if (senhaProvisoria) {
      setTimeout(() => {
        const proxima = document.getElementById('senha-nova');
        if (proxima && !usuarioDigitou) proxima.focus();
      }, 400);
    }
  });

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
