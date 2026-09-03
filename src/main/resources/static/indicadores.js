/*
 * Indicadores — o relatorio da catequese.
 *
 * Esta tela NAO e ferramenta de gestao. Nas outras abas o coordenador opera
 * (corrige chamada, classifica turma); aqui ele so le, para reuniao de
 * coordenacao e prestacao de contas. Dai as decisoes que moldam o arquivo:
 *
 *   1. NUMERO SEM COMPARACAO NAO E INDICADOR. Nenhum valor e desenhado
 *      sozinho: todo cartao mostra a base do ano anterior escrita por extenso.
 *      Quem calcula a variacao e o servidor -- se o JS recalculasse, um dia a
 *      tela e o papel diriam coisas diferentes.
 *   2. UMA PERGUNTA POR TELA, com os filtros daquela pergunta. A primeira
 *      versao punha nove blocos e dois filtros numa pagina so: para saber quem
 *      faltou na formacao, a pessoa tinha de ler matricula, frequencia e evento
 *      no caminho.
 *   3. GRAFICO EM HTML/CSS, sem biblioteca e sem SVG (o porque esta na secao
 *      de graficos).
 *   4. TODO GRAFICO VEM COM A TABELA. Quem nao le grafico le a tabela, e e ela
 *      que a impressao leva.
 *
 * Prefixo `ind` em tudo: script.js, usuarios.js e configuracoes.js dividem o
 * mesmo escopo global no index.html.
 */
(() => {
  'use strict';

  let opcoes = null;
  let dados = null;
  let vista = 'resumo';
  let carregando = false;
  let ligado = false;

  // Um conjunto de filtros POR TELA. Trocar de tela nao pode carregar o filtro
  // da anterior: "turma" da Frequencia nao quer dizer nada em Formacao, e
  // filtro herdado invisivel e a pior forma de ler um numero errado.
  const filtros = {
    resumo: { ano: '', idComunidade: '' },
    matriculas: { ano: '', idComunidade: '', idTurma: '', situacao: '' },
    frequencia: { ano: '', idComunidade: '', idTurma: '' },
    formacao: { ano: '', nivel: '', idComunidade: '', idCatequista: '' },
    eventos: { ano: '', tipo: '', nivel: '', idComunidade: '' }
  };

  const VISTAS = {
    resumo: { rotulo: 'Resumo geral', rota: '', campos: ['ano', 'idComunidade'] },
    matriculas: {
      rotulo: 'Matrículas', rota: '/matriculas',
      campos: ['ano', 'idComunidade', 'idTurma', 'situacao']
    },
    frequencia: {
      rotulo: 'Frequência', rota: '/frequencia',
      campos: ['ano', 'idComunidade', 'idTurma']
    },
    formacao: {
      rotulo: 'Formação', rota: '/formacao',
      campos: ['ano', 'nivel', 'idComunidade', 'idCatequista']
    },
    eventos: {
      rotulo: 'Eventos', rota: '/eventos',
      campos: ['ano', 'tipo', 'nivel', 'idComunidade']
    }
  };

  // ---------------------------------------------------------------- utilidades

  const esc = (valor) => {
    const div = document.createElement('div');
    div.textContent = valor === null || valor === undefined ? '' : String(valor);
    return div.innerHTML;
  };

  const inteiro = (v) => Math.round(Number(v) || 0).toLocaleString('pt-BR');

  const percentual = (v, casas = 1) =>
    v === null || v === undefined
      ? '—'
      : `${Number(v).toLocaleString('pt-BR', {
          minimumFractionDigits: casas,
          maximumFractionDigits: casas
        })}%`;

  const valorDe = (ind) => (ind.percentual ? percentual(ind.valor) : inteiro(ind.valor));

  /**
   * Sinal explicito, sem perder a casa decimal.
   *
   * Arredondar para inteiro ja transformou +8,7% em "+9%" e +4,5 p.p. em
   * "+5 p.p.". Num relatorio isso e defeito: e o numero que alguem repete em
   * reuniao e confere depois.
   */
  const comSinal = (v, casas = 0) => {
    const n = Number(v) || 0;
    const texto = n.toLocaleString('pt-BR', {
      minimumFractionDigits: casas,
      maximumFractionDigits: casas
    });
    return n > 0 ? `+${texto}` : texto;
  };

  const dataBr = (iso) => {
    if (!iso) return '—';
    const partes = String(iso).split('-');
    return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : String(iso);
  };

  /**
   * A comparacao escrita por extenso, nunca so uma seta.
   *
   * Seta sozinha obriga a pessoa a confiar sem conferir; o numero de referencia
   * ao lado e o que deixa alguem discordar do relatorio com argumento.
   */
  const comparacao = (ind) => {
    if (!ind) return '';
    const base = dados && dados.anoBase;
    if (ind.situacao === 'SEM_BASE') {
      return '<span class="ind-var neutra">sem base de comparação</span>';
    }
    if (ind.situacao === 'NOVO') {
      return `<span class="ind-var melhor">novo — não havia nenhum${base ? ` em ${esc(base)}` : ''}</span>`;
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
    // Percentual varia em PONTOS PERCENTUAIS: de 88,1% para 92,6% sao
    // +4,5 p.p., nao "+5%". Confundir os dois derruba a credibilidade na
    // primeira conferencia.
    const variacao = ind.percentual ? `${comSinal(delta, 1)} p.p.` : comSinal(delta, 0);
    const relativo =
      ind.situacao === 'BASE_PEQUENA' || ind.variacaoPercentual === null
        ? ''
        : ` (${comSinal(ind.variacaoPercentual, 1)}%)`;

    const referencia = base ? ` em ${esc(base)}` : '';
    return `<span class="ind-var ${classe}"><span aria-hidden="true">${seta}</span> ${esc(variacao)}${esc(relativo)}</span>
      <span class="ind-base">contra ${esc(valorBase)}${referencia}</span>`;
  };

  const cartao = (ind, destaque = false) => {
    if (!ind) return '';
    return `
      <div class="ind-cartao${destaque ? ' destaque' : ''}">
        <span class="ind-cartao-rotulo">${esc(ind.rotulo)}</span>
        <strong class="ind-cartao-valor">${valorDe(ind)}</strong>
        ${comparacao(ind)}
        ${ind.detalhe ? `<span class="ind-cartao-detalhe">${esc(ind.detalhe)}</span>` : ''}
      </div>`;
  };

  const painel = (titulo, pergunta, corpo) => `
    <section class="panel">
      <h2>${esc(titulo)}</h2>
      ${pergunta ? `<p class="ind-pergunta">${esc(pergunta)}</p>` : ''}
      ${corpo}
    </section>`;

  const vazio = (texto) => `<p class="ind-vazio muted">${esc(texto)}</p>`;

  // ------------------------------------------------------------------ graficos
  //
  // Tudo em HTML/CSS, sem SVG e sem biblioteca.
  //
  // A primeira versao desenhava em SVG com viewBox escalado. Alem de nao haver
  // build de JS neste projeto, o SVG escalado trouxe dois defeitos reais: o
  // rotulo no fim da barra passava da largura do pai (a regressao pegou 76
  // elementos estourando a 400px) e o texto crescia junto com a largura da
  // tela, porque `font-size` em unidade de usuario escala com o viewBox. Barra
  // em HTML nao tem nenhum dos dois, e imprime melhor.

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
      const clicavel = opts.clicavel && item.id ? ' ind-clicavel' : '';
      const dado = opts.clicavel && item.id ? ` data-filtro="${esc(item.id)}"` : '';
      const papel = clicavel ? ' role="button" tabindex="0"' : '';
      const texto = opts.formatar ? opts.formatar(item.valor) : inteiro(item.valor);
      return `
        <li class="ind-barra-linha${clicavel}"${dado}${papel}
            title="${esc(item.rotulo)}: ${esc(texto)}">
          <span class="ind-barra-rotulo">${esc(item.rotulo)}</span>
          <span class="ind-barra-trilho">
            <span class="ind-barra-preenche" style="width: ${largura.toFixed(1)}%;
                  background: ${opts.cor || 'var(--graf-1)'};"></span>
          </span>
          <strong class="ind-barra-valor">${esc(texto)}</strong>
        </li>`;
    });

    return `<ul class="ind-barras" role="list"
                aria-label="${esc(opts.titulo || 'Gráfico de barras')}">${linhas.join('')}</ul>`;
  };

  /**
   * Evolucao ano a ano, em colunas.
   *
   * Coluna e nao linha porque os pontos sao discretos: nao existe meio-termo
   * entre 31 de dezembro e 1o de janeiro.
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
   * Cada gráfico na sua moldura, com o topo da escala escrito.
   *
   * Sem a moldura os dois ficavam lado a lado sem separacao e liam-se como UM
   * grafico de oito colunas -- e, como cada um tem a sua escala, a coluna de 34
   * catequistas aparecia da mesma altura que a de 312 catequisandos. E o erro
   * do eixo duplo por outro caminho.
   */
  const faceta = (titulo, corpo, escala) => `
    <div class="ind-faceta">
      <h3 class="ind-sub">${esc(titulo)}</h3>
      ${escala ? `<p class="ind-escala">${esc(escala)}</p>` : ''}
      ${corpo}
    </div>`;

  const facetaEvolucao = (titulo, pontos, rotulo) =>
    faceta(
      titulo,
      colunas(pontos, rotulo),
      `escala própria — topo em ${inteiro(Math.max(...pontos.map((p) => p.valor), 1))}`
    );

  /** Entraram para a direita, saíram para a esquerda: a pergunta é de polaridade. */
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
  const tabela = (cabecalho, linhas, opts = {}) => {
    if (!linhas.length) return vazio(opts.vazio || 'Nenhum registro com este filtro.');
    return `
      <div class="ind-tabela-rolagem">
        <table class="ind-tabela">
          <thead><tr>${cabecalho.map((c) => `<th>${esc(c)}</th>`).join('')}</tr></thead>
          <tbody>
            ${linhas
              .map(
                (l) =>
                  `<tr>${l
                    .map((c, i) => `<td${i === 0 ? '' : ' class="num"'}>${c}</td>`)
                    .join('')}</tr>`
              )
              .join('')}
          </tbody>
        </table>
      </div>`;
  };

  /** Selo de situação, com o texto junto — nunca só a cor. */
  const selo = (tipo, texto) => `<span class="status ${tipo}">${esc(texto)}</span>`;

  const seloFrequencia = (situacao) => {
    const mapa = {
      REGULAR: ['ok', 'Regular'],
      EM_RISCO: ['warning', 'Perto do limite'],
      ABAIXO_DO_MINIMO: ['error', 'Abaixo do mínimo'],
      SEM_APURACAO: ['neutro', 'Sem apuração'],
      NAO_SE_APLICA: ['neutro', 'Não se aplica']
    };
    const par = mapa[situacao] || ['neutro', situacao || '—'];
    return selo(par[0], par[1]);
  };

  // -------------------------------------------------------------- vista resumo

  const atalho = (chave, titulo, texto) => `
    <button type="button" class="ind-atalho" data-ir="${chave}">
      <strong>${esc(titulo)}</strong>
      <span class="muted">${esc(texto)}</span>
      <span class="ind-atalho-seta" aria-hidden="true">→</span>
    </button>`;

  const vistaResumo = () => {
    const m = dados.movimento;
    const sairam = m.concluiram.valor + m.abandonaram.valor;

    const resumo = painel(
      'Resumo geral',
      'Os números do ano, cada um contra o mesmo período do ano anterior.',
      `<div class="ind-cartoes">
         ${cartao(dados.catequisandos, true)}
         ${cartao(dados.pessoasDistintas)}
         ${cartao(dados.catequistas, true)}
         ${cartao(m.retencao, true)}
         ${cartao(dados.formacoesNoAno)}
         ${cartao(dados.eventosNoAno)}
       </div>`
    );

    const crescimento = painel(
      'Crescimento ano a ano',
      'A catequese está crescendo ou encolhendo? Cada coluna é um ano; o ano em curso é contado até hoje.',
      `<div class="grid ind-dois">
         ${facetaEvolucao('Catequisandos', dados.evolucaoCatequisandos, 'catequisandos')}
         ${facetaEvolucao('Catequistas', dados.evolucaoCatequistas, 'catequistas')}
       </div>
       ${tabela(
         ['Ano', 'Catequisandos', 'Catequistas'],
         dados.evolucaoCatequisandos.map((p) => [
           esc(p.ano),
           inteiro(p.valor),
           inteiro((dados.evolucaoCatequistas.find((k) => k.ano === p.ano) || {}).valor || 0)
         ])
       )}`
    );

    const movimento = painel(
      'Quem entrou e quem saiu',
      'Comparar dois totais não responde isso: 300 num ano e 300 no outro pode ser a mesma gente ou uma turma inteira trocada.',
      `${divergente(m.entraram.valor, sairam)}
       <div class="ind-cartoes">
         ${cartao(m.entraram)}${cartao(m.concluiram)}${cartao(m.abandonaram)}
       </div>
       ${tabela(
         ['Movimento', 'Pessoas'],
         [
           ['Entraram', inteiro(m.entraram.valor)],
           ['Permaneceram', inteiro(m.permaneceram.valor)],
           ['Saíram — concluíram o percurso', inteiro(m.concluiram.valor)],
           ['Saíram — abandonaram', inteiro(m.abandonaram.valor)],
           ['Transferidos (não contam dos dois lados)', inteiro(m.transferidos)],
           ['<strong>Saldo</strong>', `<strong>${comSinal(m.saldo)}</strong>`]
         ]
       )}
       <p class="muted">A retenção desconta quem concluiu do denominador: uma paróquia que
       forma muita gente apareceria com retenção ruim justamente por estar indo bem.</p>`
    );

    const comunidades = dados.porComunidade.length
      ? painel(
          'Onde está a catequese',
          'Como catequisandos e catequistas se distribuem entre as comunidades, e qual delas cresceu ou encolheu desde o ano passado.',
          `${barras(
            dados.porComunidade.map((l) => ({
              id: l.idComunidade,
              rotulo: l.nome,
              valor: l.catequisandos.valor
            })),
            { clicavel: true, titulo: 'Catequisandos por comunidade' }
          )}
           ${tabela(
             ['Comunidade', 'Catequisandos', 'Variação', 'Catequistas', 'Variação'],
             dados.porComunidade.map((l) => [
               esc(l.nome),
               inteiro(l.catequisandos.valor),
               comparacao(l.catequisandos),
               inteiro(l.catequistas.valor),
               comparacao(l.catequistas)
             ])
           )}
           <p class="muted">Clique numa comunidade para ver só ela. Quem atua em duas
           comunidades conta em cada uma, mas é uma pessoa só no total da paróquia — por
           isso a soma da coluna pode passar do total.</p>`
        )
      : '';

    const atalhos = painel(
      'Ver em detalhe',
      'Cada assunto tem tela própria, com os filtros que aquela pergunta pede.',
      `<div class="ind-atalhos">
         ${atalho('matriculas', 'Matrículas', 'Quantos estão cursando e quantos desistiram — neste ano e nos anteriores.')}
         ${atalho('frequencia', 'Frequência', 'Aproveitamento por comunidade, por turma e por catequisando.')}
         ${atalho('formacao', 'Formação', 'Quem participou e quem faltou; por catequista e por comunidade.')}
         ${atalho('eventos', 'Eventos', 'Por tipo e por nível, com quem participou de cada um.')}
       </div>`
    );

    const fundos = painel(
      'Fundos da catequese',
      null,
      `<div class="ind-reservado">
         <p><strong>Disponível quando existir a tela de lançamentos.</strong></p>
         <p class="muted">Aqui vão entradas, saídas e saldo do ano contra o ano anterior,
         saldo por comunidade e gasto por categoria — no mesmo formato de comparação do
         resto do relatório. O desenho da tabela está em
         <code>docs/especificacoes/tela-indicadores.md</code>.</p>
       </div>`
    );

    return resumo + crescimento + movimento + comunidades + atalhos + fundos;
  };

  // ---------------------------------------------------------- vista matriculas

  const vistaMatriculas = () => {
    const cartoes = painel(
      'Matrículas no ano',
      'Quantos estão na catequese agora, e como isso se compara com o ano passado.',
      `<div class="ind-cartoes">
         ${cartao(dados.total, true)}${cartao(dados.cursando, true)}
         ${cartao(dados.concluiram)}${cartao(dados.desistentes, true)}
       </div>`
    );

    const historico = painel(
      'Ano a ano',
      'A evasão deste ano é fora do normal ou é o padrão da paróquia? Só o histórico responde.',
      `${barras(
        dados.porAno.map((a) => ({ rotulo: String(a.ano), valor: a.desistentes })),
        { titulo: 'Desistências por ano', vazio: 'Sem histórico.' }
      )}
       ${tabela(
         ['Ano', 'Cursando', 'Concluíram', 'Não concluíram', 'Transferidos', 'Desistentes', 'Total'],
         dados.porAno
           .slice()
           .reverse()
           .map((a) => [
             esc(a.ano),
             inteiro(a.cursando),
             inteiro(a.concluiram),
             inteiro(a.naoConcluiram),
             inteiro(a.transferidos),
             inteiro(a.desistentes),
             `<strong>${inteiro(a.total)}</strong>`
           ])
       )}`
    );

    const porTurma = painel(
      'Por turma',
      'Onde as desistências se concentram — turma a turma, dentro do filtro escolhido.',
      tabela(
        ['Turma', 'Comunidade', 'Cursando', 'Concluíram', 'Desistentes', 'Total'],
        dados.porTurma.map((t) => [
          esc(t.turma),
          esc(t.comunidade),
          inteiro(t.cursando),
          inteiro(t.concluiram),
          inteiro(t.desistentes),
          `<strong>${inteiro(t.total)}</strong>`
        ])
      )
    );

    return cartoes + historico + porTurma;
  };

  // ---------------------------------------------------------- vista frequencia

  const vistaFrequencia = () => {
    const cartoes = painel(
      'Aproveitamento',
      `Mínimo de ${dados.minimo}%. "Perto do limite" é quem está entre ${dados.alerta}% e ${dados.minimo}% — ainda dá tempo de recuperar.`,
      `<div class="ind-cartoes">
         ${cartao(dados.aproveitamento, true)}${cartao(dados.regulares)}
         ${cartao(dados.pertoDoLimite, true)}${cartao(dados.abaixo, true)}
       </div>
       <p class="muted">Só encontro fechado conta; cancelado não entra na conta de ninguém;
       falta justificada sai da conta. Turma sem encontro fechado aparece sem percentual —
       nulo, não zero.</p>`
    );

    const turmas = painel(
      'Por turma',
      'Da pior média para a melhor: a primeira linha é onde olhar primeiro.',
      `${barras(
        dados.turmas
          .filter((t) => t.media !== null)
          .map((t) => ({ id: t.idTurma, rotulo: `${t.turma} · ${t.comunidade}`, valor: t.media })),
        {
          clicavel: true,
          titulo: 'Aproveitamento por turma',
          formatar: (v) => percentual(v),
          vazio: 'Nenhuma turma apurada ainda.'
        }
      )}
       ${tabela(
         ['Turma', 'Comunidade', 'Média', 'Regulares', 'Perto do limite', 'Abaixo', 'Apurados', 'Encontros fechados'],
         dados.turmas.map((t) => [
           esc(t.turma),
           esc(t.comunidade),
           t.exigeFrequencia ? percentual(t.media) : '<span class="muted">não se aplica</span>',
           inteiro(t.regulares),
           inteiro(t.pertoDoLimite),
           inteiro(t.abaixo),
           inteiro(t.apurados),
           inteiro(t.encontrosFechados)
         ])
       )}
       <p class="muted">Clique numa turma para ver catequisando por catequisando.</p>`
    );

    const pessoas = dados.catequisandos.length
      ? painel(
          'Catequisando a catequisando',
          'Do menor percentual para o maior, dentro da turma escolhida.',
          tabela(
            ['Catequisando', 'Frequência', 'Situação', 'Presenças', 'Faltas', 'Justificadas', 'Encontros'],
            dados.catequisandos.map((c) => [
              esc(c.nome),
              percentual(c.percentual),
              seloFrequencia(c.situacao),
              inteiro(c.presencas),
              inteiro(c.faltas),
              inteiro(c.justificadas),
              inteiro(c.encontros)
            ])
          )
        )
      : '';

    return cartoes + turmas + pessoas;
  };

  // ------------------------------------------------------------ vista formacao

  const vistaFormacao = () => {
    const cartoes = painel(
      'Participação nas formações',
      'A pergunta aqui é participação, não quantidade: inscritos → participaram → atingiram o mínimo.',
      `<div class="ind-cartoes">
         ${cartao(dados.inscritos, true)}${cartao(dados.participaram, true)}
         ${cartao(dados.atingiramMinimo, true)}
       </div>`
    );

    const porNivel = painel(
      'Por nível',
      'Um nível com muitos inscritos e poucos participantes é o que precisa saltar aos olhos.',
      tabela(
        ['Nível', 'Formações', 'Encontros', 'Inscritos', 'Participaram', 'Atingiram o mínimo', 'Participação'],
        dados.porNivel.map((n) => [
          esc(n.rotulo),
          inteiro(n.formacoes),
          inteiro(n.encontrosRealizados),
          inteiro(n.inscritos.valor),
          inteiro(n.participaram.valor),
          inteiro(n.atingiramMinimo.valor),
          percentual(n.taxaParticipacao.valor)
        ])
      )
    );

    const catequistas = painel(
      'Catequista a catequista',
      'Quem foi e quem não foi — do mais presente para o menos.',
      `${barras(
        dados.catequistas
          .slice(0, 15)
          .map((c) => ({ rotulo: c.nome, valor: c.percentual === null ? 0 : c.percentual })),
        {
          titulo: 'Presença por catequista',
          formatar: (v) => percentual(v),
          vazio: 'Nenhum catequista inscrito com este filtro.'
        }
      )}
       ${tabela(
         ['Catequista', 'Comunidade', 'Formações', 'Presenças', 'Encontros possíveis', 'Presença', 'Mínimo'],
         dados.catequistas.map((c) => [
           esc(c.nome),
           esc(c.comunidade),
           inteiro(c.formacoes),
           inteiro(c.presencas),
           inteiro(c.encontrosPossiveis),
           percentual(c.percentual),
           c.atingiuMinimo ? selo('ok', 'Atingiu') : selo('warning', 'Não atingiu')
         ])
       )}
       ${dados.catequistas.length > 15
         ? '<p class="muted">O gráfico mostra os 15 primeiros; a tabela traz todos.</p>'
         : ''}`
    );

    const comunidades = painel(
      'Por comunidade',
      'Qual comunidade mandou mais gente às formações.',
      `${barras(
        dados.comunidades.map((c) => ({
          rotulo: c.nome,
          valor: c.percentual === null ? 0 : c.percentual
        })),
        { titulo: 'Participação por comunidade', formatar: (v) => percentual(v) }
      )}
       ${tabela(
         ['Comunidade', 'Catequistas inscritos', 'Participaram', 'Participação'],
         dados.comunidades.map((c) => [
           esc(c.nome),
           inteiro(c.catequistas),
           inteiro(c.participaram),
           percentual(c.percentual)
         ])
       )}`
    );

    const trilhas = painel(
      'Formações do ano',
      null,
      tabela(
        ['Formação', 'Nível', 'Encontros realizados', 'Inscritos', 'Participaram', 'Atingiram', 'Mínimo'],
        dados.formacoes.map((f) => [
          esc(f.nome),
          esc(f.rotuloNivel),
          inteiro(f.encontrosRealizados),
          inteiro(f.inscritos),
          inteiro(f.participaram),
          inteiro(f.atingiram),
          `${esc(f.minimo)}%`
        ])
      )
    );

    return cartoes + porNivel + catequistas + comunidades + trilhas;
  };

  // ------------------------------------------------------------- vista eventos

  const vistaEventos = () => {
    const cartoes = painel(
      'Eventos no período',
      'Cancelado aparece separado: sumir com ele faria o total do ano encolher sem explicação.',
      `<div class="ind-cartoes">
         ${cartao(dados.total, true)}${cartao(dados.realizados)}${cartao(dados.cancelados)}
       </div>`
    );

    const tipos = dados.porTipo.filter((t) => t.valor > 0 || (t.base || 0) > 0);
    const niveis = dados.porNivel.filter((n) => n.valor > 0 || (n.base || 0) > 0);

    const quebra = painel(
      'Por tipo e por nível',
      'Tipo é o que o evento é; nível é de quem ele é. São perguntas independentes.',
      `<div class="grid ind-dois">
         ${faceta('Por tipo', barras(tipos.map((t) => ({ rotulo: t.rotulo, valor: t.valor })), {
           titulo: 'Eventos por tipo'
         }), null)}
         ${faceta('Por nível', barras(niveis.map((n) => ({ rotulo: n.rotulo, valor: n.valor })), {
           titulo: 'Eventos por nível',
           cor: 'var(--graf-2)'
         }), null)}
       </div>
       ${tabela(
         ['Tipo', 'Neste ano', 'No ano anterior'],
         tipos.map((t) => [esc(t.rotulo), inteiro(t.valor), t.base === null ? '—' : inteiro(t.base)])
       )}`
    );

    const lista = painel(
      'Cada evento',
      'Quem participou de quê. Presença em branco quer dizer que ninguém registrou a chamada — não que ninguém foi.',
      tabela(
        ['Evento', 'Data', 'Tipo', 'Público', 'Situação', 'Catequistas', 'Catequisandos'],
        dados.eventos.map((e) => [
          `${esc(e.titulo)}${e.formacao ? `<br><span class="ind-base">${esc(e.formacao)}</span>` : ''}`,
          dataBr(e.data),
          esc(e.rotuloTipo),
          esc(e.publico),
          e.situacao === 'CANCELADO'
            ? selo('error', 'Cancelado')
            : e.situacao === 'REALIZADO'
              ? selo('ok', 'Realizado')
              : selo('neutro', 'Previsto'),
          e.catequistasPresentes === null
            ? '<span class="muted">—</span>'
            : inteiro(e.catequistasPresentes),
          e.catequisandosPresentes === null
            ? '<span class="muted">—</span>'
            : inteiro(e.catequisandosPresentes)
        ])
      )
    );

    return cartoes + quebra + lista;
  };

  const RENDER = {
    resumo: vistaResumo,
    matriculas: vistaMatriculas,
    frequencia: vistaFrequencia,
    formacao: vistaFormacao,
    eventos: vistaEventos
  };

  // ------------------------------------------------------------------- filtros

  const opcoesDe = (campo) => {
    const f = filtros[vista];
    const comunidade = f.idComunidade ? Number(f.idComunidade) : null;
    switch (campo) {
      case 'ano':
        return { rotulo: 'Ano', vazio: null, itens: opcoes.anos.map((a) => [a, a]) };
      case 'idComunidade':
        return {
          rotulo: 'Comunidade',
          vazio: 'Paróquia inteira',
          itens: opcoes.comunidades.map((c) => [c.id, c.nome])
        };
      case 'idTurma':
        // A lista encolhe com a comunidade escolhida: um select com todas as
        // turmas da paroquia nao e filtro, e obstaculo.
        return {
          rotulo: 'Turma',
          vazio: 'Todas as turmas',
          itens: opcoes.turmas
            .filter((t) => comunidade === null || t.idComunidade === comunidade)
            .map((t) => [t.id, t.nome])
        };
      case 'idCatequista':
        return {
          rotulo: 'Catequista',
          vazio: 'Todos os catequistas',
          itens: opcoes.catequistas
            .filter((c) => comunidade === null || c.idComunidade === comunidade)
            .map((c) => [c.id, c.nome])
        };
      case 'situacao':
        return {
          rotulo: 'Situação',
          vazio: 'Todas as situações',
          itens: opcoes.situacoesMatricula.map((s) => [s.valor, s.rotulo])
        };
      case 'tipo':
        return {
          rotulo: 'Tipo de evento',
          vazio: 'Todos os tipos',
          itens: opcoes.tiposEvento.map((t) => [t.valor, t.rotulo])
        };
      case 'nivel':
        return {
          rotulo: 'Nível',
          vazio: 'Todos os níveis',
          itens: opcoes.niveisEvento.map((n) => [n.valor, n.rotulo])
        };
      default:
        return null;
    }
  };

  const desenharFiltros = () => {
    const caixa = document.getElementById('ind-filtros');
    if (!caixa || !opcoes) return;
    const f = filtros[vista];

    caixa.innerHTML = VISTAS[vista].campos
      .map((campo) => {
        const cfg = opcoesDe(campo);
        if (!cfg) return '';
        const escolhido = String(f[campo] === undefined || f[campo] === null ? '' : f[campo]);
        const linhas = (cfg.vazio ? [['', cfg.vazio]] : []).concat(cfg.itens);
        return `
          <label class="ind-filtro">
            ${esc(cfg.rotulo)}
            <select data-campo="${campo}">
              ${linhas
                .map(
                  (par) =>
                    `<option value="${esc(par[0])}"${String(par[0]) === escolhido ? ' selected' : ''}>${esc(par[1])}</option>`
                )
                .join('')}
            </select>
          </label>`;
      })
      .join('');

    caixa.querySelectorAll('select[data-campo]').forEach((select) => {
      select.addEventListener('change', () => {
        const campo = select.dataset.campo;
        f[campo] = select.value;
        // Trocar de comunidade invalida turma e catequista: manter um vinculo
        // que nao pertence mais ao recorte devolveria tela vazia sem explicacao.
        if (campo === 'idComunidade') {
          if ('idTurma' in f) f.idTurma = '';
          if ('idCatequista' in f) f.idCatequista = '';
        }
        carregar();
      });
    });
  };

  const fichasDoFiltro = () => {
    const f = filtros[vista];
    const ativos = VISTAS[vista].campos
      .filter((campo) => campo !== 'ano' && f[campo])
      .map((campo) => {
        const cfg = opcoesDe(campo);
        const item = cfg.itens.find((par) => String(par[0]) === String(f[campo]));
        return { campo: campo, rotulo: cfg.rotulo, valor: item ? item[1] : f[campo] };
      });
    if (!ativos.length) return '';
    return `<div class="ind-fichas ind-nao-imprime">
        ${ativos
          .map(
            (a) =>
              `<span class="ind-ficha">${esc(a.rotulo)}: ${esc(a.valor)}
                 <button type="button" class="ind-ficha-x" data-limpar="${esc(a.campo)}"
                         aria-label="Remover filtro">×</button></span>`
          )
          .join('')}
        <button type="button" class="ind-limpar-tudo" data-limpar="*">Limpar filtros</button>
      </div>`;
  };

  // ------------------------------------------------------------------- desenho

  const alvo = () => document.getElementById('ind-conteudo');

  const desenharSubnav = () => {
    document.querySelectorAll('.ind-subnav-btn').forEach((btn) => {
      const ativa = btn.dataset.vista === vista;
      btn.classList.toggle('active', ativa);
      btn.setAttribute('aria-current', ativa ? 'page' : 'false');
    });
    const titulo = document.getElementById('ind-titulo');
    if (titulo) titulo.textContent = `Indicadores · ${VISTAS[vista].rotulo}`;
  };

  const desenharAvisos = () => {
    const caixa = document.getElementById('ind-avisos');
    if (!caixa) return;
    caixa.innerHTML =
      fichasDoFiltro() +
      ((dados && dados.avisos) || [])
        .map((a) => `<div class="status warning">${esc(a)}</div>`)
        .join('');

    caixa.querySelectorAll('[data-limpar]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const campo = btn.dataset.limpar;
        const f = filtros[vista];
        if (campo === '*') {
          Object.keys(f).forEach((k) => {
            if (k !== 'ano') f[k] = '';
          });
        } else {
          f[campo] = '';
          if (campo === 'idComunidade') {
            if ('idTurma' in f) f.idTurma = '';
            if ('idCatequista' in f) f.idCatequista = '';
          }
        }
        carregar();
      });
    });
  };

  const desenhar = () => {
    document.getElementById('ind-cabecalho').textContent = dados.cabecalho;
    desenharSubnav();
    desenharFiltros();
    desenharAvisos();
    alvo().innerHTML = RENDER[vista]();

    // Atalhos do resumo para as telas de detalhe.
    alvo().querySelectorAll('[data-ir]').forEach((btn) => {
      btn.addEventListener('click', () => irPara(btn.dataset.ir));
    });

    // Drill-down: clicar numa barra aplica aquilo como filtro DA TELA ATUAL.
    // No resumo a barra e comunidade; na frequencia, turma.
    alvo().querySelectorAll('[data-filtro]').forEach((el) => {
      el.addEventListener('click', () => {
        const f = filtros[vista];
        const campo = vista === 'frequencia' ? 'idTurma' : 'idComunidade';
        f[campo] = el.dataset.filtro;
        carregar();
      });
    });
  };

  const mostrarEstado = (html) => {
    alvo().innerHTML = `<section class="panel">${html}</section>`;
  };

  const irPara = (novaVista) => {
    if (!VISTAS[novaVista]) return;
    // O ano viaja entre as telas; o resto, nao. Quem estava olhando 2025 quer
    // continuar em 2025 -- mas "turma" de uma tela nao quer dizer nada na
    // outra, e filtro herdado invisivel e a pior forma de ler numero errado.
    const anoAtual = filtros[vista].ano;
    vista = novaVista;
    if (anoAtual) filtros[vista].ano = anoAtual;
    carregar();
  };

  // -------------------------------------------------------------------- dados

  const carregar = async () => {
    if (carregando) return;
    carregando = true;
    desenharSubnav();
    document.getElementById('ind-cabecalho').textContent = 'Apurando…';
    mostrarEstado('<p class="muted">Apurando…</p>');

    try {
      if (!opcoes) {
        const r = await fetch('/api/indicadores/opcoes');
        if (!r.ok) throw new Error(await mensagemDeErro(r));
        opcoes = await r.json();
        // Sem ano escolhido, o primeiro da lista (o mais recente) vale para todas.
        const primeiro = opcoes.anos[0];
        Object.keys(filtros).forEach((v) => {
          if (!filtros[v].ano) filtros[v].ano = String(primeiro);
        });
      }

      const params = new URLSearchParams();
      const f = filtros[vista];
      VISTAS[vista].campos.forEach((campo) => {
        if (f[campo]) params.set(campo, f[campo]);
      });

      const resposta = await fetch(`/api/indicadores${VISTAS[vista].rota}?${params}`);
      if (!resposta.ok) throw new Error(await mensagemDeErro(resposta));
      dados = await resposta.json();
      desenhar();
    } catch (err) {
      document.getElementById('ind-cabecalho').textContent = 'Não foi possível apurar.';
      desenharSubnav();
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
    document.querySelectorAll('.ind-subnav-btn').forEach((btn) => {
      btn.addEventListener('click', () => irPara(btn.dataset.vista));
    });
    const imprimir = document.getElementById('ind-imprimir');
    // "Salvar como PDF" e a impressao do proprio navegador -- sem biblioteca.
    if (imprimir) imprimir.addEventListener('click', () => window.print());
  };

  // script.js chama esta funcao ao abrir a aba.
  window.carregarIndicadores = () => {
    if (!ligado) {
      ligar();
      ligado = true;
    }
    carregar();
  };
})();
