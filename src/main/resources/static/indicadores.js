/*
 * Indicadores — o relatorio da catequese.
 *
 * Esta tela NAO e ferramenta de gestao. Nas outras abas o coordenador opera
 * (corrige chamada, classifica turma); aqui ele so le, para reuniao de
 * coordenacao e prestacao de contas. Dai as tres decisoes que moldam o arquivo:
 *
 *   1. NUMERO SEM COMPARACAO NAO E INDICADOR. Nenhum valor e desenhado sozinho:
 *      todo cartao mostra a base do ano anterior escrita por extenso. E o
 *      servidor que calcula a variacao -- se o JS recalculasse, um dia a tela e
 *      o papel diriam coisas diferentes.
 *   2. GRAFICO EM HTML/CSS, sem biblioteca e sem SVG. Nao ha build de JS no
 *      projeto, e depender de CDN quebraria a tela numa paroquia com internet
 *      ruim, que e exatamente onde ela roda. O porque de HTML em vez de SVG
 *      esta no comentario da secao de graficos.
 *   3. TODO GRAFICO VEM COM A TABELA. Quem nao le grafico le a tabela, e e ela
 *      que a impressao leva.
 *
 * Prefixo `ind` em tudo: script.js, usuarios.js e configuracoes.js dividem o
 * mesmo escopo global no index.html.
 */
(() => {
  'use strict';

  let opcoes = null;
  let dados = null;
  let ano = null;
  let idComunidade = '';
  let carregando = false;

  // ---------------------------------------------------------------- utilidades

  const esc = (valor) => {
    const div = document.createElement('div');
    div.textContent = valor === null || valor === undefined ? '' : String(valor);
    return div.innerHTML;
  };

  const inteiro = (v) => Math.round(Number(v) || 0).toLocaleString('pt-BR');

  const percentual = (v) =>
    `${(Number(v) || 0).toLocaleString('pt-BR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;

  const valorDe = (ind) => (ind.percentual ? percentual(ind.valor) : inteiro(ind.valor));

  /**
   * Sinal explicito, sem perder a casa decimal.
   *
   * A primeira versao mandava tudo por `inteiro()`, que arredonda: +8,7% saia
   * como "+9%" e +4,5 p.p. como "+5 p.p.". Num relatorio isso e defeito, nao
   * detalhe -- e o numero que alguem vai repetir em reuniao.
   */
  const comSinal = (v, casas = 0) => {
    const n = Number(v) || 0;
    const texto = n.toLocaleString('pt-BR', {
      minimumFractionDigits: casas,
      maximumFractionDigits: casas
    });
    return n > 0 ? `+${texto}` : texto;
  };

  const sinal = (v) => comSinal(v, 0);

  /**
   * A comparacao escrita por extenso, nunca so uma seta.
   *
   * Seta sozinha obriga a pessoa a confiar sem conferir; o numero de referencia
   * do lado e o que deixa alguem discordar do relatorio com argumento.
   */
  const comparacao = (ind) => {
    const base = dados && dados.anoBase;
    if (ind.situacao === 'SEM_BASE') {
      return '<span class="ind-var neutra">primeiro ano apurado</span>';
    }
    if (ind.situacao === 'NOVO') {
      return `<span class="ind-var melhor">novo — não havia nenhum em ${esc(base)}</span>`;
    }

    const delta = Number(ind.variacao) || 0;
    // Verde e vermelho so onde a direcao e inequivoca. Numero de eventos subir
    // nao e bom nem ruim, e pintar de verde sugeriria uma meta que ninguem
    // definiu.
    let classe = 'neutra';
    if (ind.direcaoBoa === 'MAIOR' && delta !== 0) classe = delta > 0 ? 'melhor' : 'pior';
    if (ind.direcaoBoa === 'MENOR' && delta !== 0) classe = delta > 0 ? 'pior' : 'melhor';

    const seta = delta > 0 ? '▲' : delta < 0 ? '▼' : '=';
    const valorBase = ind.percentual ? percentual(ind.base) : inteiro(ind.base);
    // Percentual varia em PONTOS PERCENTUAIS, com uma casa: de 88,1% para
    // 92,6% sao +4,5 p.p., nao "+5%". Confundir os dois e como um relatorio
    // perde credibilidade na primeira conferencia.
    const variacao = ind.percentual ? `${comSinal(delta, 1)} p.p.` : comSinal(delta, 0);

    // Base pequena nao vira percentual: de 1 para 2 daria "+100%", que tem cara
    // de tendencia e e ruido.
    const relativo =
      ind.situacao === 'BASE_PEQUENA' || ind.variacaoPercentual === null
        ? ''
        : ` (${comSinal(ind.variacaoPercentual, 1)}%)`;

    return `<span class="ind-var ${classe}"><span aria-hidden="true">${seta}</span> ${esc(variacao)}${esc(relativo)}</span>
      <span class="ind-base">contra ${esc(valorBase)} em ${esc(base)}</span>`;
  };

  const cartao = (ind, destaque = false) => `
    <div class="ind-cartao${destaque ? ' destaque' : ''}">
      <span class="ind-cartao-rotulo">${esc(ind.rotulo)}</span>
      <strong class="ind-cartao-valor">${valorDe(ind)}</strong>
      ${comparacao(ind)}
      ${ind.detalhe ? `<span class="ind-cartao-detalhe">${esc(ind.detalhe)}</span>` : ''}
    </div>`;

  const painel = (titulo, legenda, corpo) => `
    <section class="panel">
      <h2>${esc(titulo)}</h2>
      ${legenda ? `<p class="muted">${esc(legenda)}</p>` : ''}
      ${corpo}
    </section>`;

  const vazio = (texto) => `<p class="ind-vazio muted">${esc(texto)}</p>`;

  // ------------------------------------------------------------------ graficos
  //
  // Tudo em HTML/CSS, sem SVG e sem biblioteca.
  //
  // A primeira versao desenhava em SVG com viewBox escalado. Alem de nao haver
  // build de JS neste projeto (biblioteca esta fora de questao), o SVG escalado
  // trouxe dois problemas de verdade: o rotulo no fim da barra passava da
  // largura do pai -- a regressao pegou 76 elementos estourando a 400px -- e o
  // texto crescia junto com a largura da tela, porque `font-size` em unidade de
  // usuario escala com o viewBox. Barra em HTML nao tem nenhum dos dois: o
  // texto e texto, e `width: %` respeita o pai por definicao.

  /**
   * Barras horizontais. Horizontal porque nome de comunidade e longo -- em
   * coluna o rotulo teria de virar de lado ou ser cortado.
   *
   * Um tom so: a identidade esta no rotulo ao lado, nao na cor. Cor categorica
   * aqui gastaria quatro hues para dizer o que o texto ja diz.
   */
  const barras = (itens, opts = {}) => {
    if (!itens.length) return vazio(opts.vazio || 'Nenhum registro.');
    const maior = Math.max(...itens.map((i) => Math.abs(i.valor)), 1);

    const linhas = itens.map((item) => {
      const largura = (Math.abs(item.valor) / maior) * 100;
      const clicavel = opts.clicavel ? ' ind-clicavel' : '';
      const dado =
        opts.clicavel && item.id !== undefined ? ` data-comunidade="${esc(item.id ?? '')}"` : '';
      const papel = opts.clicavel ? ' role="button" tabindex="0"' : '';
      return `
        <li class="ind-barra-linha${clicavel}"${dado}${papel}
            title="${esc(item.rotulo)}: ${inteiro(item.valor)}">
          <span class="ind-barra-rotulo">${esc(item.rotulo)}</span>
          <span class="ind-barra-trilho">
            <span class="ind-barra-preenche" style="width: ${largura.toFixed(1)}%;
                  background: ${opts.cor || 'var(--graf-1)'};"></span>
          </span>
          <strong class="ind-barra-valor">${inteiro(item.valor)}</strong>
        </li>`;
    });

    return `<ul class="ind-barras" role="list"
                aria-label="${esc(opts.titulo || 'Gráfico de barras')}">${linhas.join('')}</ul>`;
  };

  /**
   * Evolucao ano a ano, em colunas.
   *
   * Coluna e nao linha porque sao poucos pontos e todos discretos: um ano nao
   * tem meio-termo, e a linha sugeriria uma continuidade que nao existe entre
   * 31 de dezembro e 1o de janeiro.
   *
   * Nunca dois eixos y no mesmo grafico: duas medidas de escala diferente viram
   * dois graficos, senao as curvas "se cruzam" sem que isso signifique nada.
   */
  const colunas = (pontos, rotulo) => {
    if (!pontos.length) return vazio('Ainda não há anos apurados.');
    // Eixo comecando em zero: cortar a base exagera qualquer variacao.
    const maior = Math.max(...pontos.map((p) => p.valor), 1);
    const itens = pontos
      .map(
        (p) => `
        <li class="ind-coluna" title="${esc(p.ano)}: ${inteiro(p.valor)} ${esc(rotulo)}">
          <span class="ind-coluna-valor">${inteiro(p.valor)}</span>
          <span class="ind-coluna-trilho">
            <span class="ind-coluna-preenche" style="height: ${((p.valor / maior) * 100).toFixed(1)}%;"></span>
          </span>
          <span class="ind-coluna-ano">${esc(p.ano)}</span>
        </li>`
      )
      .join('');
    return `<ul class="ind-colunas" role="list" aria-label="Evolução de ${esc(rotulo)}">${itens}</ul>`;
  };

  /**
   * Entraram para a direita, sairam para a esquerda, a partir do mesmo centro:
   * a pergunta aqui e de polaridade -- ganhou ou perdeu gente no ano.
   */
  const divergente = (entraram, sairam) => {
    const maior = Math.max(entraram, sairam, 1);
    const linha = (rotulo, valor, lado, cor) => `
      <li class="ind-diverge-linha" title="${esc(rotulo)}: ${inteiro(valor)}">
        <span class="ind-barra-rotulo">${esc(rotulo)}</span>
        <span class="ind-diverge-lado esquerda">
          ${lado === 'esquerda'
            ? `<span class="ind-barra-preenche" style="width: ${((valor / maior) * 100).toFixed(1)}%; background: ${cor};"></span>`
            : ''}
        </span>
        <span class="ind-diverge-lado direita">
          ${lado === 'direita'
            ? `<span class="ind-barra-preenche" style="width: ${((valor / maior) * 100).toFixed(1)}%; background: ${cor};"></span>`
            : ''}
        </span>
        <strong class="ind-barra-valor">${inteiro(valor)}</strong>
      </li>`;
    return `<ul class="ind-diverge" role="list" aria-label="Entradas e saídas do ano">
              ${linha('Entraram', entraram, 'direita', 'var(--graf-2)')}
              ${linha('Saíram', sairam, 'esquerda', 'var(--graf-1)')}
            </ul>`;
  };

  /** Tabela: acompanha todo grafico, e e o que sai no papel. */
  const tabela = (colunas, linhas) => `
    <div class="ind-tabela-rolagem">
      <table class="ind-tabela">
        <thead><tr>${colunas.map((c) => `<th>${esc(c)}</th>`).join('')}</tr></thead>
        <tbody>
          ${linhas
            .map((l) => `<tr>${l.map((c, i) => `<td${i === 0 ? '' : ' class="num"'}>${c}</td>`).join('')}</tr>`)
            .join('')}
        </tbody>
      </table>
    </div>`;

  // -------------------------------------------------------------------- blocos

  const blocoResumo = () => {
    const cartoes = [cartao(dados.catequisandos, true)];
    if (dados.pessoasDistintas) cartoes.push(cartao(dados.pessoasDistintas));
    cartoes.push(cartao(dados.catequistas, true));
    cartoes.push(cartao(dados.movimento.entraram));
    cartoes.push(cartao(dados.movimento.concluiram));
    cartoes.push(cartao(dados.movimento.abandonaram));
    cartoes.push(cartao(dados.movimento.retencao, true));
    return painel('O ano em números', null, `<div class="ind-cartoes">${cartoes.join('')}</div>`);
  };

  /**
   * Cada evolucao na sua moldura, com o topo da escala escrito.
   *
   * Sem a moldura os dois graficos ficavam lado a lado sem separacao e liam-se
   * como UM grafico de oito colunas -- e como cada um tem a sua escala, a
   * coluna de 34 catequistas aparecia da mesma altura que a de 312
   * catequisandos. E o erro do eixo duplo por outro caminho.
   */
  const faceta = (titulo, pontos, rotulo) => {
    const topo = Math.max(...pontos.map((p) => p.valor), 1);
    return `<div class="ind-faceta">
              <h3 class="ind-sub">${esc(titulo)}</h3>
              <p class="ind-escala">escala própria — topo em ${inteiro(topo)}</p>
              ${colunas(pontos, rotulo)}
            </div>`;
  };

  const blocoEvolucao = () => {
    const c = dados.evolucaoCatequisandos;
    const k = dados.evolucaoCatequistas;
    const anos = c.map((p) => p.ano);
    return painel(
      'Evolução',
      'Duas medidas de escala diferente, dois gráficos: um eixo só por gráfico.',
      `<div class="grid ind-dois">
         ${faceta('Catequisandos', c, 'catequisandos')}
         ${faceta('Catequistas', k, 'catequistas')}
       </div>
       ${tabela(
         ['Ano', 'Catequisandos', 'Catequistas'],
         anos.map((a) => [
           esc(a),
           inteiro((c.find((p) => p.ano === a) || {}).valor || 0),
           inteiro((k.find((p) => p.ano === a) || {}).valor || 0)
         ])
       )}`
    );
  };

  const blocoMovimento = () => {
    const m = dados.movimento;
    const sairam = m.concluiram.valor + m.abandonaram.valor;
    return painel(
      'Quem entrou e quem saiu',
      'Comparar dois totais não responde isso: 300 num ano e 300 no outro pode ser a mesma gente ou uma turma inteira trocada.',
      `${divergente(m.entraram.valor, sairam)}
       ${tabela(
         ['Movimento', 'Pessoas'],
         [
           ['Entraram', inteiro(m.entraram.valor)],
           ['Permaneceram', inteiro(m.permaneceram.valor)],
           ['Saíram — concluíram o percurso', inteiro(m.concluiram.valor)],
           ['Saíram — abandonaram', inteiro(m.abandonaram.valor)],
           ['Transferidos (não contam dos dois lados)', inteiro(m.transferidos)],
           ['<strong>Saldo</strong>', `<strong>${sinal(m.saldo)}</strong>`]
         ]
       )}
       <p class="muted">A retenção desconta quem concluiu do denominador: uma paróquia que
       forma muita gente apareceria com retenção ruim justamente por estar indo bem.</p>`
    );
  };

  const blocoComunidades = () => {
    const linhas = dados.porComunidade;
    if (!linhas.length) return painel('Por comunidade', null, vazio('Nenhuma matrícula no período.'));

    const itens = linhas.map((l) => ({
      id: l.idComunidade,
      rotulo: l.nome,
      valor: l.catequisandos.valor
    }));

    return painel(
      'Por comunidade',
      'Clique numa comunidade para ver só ela.',
      `${barras(itens, { clicavel: true, titulo: 'Catequisandos por comunidade' })}
       ${tabela(
         ['Comunidade', 'Catequisandos', 'Variação', 'Catequistas', 'Variação'],
         linhas.map((l) => [
           esc(l.nome),
           inteiro(l.catequisandos.valor),
           comparacao(l.catequisandos),
           inteiro(l.catequistas.valor),
           comparacao(l.catequistas)
         ])
       )}
       <p class="muted">Quem atua em duas comunidades conta em cada uma, mas é uma pessoa
       só no total da paróquia — por isso a soma da coluna pode passar do total.</p>`
    );
  };

  const blocoFormacoes = () => {
    const linhas = dados.formacoes;
    const temAlgo = linhas.some((l) => l.formacoes > 0);
    if (!temAlgo) {
      return painel(
        'Formação de catequistas',
        null,
        vazio('Nenhuma formação cadastrada neste ano.')
      );
    }
    return painel(
      'Formação de catequistas',
      'A pergunta aqui é participação, não quantidade: inscritos → participaram → atingiram o mínimo.',
      `${barras(
        linhas.map((l) => ({ rotulo: l.rotulo, valor: l.participaram.valor })),
        { titulo: 'Catequistas que participaram, por nível' }
      )}
       ${tabela(
         ['Nível', 'Formações', 'Encontros', 'Inscritos', 'Participaram', 'Atingiram o mínimo', 'Participação'],
         linhas.map((l) => [
           esc(l.rotulo),
           inteiro(l.formacoes),
           inteiro(l.encontrosRealizados),
           inteiro(l.inscritos.valor),
           `${inteiro(l.participaram.valor)} ${comparacao(l.participaram)}`,
           `${inteiro(l.atingiramMinimo.valor)} <span class="ind-base">mínimo ${esc(l.minimo)}%</span>`,
           percentual(l.taxaParticipacao.valor)
         ])
       )}`
    );
  };

  const blocoFrequencia = () => {
    const f = dados.frequencia;
    return painel(
      'Frequência',
      `Mínimo de ${f.minimo}%. Só encontro fechado conta; cancelado não entra na conta de ninguém.`,
      `<div class="ind-cartoes">
         ${cartao(f.media, true)}${cartao(f.abaixoDoMinimo)}${cartao(f.emRisco)}
       </div>
       ${tabela(
         ['Turmas', 'Quantas'],
         [
           ['Com frequência apurada', inteiro(f.turmasApuradas)],
           ['Sem nenhum encontro fechado ainda', inteiro(f.turmasSemApuracao)],
           ['Não apuram frequência (pré-catequese, perseverança)', inteiro(f.turmasNaoSeAplica)]
         ]
       )}`
    );
  };

  const blocoEventos = () => {
    const e = dados.eventos;
    const tipos = e.porTipo.filter((t) => t.valor > 0 || (t.base || 0) > 0);
    return painel(
      'Eventos do período',
      'Cancelado aparece separado: sumir com ele faria o total do ano encolher sem explicação.',
      `<div class="ind-cartoes">
         ${cartao(e.total, true)}${cartao(e.realizados)}${cartao(e.cancelados)}
       </div>
       ${
         tipos.length
           ? barras(tipos.map((t) => ({ rotulo: t.rotulo, valor: t.valor })), {
               titulo: 'Eventos por tipo'
             }) +
             tabela(
               ['Tipo', dados.ano, dados.anoBase || '—'],
               tipos.map((t) => [esc(t.rotulo), inteiro(t.valor), t.base === null ? '—' : inteiro(t.base)])
             )
           : vazio('Nenhum evento no período.')
       }`
    );
  };

  const blocoSituacoes = () => {
    const linhas = dados.situacaoMatriculas.filter((s) => s.valor > 0 || (s.base || 0) > 0);
    if (!linhas.length) return '';
    return painel(
      'Situação das matrículas',
      null,
      tabela(
        ['Situação', dados.ano, dados.anoBase || '—'],
        linhas.map((s) => [esc(s.rotulo), inteiro(s.valor), s.base === null ? '—' : inteiro(s.base)])
      )
    );
  };

  /**
   * Fundos: so o lugar reservado. Nao existe tela de lancamento ainda, e o
   * cartao fica visivel de proposito -- assim o espaco dele ja esta no layout
   * e ninguem reabre a discussao achando que foi esquecimento.
   */
  const blocoFundos = () =>
    painel(
      'Fundos da catequese',
      null,
      `<div class="ind-reservado">
         <p><strong>Disponível quando existir a tela de lançamentos.</strong></p>
         <p class="muted">Aqui vão entradas, saídas e saldo do ano contra o ano anterior,
         saldo por comunidade e gasto por categoria — no mesmo formato de comparação do
         resto da tela. O desenho da tabela está em
         <code>docs/especificacoes/tela-indicadores.md</code>.</p>
       </div>`
    );

  // ------------------------------------------------------------------- desenho

  const alvo = () => document.getElementById('ind-conteudo');

  const desenharFichas = () => {
    const caixa = document.getElementById('ind-fichas');
    if (!caixa) return;
    if (!idComunidade) {
      caixa.hidden = true;
      caixa.innerHTML = '';
      return;
    }
    const nome = (dados && dados.nomeComunidade) || 'comunidade';
    caixa.hidden = false;
    // O filtro aplicado aparece escrito. Filtro esquecido e o erro mais caro
    // que uma tela de relatorio comete: a pessoa le um numero de uma comunidade
    // achando que e o da paroquia.
    caixa.innerHTML = `
      <span class="ind-ficha">Comunidade: ${esc(nome)}
        <button type="button" class="ind-ficha-x" id="ind-limpar" aria-label="Remover filtro">×</button>
      </span>
      <button type="button" class="ind-limpar-tudo" id="ind-limpar-tudo">Ver a paróquia inteira</button>`;
    caixa.querySelector('#ind-limpar').addEventListener('click', limparComunidade);
    caixa.querySelector('#ind-limpar-tudo').addEventListener('click', limparComunidade);
  };

  const desenharAvisos = () => {
    const caixa = document.getElementById('ind-avisos');
    if (!caixa) return;
    const avisos = (dados && dados.avisos) || [];
    caixa.innerHTML = avisos
      .map((a) => `<div class="status warning">${esc(a)}</div>`)
      .join('');
  };

  const desenhar = () => {
    document.getElementById('ind-cabecalho').textContent = dados.cabecalho;
    desenharFichas();
    desenharAvisos();

    alvo().innerHTML = [
      blocoResumo(),
      blocoEvolucao(),
      blocoMovimento(),
      blocoComunidades(),
      blocoSituacoes(),
      blocoFormacoes(),
      blocoFrequencia(),
      blocoEventos(),
      blocoFundos()
    ].join('');

    // Drill-down: clicar numa comunidade filtra por ela. Nao e descobrivel
    // sozinho, por isso o painel tras a legenda "Clique numa comunidade".
    alvo().querySelectorAll('[data-comunidade]').forEach((g) => {
      g.addEventListener('click', () => {
        const id = g.dataset.comunidade;
        if (!id) return; // "Sem comunidade definida" nao e um filtro valido
        idComunidade = id;
        const select = document.getElementById('ind-comunidade');
        if (select) select.value = id;
        carregar();
      });
    });
  };

  const mostrarEstado = (html) => {
    alvo().innerHTML = `<section class="panel">${html}</section>`;
  };

  const limparComunidade = () => {
    idComunidade = '';
    const select = document.getElementById('ind-comunidade');
    if (select) select.value = '';
    carregar();
  };

  // ------------------------------------------------------------------- dados

  const preencherOpcoes = () => {
    const selAno = document.getElementById('ind-ano');
    const selCom = document.getElementById('ind-comunidade');
    if (!selAno || !selCom) return;

    selAno.innerHTML = opcoes.anos.map((a) => `<option value="${a}">${a}</option>`).join('');
    if (ano) selAno.value = String(ano);
    else ano = Number(selAno.value);

    selCom.innerHTML =
      '<option value="">Paróquia inteira</option>' +
      opcoes.comunidades.map((c) => `<option value="${c.id}">${esc(c.nome)}</option>`).join('');
    selCom.value = idComunidade;
  };

  const carregar = async () => {
    if (carregando) return;
    carregando = true;
    document.getElementById('ind-cabecalho').textContent = 'Apurando…';
    mostrarEstado('<p class="muted">Apurando o ano e o anterior…</p>');

    try {
      if (!opcoes) {
        const r = await fetch('/api/indicadores/opcoes');
        if (!r.ok) throw new Error(await mensagemDeErro(r));
        opcoes = await r.json();
        preencherOpcoes();
      }

      const params = new URLSearchParams();
      if (ano) params.set('ano', ano);
      if (idComunidade) params.set('idComunidade', idComunidade);

      const resposta = await fetch(`/api/indicadores?${params}`);
      if (!resposta.ok) throw new Error(await mensagemDeErro(resposta));
      dados = await resposta.json();
      desenhar();
    } catch (err) {
      document.getElementById('ind-cabecalho').textContent = 'Não foi possível apurar.';
      mostrarEstado(
        `<div class="status error">${esc(err.message)}</div>
         <div class="row"><button type="button" id="ind-tentar">Tentar de novo</button></div>`
      );
      const botao = document.getElementById('ind-tentar');
      if (botao) botao.addEventListener('click', carregar);
    } finally {
      carregando = false;
    }
  };

  const mensagemDeErro = async (resposta) => {
    if (resposta.status === 403) {
      return 'Este relatório é exclusivo do coordenador paroquial.';
    }
    try {
      const corpo = await resposta.json();
      if (corpo && corpo.message) return corpo.message;
    } catch (e) {
      /* resposta sem JSON: cai no texto generico abaixo */
    }
    return `O servidor respondeu ${resposta.status}.`;
  };

  // ------------------------------------------------------------------ ligacao

  const ligar = () => {
    const selAno = document.getElementById('ind-ano');
    const selCom = document.getElementById('ind-comunidade');
    const imprimir = document.getElementById('ind-imprimir');
    if (selAno) {
      selAno.addEventListener('change', () => {
        ano = Number(selAno.value);
        carregar();
      });
    }
    if (selCom) {
      selCom.addEventListener('change', () => {
        idComunidade = selCom.value;
        carregar();
      });
    }
    // "Salvar como PDF" e a impressao do proprio navegador -- sem biblioteca.
    if (imprimir) imprimir.addEventListener('click', () => window.print());
  };

  let ligado = false;

  // script.js chama esta funcao ao abrir a aba.
  window.carregarIndicadores = () => {
    if (!ligado) {
      ligar();
      ligado = true;
    }
    carregar();
  };
})();
