/*
 * Camada de sessao compartilhada por todas as telas.
 *
 * Precisa ser carregada ANTES de script.js / ficha.js, porque intercepta o
 * fetch: assim nenhuma chamada a /api/ fica sem o cabecalho Authorization, e
 * nao e preciso lembrar de tratar 401 em cada lugar que chama a API.
 */
(() => {
  const CHAVE_TOKEN = 'catequese.token';
  const CHAVE_USUARIO = 'catequese.usuario';
  // sessionStorage, e nao localStorage: vale so para esta aba e some quando ela
  // fecha. Guarda por segundos a senha provisoria digitada no login, para a
  // tela de troca obrigatoria ja vir com ela preenchida -- e uma senha longa
  // gerada pelo sistema, que ninguem decora nem quer colar duas vezes.
  const CHAVE_SENHA_PROVISORIA = 'catequese.senhaProvisoria';
  // Chave de inscrição que chega pelo link divulgado pela paróquia
  // (index.html?chave=CAT-XXXX-XXXX). Fica na aba: some quando ela fecha.
  const CHAVE_INSCRICAO = 'catequese.chaveInscricao';

  // Captura logo na carga e guarda, para sobreviver à navegação entre abas da
  // aplicação sem precisar carregar o parâmetro na URL o tempo todo.
  try {
    const daUrl = new URLSearchParams(window.location.search).get('chave');
    if (daUrl && daUrl.trim()) {
      sessionStorage.setItem(CHAVE_INSCRICAO, daUrl.trim().toUpperCase());
    }
  } catch (err) {
    // Sem sessionStorage, o cadastro simplesmente pedirá o código.
  }

  const lerUsuario = () => {
    try {
      const bruto = localStorage.getItem(CHAVE_USUARIO);
      return bruto ? JSON.parse(bruto) : null;
    } catch (err) {
      return null;
    }
  };

  const Auth = {
    token: () => localStorage.getItem(CHAVE_TOKEN),

    usuario: lerUsuario,

    estaLogado: () => Boolean(localStorage.getItem(CHAVE_TOKEN)),

    /** Guarda o que voltou de /api/auth/login ou /trocar-senha. */
    salvarSessao(dto) {
      if (dto && dto.token) localStorage.setItem(CHAVE_TOKEN, dto.token);
      if (dto) {
        const { token, ...usuario } = dto;
        localStorage.setItem(CHAVE_USUARIO, JSON.stringify(usuario));
      }
    },

    limpar() {
      localStorage.removeItem(CHAVE_TOKEN);
      localStorage.removeItem(CHAVE_USUARIO);
      sessionStorage.removeItem(CHAVE_SENHA_PROVISORIA);
    },

    /** Chamado pelo login quando a conta ainda esta com senha provisoria. */
    guardarSenhaProvisoria(senha) {
      try {
        sessionStorage.setItem(CHAVE_SENHA_PROVISORIA, senha);
      } catch (err) {
        // Sem sessionStorage o usuario so tera de digitar a senha de novo.
      }
    },

    /** Le e ja apaga: serve uma vez so, na tela de troca. */
    consumirSenhaProvisoria() {
      try {
        const senha = sessionStorage.getItem(CHAVE_SENHA_PROVISORIA);
        sessionStorage.removeItem(CHAVE_SENHA_PROVISORIA);
        return senha || '';
      } catch (err) {
        return '';
      }
    },

    /** Atalhos de papel. O backend e quem realmente bloqueia; isto so ajusta a tela. */
    podeEditar: () => Boolean(lerUsuario()?.podeEditar),
    ehAdmin: () => Boolean(lerUsuario()?.admin),

    chaveInscricao() {
      try {
        return sessionStorage.getItem(CHAVE_INSCRICAO) || '';
      } catch (err) {
        return '';
      }
    },

    definirChaveInscricao(codigo) {
      try {
        const limpo = (codigo || '').trim().toUpperCase();
        if (limpo) sessionStorage.setItem(CHAVE_INSCRICAO, limpo);
        else sessionStorage.removeItem(CHAVE_INSCRICAO);
      } catch (err) {
        // Ignorado: sem sessionStorage o envio será recusado e a tela avisa.
      }
    },

    irParaLogin(destino) {
      const params = new URLSearchParams();
      if (destino) params.set('destino', destino);
      window.location.href = `login.html${params.toString() ? '?' + params : ''}`;
    },

    sair() {
      Auth.limpar();
      window.location.href = 'login.html';
    },

    /** Usar no topo de telas que nao fazem sentido sem login. */
    exigirLogin(destino) {
      if (Auth.estaLogado()) return true;
      Auth.irParaLogin(destino);
      return false;
    },

    /** Preenche a barrinha de usuario, se a pagina tiver uma. */
    renderBarraUsuario() {
      const alvo = document.getElementById('barra-usuario');
      if (!alvo) return;

      const usuario = lerUsuario();
      if (!usuario) {
        alvo.innerHTML =
          '<a class="botao-falso" href="login.html">Entrar</a>';
        return;
      }

      // Sem atalho para trocar senha aqui: e uma acao rara e ficava competindo
      // com o Sair. Quando existir uma tela de configuracoes do usuario, ela
      // entra la -- trocar-senha.html continua acessivel pela URL.
      alvo.innerHTML = `
        <span class="usuario-info">
          <strong>${escapar(usuario.nome || usuario.username)}</strong>
          <span class="usuario-tipo">${rotuloTipo(usuario.tipo)}</span>
        </span>
        <button type="button" class="secondary" id="btn-sair">Sair</button>
      `;
      const botao = document.getElementById('btn-sair');
      if (botao) botao.addEventListener('click', Auth.sair);
    }
  };

  const ROTULOS_TIPO = {
    CATEQUISTA: 'Catequista',
    COORDENADOR: 'Coordenador',
    COORDENADOR_PAROQUIAL: 'Coordenador paroquial'
  };
  const rotuloTipo = (tipo) => ROTULOS_TIPO[tipo] || tipo || '';

  const escapar = (valor) => {
    const div = document.createElement('div');
    div.textContent = valor === null || valor === undefined ? '' : String(valor);
    return div.innerHTML;
  };

  // ---- Interceptacao do fetch ----------------------------------------------
  // Toda chamada para /api/ leva o token, e as respostas de sessao invalida sao
  // tratadas num lugar so.
  const fetchOriginal = window.fetch.bind(window);

  const ehChamadaDaApi = (entrada) => {
    const url = typeof entrada === 'string' ? entrada : (entrada && entrada.url) || '';
    return url.includes('/api/');
  };

  const paginaAtual = () => window.location.pathname.split('/').pop() || 'index.html';

  window.fetch = async (entrada, init = {}) => {
    if (!ehChamadaDaApi(entrada)) return fetchOriginal(entrada, init);

    const token = Auth.token();
    const chave = Auth.chaveInscricao();
    const opcoes = { ...init };

    if (token || chave) {
      const headers = new Headers(opcoes.headers || {});
      if (token) headers.set('Authorization', `Bearer ${token}`);
      // O envio da inscrição e o upload dos anexos exigem a chave. Mandar
      // sempre é inofensivo: quem não precisa dela simplesmente ignora.
      if (chave) headers.set('X-Chave-Inscricao', chave);
      opcoes.headers = headers;
    }

    const resposta = await fetchOriginal(entrada, opcoes);

    // Sessao expirada ou derrubada (troca de senha, conta desativada).
    // So redireciona quem tinha token: visitante da tela publica de cadastro
    // nao pode ser jogado para o login por causa de um erro de API.
    if (resposta.status === 401 && token) {
      Auth.limpar();
      if (paginaAtual() !== 'login.html') {
        window.location.href = 'login.html?expirou=1';
      }
      return resposta;
    }

    if (resposta.status === 403) {
      const corpo = await resposta.clone().json().catch(() => null);
      if (corpo && corpo.codigo === 'SENHA_PROVISORIA' &&
          paginaAtual() !== 'trocar-senha.html') {
        window.location.href = 'trocar-senha.html?obrigatoria=1';
      }
    }

    return resposta;
  };

  window.Auth = Auth;
  document.addEventListener('DOMContentLoaded', Auth.renderBarraUsuario);
})();
