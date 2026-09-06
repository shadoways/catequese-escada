"""Consultar Catequistas: quem enxerga a aba, e o detalhe mostra o que a API manda.

O que estes testes protegem, em ordem de importancia:

  1. QUEM VE A ABA. Catequista comum nao tem "Consultar Catequistas" no menu --
     a especificacao (tela-catequistas.md, secao 2) e explicita: e tela de
     coordenador (so visualizacao) e coordenador paroquial (visualiza e altera
     a configuracao, em Configuracoes). Isto e so o CONFORTO visual da aba;
     quem barra de verdade sao os endpoints (EscopoAcessoService), verificados
     por leitura porque o Gradle nao roda neste sandbox.
  2. A COR SEGUE O QUE A API MANDOU, sem a tela reinterpretar. VERDE/AMARELO/
     VERMELHO/NEUTRO vem prontos em `estado`; a tela so mapeia para a classe
     .status certa -- o calculo de data e percentual e todo do backend
     (CurriculoCatequistaService), para as duas contas (lista e detalhe)
     nunca poderem divergir.
  3. O HISTORICO DESCREVE FALTA E JUSTIFICATIVA, nao so presenca -- foi pedido
     explicito do Gabriel ("deve ficar descrito que nao participou, ou por
     falta, ou se deu justificativa"), por isso cada encontro vira um selo
     proprio (Presente/Faltou/Justificada), nunca so uma data.
  4. SEM INSCRICAO NO ANO tem aviso proprio, igual ao que tela-formacao.md ja
     usa -- nao inventar um segundo jeito de dizer a mesma coisa.
  5. O TITULO DO DETALHE E SO O NOME -- "Currículo" saiu do texto (pedido do
     Gabriel: nao e profissao). O nome vem da propria lista, sem esperar o
     fetch, entao a tela nunca fica com um titulo generico piscando.
  6. FILTRO POR COMUNIDADE, junto do de nome -- os dois filtram a MESMA lista
     ja carregada, sem chamada nova (mesma logica da busca por nome).
  7. A ABA "CONHECIMENTOS" e um checklist com PERMISSAO RESOLVIDA PELO
     SERVIDOR (`podeEditar`, de EscopoAcessoService.ehAdmin()) -- a tela so
     habilita ou desabilita o checkbox, nunca decide sozinha quem e
     coordenador paroquial. Coordenador de comunidade ve tudo desabilitado.
  8. A ABA "FORMACOES" e o historico completo (todos os anos, nao so o ano
     corrente do Resumo) com filtro de situacao (chip, igual Agenda) e de
     ano/mes (select) -- os TRES filtros ficam em grupos SEPARADOS, pedido
     explicito de usabilidade.
"""
from playwright.sync_api import sync_playwright
import json, pathlib, sys

url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()

LISTA = [
    {"idCatequista": 1, "nome": "Ana Souza", "comunidade": "Matriz", "ano": 2026,
     "percentual": 40, "minimoAgregado": 80, "estado": "VERMELHO",
     "estadoRotulo": "Abaixo do mínimo — ano de formação encerrado"},
    {"idCatequista": 2, "nome": "Bruno Lima", "comunidade": "Matriz", "ano": 2026,
     "percentual": 65, "minimoAgregado": 80, "estado": "AMARELO",
     "estadoRotulo": "Abaixo do mínimo — ano de formação fechando"},
    {"idCatequista": 3, "nome": "Carla Reis", "comunidade": "São José", "ano": 2026,
     "percentual": 90, "minimoAgregado": 80, "estado": "VERDE",
     "estadoRotulo": "Dentro do mínimo"},
    {"idCatequista": 4, "nome": "Diego Alves", "comunidade": "São José", "ano": 2026,
     "percentual": None, "minimoAgregado": 80, "estado": "NEUTRO",
     "estadoRotulo": "Sem apuração ainda"},
]

CURRICULO_PREENCHIDO = {
    "idCatequista": 2, "nome": "Bruno Lima", "comunidade": "Matriz", "ano": 2026,
    "percentualAgregado": 65, "minimoAgregado": 80, "estado": "AMARELO",
    "estadoRotulo": "Abaixo do mínimo — ano de formação fechando",
    "diocesana": [{
        "idFormacao": 10, "nome": "Formação diocesana 2026", "nivel": "DIOCESANO",
        "nivelRotulo": "Diocesano", "ano": 2026, "percentualMinimo": 80, "percentual": 50,
        "atingiuMinimo": False,
        "encontros": [
            {"data": "2026-03-01", "situacao": "PRESENTE", "justificativa": None},
            {"data": "2026-04-01", "situacao": "FALTA", "justificativa": None},
            {"data": "2026-05-01", "situacao": "JUSTIFICADA", "justificativa": "Trabalho"},
        ],
    }],
    "regional": [],
    "paroquial": [{
        "idFormacao": 11, "nome": "Escola de catequistas 2026", "nivel": "PAROQUIAL",
        "nivelRotulo": "Paroquial", "ano": 2026, "percentualMinimo": 80, "percentual": 100,
        "atingiuMinimo": True,
        "encontros": [{"data": "2026-06-10", "situacao": "PRESENTE", "justificativa": None}],
    }],
}

CURRICULO_SEM_INSCRICAO = {
    "idCatequista": 4, "nome": "Diego Alves", "comunidade": "São José", "ano": 2026,
    "percentualAgregado": None, "minimoAgregado": 80, "estado": "NEUTRO",
    "estadoRotulo": "Sem apuração ainda",
    "diocesana": [], "regional": [], "paroquial": [],
}

# Mesmos 4 encontros de CURRICULO_PREENCHIDO (Bruno), so que "achatados" numa
# lista unica -- e o que a aba Formacoes mostra -- MAIS um encontro de 2025,
# so para o filtro de ano ter o que separar (o resumo nunca mostraria isso,
# porque so olha o ano corrente -- regra 2 da especificacao).
HISTORICO_BRUNO = [
    {"idFormacao": 10, "formacaoNome": "Formação diocesana 2026", "nivel": "DIOCESANO",
     "nivelRotulo": "Diocesano", "ano": 2026, "data": "2026-03-01",
     "situacao": "PRESENTE", "justificativa": None},
    {"idFormacao": 10, "formacaoNome": "Formação diocesana 2026", "nivel": "DIOCESANO",
     "nivelRotulo": "Diocesano", "ano": 2026, "data": "2026-04-01",
     "situacao": "FALTA", "justificativa": None},
    {"idFormacao": 10, "formacaoNome": "Formação diocesana 2026", "nivel": "DIOCESANO",
     "nivelRotulo": "Diocesano", "ano": 2026, "data": "2026-05-01",
     "situacao": "JUSTIFICADA", "justificativa": "Trabalho"},
    {"idFormacao": 11, "formacaoNome": "Escola de catequistas 2026", "nivel": "PAROQUIAL",
     "nivelRotulo": "Paroquial", "ano": 2026, "data": "2026-06-10",
     "situacao": "PRESENTE", "justificativa": None},
    {"idFormacao": 9, "formacaoNome": "Formação diocesana 2025", "nivel": "DIOCESANO",
     "nivelRotulo": "Diocesano", "ano": 2025, "data": "2025-08-15",
     "situacao": "PRESENTE", "justificativa": None},
]

# possui=False para "Artigos do Credo" de proposito -- e o item que prova que
# o checklist nao vem tudo marcado por padrao.
CONHECIMENTOS_ITENS = [
    {"idRequisito": 1, "nome": "Kerigma", "possui": True},
    {"idRequisito": 2, "nome": "Artigos do Credo", "possui": False},
    {"idRequisito": 3, "nome": "Pai Nosso", "possui": True},
]

CATALOGO_CONHECIMENTOS = [
    {"idRequisito": 1, "nome": "Kerigma", "ativo": True},
    {"idRequisito": 2, "nome": "Artigos do Credo", "ativo": True},
    {"idRequisito": 3, "nome": "Pai Nosso", "ativo": True},
    {"idRequisito": 4, "nome": "Mariologia", "ativo": False},
]


def stub(tipo):
    curriculos = {2: CURRICULO_PREENCHIDO, 4: CURRICULO_SEM_INSCRICAO}
    historicos = {2: HISTORICO_BRUNO}
    # podeEditar vem do servidor (EscopoAcessoService.ehAdmin()) -- o stub
    # imita exatamente essa regra, para o teste provar que a TELA obedece o
    # campo, e nao decide sozinha pelo tipo de usuario.
    checklist = {"podeEditar": tipo == "COORDENADOR_PAROQUIAL", "itens": CONHECIMENTOS_ITENS}
    return """
    window.fetch = async (u, opts) => {
      const s = String(typeof u === 'string' ? u : (u && u.url) || '');
      const metodo = (opts && opts.method) || 'GET';
      const j = (x) => new Response(JSON.stringify(x),
          {status: 200, headers: {'Content-Type': 'application/json'}});
      const curriculos = %(curriculos)s;
      const historicos = %(historicos)s;
      const checklist = %(checklist)s;
      const catalogo = %(catalogo)s;

      if (s.includes('/api/catequistas/curriculo')) return j(%(lista)s);
      if (s.includes('/api/conhecimentos-exigidos')) return j(catalogo);

      let m = s.match(/\\/api\\/catequistas\\/(\\d+)\\/curriculo/);
      if (m) return j(curriculos[m[1]] || curriculos[Number(m[1])]);

      m = s.match(/\\/api\\/catequistas\\/(\\d+)\\/formacoes/);
      if (m) return j(historicos[m[1]] || historicos[Number(m[1])] || []);

      // PUT /api/catequistas/{id}/conhecimentos/{idRequisito} -- marcar/desmarcar.
      m = s.match(/\\/api\\/catequistas\\/(\\d+)\\/conhecimentos\\/(\\d+)/);
      if (m && metodo === 'PUT') return j({});

      // GET /api/catequistas/{id}/conhecimentos -- o checklist.
      m = s.match(/\\/api\\/catequistas\\/(\\d+)\\/conhecimentos/);
      if (m) return j(checklist);

      return j({});
    };
    localStorage.setItem('catequese.token', 't');
    localStorage.setItem('catequese.usuario', JSON.stringify(
      {nome: 'G', username: 'g', tipo: '%(tipo)s', admin: %(admin)s, podeEditar: true}));
    """ % {
        "lista": json.dumps(LISTA),
        "curriculos": json.dumps(curriculos),
        "historicos": json.dumps(historicos),
        "checklist": json.dumps(checklist),
        "catalogo": json.dumps(CATALOGO_CONHECIMENTOS),
        "tipo": tipo,
        "admin": "true" if tipo == "COORDENADOR_PAROQUIAL" else "false",
    }


falhas = []


def checar(nome, condicao, detalhe=''):
    if not condicao:
        falhas.append(nome)
    print(f"  {'OK' if condicao else '!!'}  {nome}"
          f"{(' — ' + str(detalhe)) if detalhe and not condicao else ''}")


with sync_playwright() as p:
    navegador = p.chromium.launch(executable_path='/opt/pw-browsers/chromium')

    def abrir(tipo, largura=1280):
        page = navegador.new_page(viewport={'width': largura, 'height': 900},
                                  reduced_motion='reduce')
        page.add_init_script(stub(tipo))
        page.goto(url)
        page.wait_for_timeout(400)
        return page

    print('--- quem ve a aba no menu lateral')
    pcat = abrir('CATEQUISTA')
    checar('catequista comum NAO ve o botao da aba',
           pcat.eval_on_selector('.tab-btn[data-tab="catequistas"]', 'e => e.hidden') is True)

    pcoord = abrir('COORDENADOR')
    checar('coordenador de comunidade VE o botao da aba',
           pcoord.eval_on_selector('.tab-btn[data-tab="catequistas"]', 'e => e.hidden') is False)

    ppar = abrir('COORDENADOR_PAROQUIAL')
    checar('coordenador paroquial VE o botao da aba',
           ppar.eval_on_selector('.tab-btn[data-tab="catequistas"]', 'e => e.hidden') is False)

    print('--- a lista mostra a cor que a API mandou, sem reinterpretar')
    page = abrir('COORDENADOR_PAROQUIAL')
    page.click('button.tab-btn[data-tab="catequistas"]')
    page.wait_for_timeout(500)
    selos = page.eval_on_selector_all(
        '#cat-lista [data-id] .status', 'e => e.map(x => x.className)')
    checar('4 catequistas na lista, um selo cada', len(selos) == 4, selos)
    checar('vermelho tem classe error', 'error' in selos[0])
    checar('amarelo tem classe warning', 'warning' in selos[1])
    checar('verde tem classe ok', 'ok' in selos[2])
    checar('sem apuracao tem classe neutro', 'neutro' in selos[3])

    print('--- filtro por comunidade, junto da busca por nome')
    page.select_option('#cat-filtro-comunidade', label='São José')
    page.wait_for_timeout(300)
    linhas_sj = page.eval_on_selector_all('#cat-lista [data-id]', 'e => e.length')
    checar('"São José" mostra Carla e Diego (2)', linhas_sj == 2, linhas_sj)
    page.select_option('#cat-filtro-comunidade', label='Todas as comunidades')
    page.wait_for_timeout(300)

    print('--- abrir o detalhe de quem tem formacao')
    page.click('#cat-lista [data-id="2"]')
    page.wait_for_timeout(400)
    checar('a lista some e o detalhe aparece',
           page.eval_on_selector('#cat-tela-lista', 'e => e.hidden') is True and
           page.eval_on_selector('#cat-tela-curriculo', 'e => e.hidden') is False)

    print('--- o titulo do detalhe e so o nome, sem a palavra "Currículo"')
    titulo = page.inner_text('#cat-curriculo-titulo').strip()
    checar('titulo == "Bruno Lima"', titulo == 'Bruno Lima', titulo)

    print('--- aba Resumo (a mesma tela de antes, agora dentro da subnav)')
    checar('abre na aba Resumo', page.eval_on_selector('#cat-vista-resumo', 'e => e.hidden') is False)
    texto = page.inner_text('#cat-curriculo-corpo')
    checar('a coluna diocesana aparece com a formacao', 'Formação diocesana 2026' in texto)
    checar('a coluna paroquial aparece com a formacao', 'Escola de catequistas 2026' in texto)
    checar('regional sem inscricao diz isso', 'Nenhuma inscrição neste nível' in texto)

    print('--- cada encontro descreve presenca, falta OU justificativa -- nao so a data')
    checar('tem um selo "Presente"', 'Presente' in texto)
    checar('tem um selo "Faltou" (nao so a data)', 'Faltou' in texto)
    checar('tem um selo "Justificada"', 'Justificada' in texto)

    print('--- o checkbox do Resumo bate com atingiuMinimo DAQUELA formacao, nao o agregado')
    marcados = page.eval_on_selector_all(
        '.cat-formacao input[type="checkbox"]', 'e => e.map(x => x.checked)')
    checar('diocesana (50%, minimo 80%) desmarcada, paroquial (100%) marcada',
           marcados == [False, True], marcados)

    print('--- aba Conhecimentos: checklist com podeEditar do servidor')
    page.click('.adm-subnav-btn[data-cat-vista="conhecimentos"]')
    page.wait_for_timeout(300)
    checar('troca para Conhecimentos (Resumo esconde, Conhecimentos aparece)',
           page.eval_on_selector('#cat-vista-resumo', 'e => e.hidden') is True and
           page.eval_on_selector('#cat-vista-conhecimentos', 'e => e.hidden') is False)
    texto_conhec = page.inner_text('#cat-conhecimentos-lista')
    checar('lista os 3 conhecimentos do catalogo ativo',
           all(n in texto_conhec for n in ('Kerigma', 'Artigos do Credo', 'Pai Nosso')))
    marcados_conhec = page.eval_on_selector_all(
        '.cat-conhecimento-item input[type="checkbox"]', 'e => e.map(x => x.checked)')
    checar('Kerigma e Pai Nosso marcados, Credo nao (bate com o checklist da API)',
           marcados_conhec == [True, False, True], marcados_conhec)
    habilitados_par = page.eval_on_selector_all(
        '.cat-conhecimento-item input[type="checkbox"]', 'e => e.map(x => !x.disabled)')
    checar('coordenador paroquial PODE marcar (checkbox habilitado)',
           habilitados_par == [True, True, True], habilitados_par)

    print('--- marcar um conhecimento chama o PUT e atualiza a tela')
    page.click('.cat-conhecimento-item input[type="checkbox"]')
    page.wait_for_timeout(300)
    status_marcado = page.inner_text('#cat-conhecimentos-status')
    checar('mensagem de salvo aparece', 'Salvo' in status_marcado, status_marcado)

    print('--- coordenador de comunidade so visualiza os conhecimentos (sem editar)')
    pconhec_coord = abrir('COORDENADOR')
    pconhec_coord.click('button.tab-btn[data-tab="catequistas"]')
    pconhec_coord.wait_for_timeout(400)
    pconhec_coord.click('#cat-lista [data-id="2"]')
    pconhec_coord.wait_for_timeout(300)
    pconhec_coord.click('.adm-subnav-btn[data-cat-vista="conhecimentos"]')
    pconhec_coord.wait_for_timeout(300)
    habilitados_coord = pconhec_coord.eval_on_selector_all(
        '.cat-conhecimento-item input[type="checkbox"]', 'e => e.map(x => !x.disabled)')
    checar('coordenador de comunidade NAO pode marcar (checkbox desabilitado)',
           habilitados_coord == [False, False, False], habilitados_coord)
    checar('aviso explica por que esta desabilitado',
           'Somente o coordenador paroquial' in pconhec_coord.inner_text('#cat-conhecimentos-status'))

    print('--- aba Formacoes: historico completo (todos os anos), com filtros separados')
    page.click('.adm-subnav-btn[data-cat-vista="formacoes"]')
    page.wait_for_timeout(300)
    checar('troca para Formacoes',
           page.eval_on_selector('#cat-vista-conhecimentos', 'e => e.hidden') is True and
           page.eval_on_selector('#cat-vista-formacoes', 'e => e.hidden') is False)
    linhas_hist = page.eval_on_selector_all('.cat-historico-linha', 'e => e.length')
    checar('mostra os 5 encontros do historico (2026 e 2025 juntos)', linhas_hist == 5, linhas_hist)

    page.click('#cat-formacoes-filtro-situacao button[data-valor="FALTA"]')
    page.wait_for_timeout(300)
    linhas_falta = page.eval_on_selector_all('.cat-historico-linha', 'e => e.length')
    checar('filtro de situacao "Faltou" mostra so 1', linhas_falta == 1, linhas_falta)

    page.click('#cat-formacoes-filtro-situacao button[data-valor=""]')
    page.wait_for_timeout(200)
    page.select_option('#cat-formacoes-filtro-ano', label='2025')
    page.wait_for_timeout(300)
    linhas_2025 = page.eval_on_selector_all('.cat-historico-linha', 'e => e.length')
    checar('filtro de ano 2025 mostra so o encontro de 2025', linhas_2025 == 1, linhas_2025)
    page.select_option('#cat-formacoes-filtro-ano', label='Todos os anos')
    page.wait_for_timeout(300)

    print('--- voltar retorna para a lista')
    page.click('#cat-voltar')
    page.wait_for_timeout(300)
    checar('a lista volta a aparecer',
           page.eval_on_selector('#cat-tela-lista', 'e => e.hidden') is False)

    print('--- sem inscricao no ano tem o aviso, nao colunas vazias')
    page.click('#cat-lista [data-id="4"]')
    page.wait_for_timeout(400)
    checar('mensagem de "nao esta inscrito" aparece',
           'não está inscrito em nenhuma formação este ano' in page.inner_text('#cat-curriculo-corpo'))

    print('--- a busca filtra por nome sem nova chamada')
    page.click('#cat-voltar')
    page.wait_for_timeout(300)
    page.fill('#cat-busca', 'bruno')
    page.wait_for_timeout(300)
    linhas = page.eval_on_selector_all('#cat-lista [data-id]', 'e => e.length')
    checar('busca por "bruno" mostra so 1', linhas == 1, linhas)
    page.fill('#cat-busca', '')

    print('--- 400px: nada estoura o pai, nas tres abas do detalhe')
    p400 = abrir('COORDENADOR_PAROQUIAL', 400)
    p400.click('button.tab-btn[data-tab="catequistas"]')
    p400.wait_for_timeout(400)
    p400.click('#cat-lista [data-id="2"]')
    p400.wait_for_timeout(400)

    def sem_estouro(pagina, rotulo):
        estouros = pagina.evaluate("""() => {
          const raiz = document.getElementById('tab-catequistas');
          const limite = raiz.getBoundingClientRect().right + 1;
          return Array.from(raiz.querySelectorAll('*'))
            .filter(e => e.getBoundingClientRect().right > limite)
            .map(e => e.tagName + '.' + e.className).slice(0, 5);
        }""")
        checar(f'nenhum elemento passa da borda em 400px ({rotulo})', estouros == [], estouros)

    sem_estouro(p400, 'Resumo')
    p400.click('.adm-subnav-btn[data-cat-vista="conhecimentos"]')
    p400.wait_for_timeout(300)
    sem_estouro(p400, 'Conhecimentos')
    p400.click('.adm-subnav-btn[data-cat-vista="formacoes"]')
    p400.wait_for_timeout(300)
    sem_estouro(p400, 'Formações')
    checar('a pagina nao rola na horizontal',
           p400.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1'))

    navegador.close()

print()
if falhas:
    print(f"{len(falhas)} FALHA(S): " + '; '.join(falhas))
    sys.exit(1)
print('TODOS OS INVARIANTES OK')
