/*
 * Conferencia de senha forte na tela, olho para revelar e comparacao da
 * confirmacao.
 *
 * ATENCAO: as regras aqui espelham PoliticaSenha.kt no backend. Mexeu la, mexa
 * aqui. Esta tela e cortesia: quem de fato recusa a senha e o servidor.
 *
 * Como usar no HTML, sem escrever JavaScript nenhum:
 *   <input type="password" id="senha-nova" data-olho data-criterios>
 *   <div class="criterios-senha" data-criterios-de="senha-nova"></div>
 *   <input type="password" id="senha-repete" data-olho
 *          data-confirma="senha-nova">
 *   <div class="conferencia-senha" data-conferencia-de="senha-repete"></div>
 */
(() => {
  const TAMANHO_MINIMO = 8;

  const CRITERIOS = [
    {
      id: 'tamanho',
      texto: `Pelo menos ${TAMANHO_MINIMO} caracteres`,
      ok: (s) => s.length >= TAMANHO_MINIMO
    },
    {
      id: 'maiuscula',
      texto: 'Pelo menos uma letra maiúscula',
      ok: (s) => /[A-ZÀ-Þ]/.test(s)
    },
    {
      id: 'numero',
      texto: 'Pelo menos um número',
      ok: (s) => /[0-9]/.test(s)
    },
    {
      id: 'especial',
      texto: 'Pelo menos um caractere especial (! @ # $ % * ?)',
      // Espelha o backend: qualquer coisa que nao seja letra, numero ou espaco.
      ok: (s) => /[^\p{L}\p{N}\s]/u.test(s)
    }
  ];

  const avaliar = (senha) => CRITERIOS.map((c) => ({
    id: c.id,
    texto: c.texto,
    ok: senha.length > 0 && c.ok(senha)
  }));

  const OLHO_ABERTO = `
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
         stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
      <circle cx="12" cy="12" r="3" />
    </svg>`;

  const OLHO_FECHADO = `
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor"
         stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
      <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
      <path d="M9.88 9.88a3 3 0 1 0 4.24 4.24" />
      <path d="M1 1l22 22" />
    </svg>`;

  /** Envolve o campo e acrescenta o botao de revelar. */
  const aplicarOlho = (input) => {
    if (input.dataset.olhoPronto === '1') return;
    input.dataset.olhoPronto = '1';

    const caixa = document.createElement('div');
    caixa.className = 'campo-senha';
    input.parentNode.insertBefore(caixa, input);
    caixa.appendChild(input);

    const botao = document.createElement('button');
    botao.type = 'button';
    botao.className = 'olho-senha';
    botao.innerHTML = OLHO_ABERTO;
    botao.setAttribute('aria-label', 'Mostrar senha');
    botao.title = 'Mostrar senha';

    botao.addEventListener('click', () => {
      const revelando = input.type === 'password';
      input.type = revelando ? 'text' : 'password';
      botao.innerHTML = revelando ? OLHO_FECHADO : OLHO_ABERTO;
      const rotulo = revelando ? 'Ocultar senha' : 'Mostrar senha';
      botao.setAttribute('aria-label', rotulo);
      botao.title = rotulo;
      input.focus();
    });

    caixa.appendChild(botao);
  };

  const renderCriterios = (caixa, senha) => {
    caixa.innerHTML = avaliar(senha).map((c) => `
      <span class="criterio ${c.ok ? 'ok' : ''}">
        <span class="criterio-marca" aria-hidden="true">${c.ok ? '✓' : '○'}</span>
        ${c.texto}
      </span>
    `).join('');
  };

  const renderConferencia = (caixa, senha, confirmacao) => {
    if (!confirmacao) {
      caixa.innerHTML = '';
      return;
    }
    const igual = senha === confirmacao;
    caixa.innerHTML = `
      <span class="criterio ${igual ? 'ok' : 'erro'}">
        <span class="criterio-marca" aria-hidden="true">${igual ? '✓' : '✕'}</span>
        ${igual ? 'As senhas conferem' : 'As senhas não conferem'}
      </span>`;
  };

  const ligar = () => {
    document.querySelectorAll('input[type="password"][data-olho]').forEach(aplicarOlho);

    // Lista de criterios, atualizada a cada tecla.
    document.querySelectorAll('[data-criterios-de]').forEach((caixa) => {
      const input = document.getElementById(caixa.dataset.criteriosDe);
      if (!input) return;
      renderCriterios(caixa, input.value);
      input.addEventListener('input', () => renderCriterios(caixa, input.value));
    });

    // Comparacao entre senha e confirmacao, nos dois sentidos.
    document.querySelectorAll('[data-conferencia-de]').forEach((caixa) => {
      const confirmacao = document.getElementById(caixa.dataset.conferenciaDe);
      if (!confirmacao) return;
      const original = document.getElementById(confirmacao.dataset.confirma);
      if (!original) return;

      const atualizar = () => renderConferencia(caixa, original.value, confirmacao.value);
      atualizar();
      confirmacao.addEventListener('input', atualizar);
      original.addEventListener('input', atualizar);
    });
  };

  window.SenhaForte = {
    /** Usado pelas telas antes de enviar, para nao gastar uma ida ao servidor. */
    problemas: (senha) => avaliar(senha).filter((c) => !c.ok).map((c) => c.texto),
    tudoOk: (senha) => avaliar(senha).every((c) => c.ok)
  };

  document.addEventListener('DOMContentLoaded', ligar);
})();
