"""Chamada: filtro de comunidade/turma, e o recorte que cada papel enxerga.

O que estes testes protegem, em ordem de importancia:

  1. NESTE MENU SO TEM CHAMADA. Chegou a existir uma secao de Eventos (retiro,
     missa) na mesma tela -- foi removida a pedido: o catequista abre este
     menu para uma coisa so, marcar presenca, e outra secao ali (mesmo que
     relacionada) e ruido nesse momento.
  2. NADA APARECE ANTES DO "CONSULTAR". A lista de turmas vinha toda de uma
     vez, e quem enxerga mais de uma comunidade (coordenador, coordenador
     paroquial) tinha que descartar o resto no olho ate achar a turma que
     queria -- mesmo problema que a tela de Turmas e Inscricoes ja resolveu.
  3. O FILTRO NUNCA OFERECE MAIS DO QUE O BACKEND JA DEVOLVEU. As opcoes de
     comunidade vem das PROPRIAS turmas do usuario (`/api/chamada/minhas-
     turmas`), nao de `/api/comunidades` (a paroquia inteira). E assim que um
     catequista, que so recebe as turmas dele, so ve a comunidade dele no
     combo -- sem nenhuma regra de permissao nova NESTA tela: quem barra de
     verdade continua sendo o EscopoAcessoService no servidor.

Este script cobre o FRONTEND (a tela so mostra o que a API manda, e filtra
direito o que ja recebeu). O recorte por papel em si -- catequista so ve as
turmas em que atua, coordenador so as da propria comunidade, admin ve tudo --
e regra de dados em ChamadaService.minhasTurmas, verificada por leitura
(Gradle nao roda neste sandbox); aqui simulamos as duas respostas que o
backend daria a um coordenador e a um catequista para provar que a tela nao
adiciona nem esconde nada por conta propria.
"""
from playwright.sync_api import sync_playwright
import json, pathlib, sys

url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()

# Como o backend responderia para um COORDENADOR: turmas de duas comunidades,
# porque a comunidade dele tem duas turmas (o campo `idComunidade` aqui e o
# da TURMA, nao o de quem esta matriculado -- ver a nota em ChamadaService).
TURMAS_COORDENADOR = [
    {"idTurma": 10, "nome": "Eucaristia I - Matriz", "categoria": "EUCARISTIA", "etapa": 1,
     "ano": 2026, "matriculados": 14, "exigeFrequencia": True,
     "encontroAberto": None, "ultimoEncontro": "2026-08-01",
     "idComunidade": 1, "nomeComunidade": "Matriz"},
    {"idTurma": 11, "nome": "Eucaristia I - São José", "categoria": "EUCARISTIA", "etapa": 1,
     "ano": 2026, "matriculados": 12, "exigeFrequencia": True,
     "encontroAberto": None, "ultimoEncontro": None,
     "idComunidade": 2, "nomeComunidade": "São José"},
    {"idTurma": 20, "nome": "Adultos - Matriz", "categoria": "ADULTOS", "etapa": None,
     "ano": 2026, "matriculados": 9, "exigeFrequencia": True,
     "encontroAberto": None, "ultimoEncontro": None,
     "idComunidade": 1, "nomeComunidade": "Matriz"},
]

# Como o backend responderia para um CATEQUISTA que atua so numa turma: uma
# turma, uma comunidade -- o combo nao deve inventar "São José" do nada.
TURMAS_CATEQUISTA = [
    {"idTurma": 10, "nome": "Eucaristia I - Matriz", "categoria": "EUCARISTIA", "etapa": 1,
     "ano": 2026, "matriculados": 14, "exigeFrequencia": True,
     "encontroAberto": None, "ultimoEncontro": "2026-08-01",
     "idComunidade": 1, "nomeComunidade": "Matriz"},
]


def stub(turmas, tipo='COORDENADOR_PAROQUIAL'):
    return """
    window.fetch = async (u) => {
      const s = String(typeof u === 'string' ? u : (u && u.url) || '');
      const j = (x) => new Response(JSON.stringify(x),
          {status: 200, headers: {'Content-Type': 'application/json'}});
      if (s.includes('/api/chamada/minhas-turmas')) return j(%(turmas)s);
      return j({});
    };
    localStorage.setItem('catequese.token', 't');
    localStorage.setItem('catequese.usuario', JSON.stringify(
      {nome: 'G', username: 'g', tipo: '%(tipo)s', admin: true, podeEditar: true}));
    """ % {"turmas": json.dumps(turmas), "tipo": tipo}


falhas = []


def checar(nome, condicao, detalhe=''):
    if not condicao:
        falhas.append(nome)
    print(f"  {'OK' if condicao else '!!'}  {nome}"
          f"{(' — ' + str(detalhe)) if detalhe and not condicao else ''}")


with sync_playwright() as p:
    navegador = p.chromium.launch(executable_path='/opt/pw-browsers/chromium')

    def abrir(turmas, largura=1280, tipo='COORDENADOR_PAROQUIAL'):
        page = navegador.new_page(viewport={'width': largura, 'height': 900},
                                  reduced_motion='reduce')
        page.add_init_script(stub(turmas, tipo))
        page.goto(url)
        page.wait_for_timeout(400)
        page.click('button.tab-btn[data-tab="chamada"]')
        page.wait_for_timeout(700)
        return page

    def consultar(page):
        page.click('#cham-consultar')
        page.wait_for_timeout(500)

    print('--- neste menu so tem chamada')
    page = abrir(TURMAS_COORDENADOR)
    checar('nao existe mais secao de eventos nesta aba',
           page.eval_on_selector_all('#tab-chamada #cham-tela-eventos', 'e => e.length') == 0)
    checar('e nenhum cartao de evento sobrou na aba',
           page.eval_on_selector_all('#tab-chamada .evento-card', 'e => e.length') == 0)

    print('--- a lista so aparece quando se pede')
    checar('nenhuma turma antes do "Consultar"',
           page.eval_on_selector_all('#cham-turmas-lista [data-id-turma]', 'e => e.length') == 0)
    checar('a tela diz o que fazer',
           'Consultar' in page.inner_text('#cham-turmas-lista'))
    checar('o filtro de turma ja vem preenchido',
           page.eval_on_selector_all('#cham-filtro-turma option', 'e => e.length') == 4)

    page.select_option('#cham-filtro-comunidade', '1')
    page.wait_for_timeout(300)
    checar('mudar o filtro NAO consulta',
           page.eval_on_selector_all('#cham-turmas-lista [data-id-turma]', 'e => e.length') == 0)

    consultar(page)
    cartoes = page.eval_on_selector_all('#cham-turmas-lista [data-id-turma]', 'e => e.length')
    checar('o filtro de comunidade recorta a lista de turmas', cartoes == 2, cartoes)

    print('--- o combo de comunidade nunca inventa uma comunidade que o usuario nao tem')
    pc = abrir(TURMAS_CATEQUISTA, tipo='CATEQUISTA')
    opcoesComunidade = pc.eval_on_selector_all(
        '#cham-filtro-comunidade option', 'e => e.map(o => o.textContent.trim())')
    checar('catequista com uma turma so ve "Todas" e a comunidade dele',
           opcoesComunidade == ['Todas as comunidades', 'Matriz'], opcoesComunidade)
    consultar(pc)
    checar('e a turma dele aparece normalmente',
           'Eucaristia I - Matriz' in pc.inner_text('#cham-turmas-lista'))

    # Titulo no singular ou plural conforme quem esta vendo -- nao e so
    # cosmetico: um coordenador com oito turmas na lista e "Minha turma" no
    # topo leria a tela como quebrada. So o texto muda; quem ve o que
    # continua sendo o backend (EscopoAcessoService), como em todo o resto
    # desta tela.
    print('--- o titulo da lista respeita quem esta vendo')
    checar('catequista (uma turma, o normal) ve o titulo no singular',
           pc.inner_text('#cham-titulo-turmas').strip() == 'Minha turma',
           pc.inner_text('#cham-titulo-turmas'))
    checar('coordenador paroquial (varias turmas, de proposito) ve o titulo no plural',
           page.inner_text('#cham-titulo-turmas').strip() == 'Minhas turmas',
           page.inner_text('#cham-titulo-turmas'))
    pcoord = abrir(TURMAS_COORDENADOR, tipo='COORDENADOR')
    checar('coordenador de comunidade tambem ve o titulo no plural',
           pcoord.inner_text('#cham-titulo-turmas').strip() == 'Minhas turmas',
           pcoord.inner_text('#cham-titulo-turmas'))

    print('--- 400px: nada estoura o pai')
    p400 = abrir(TURMAS_COORDENADOR, 400)
    consultar(p400)
    estouros = p400.evaluate("""() => {
      const raiz = document.getElementById('tab-chamada');
      const limite = raiz.getBoundingClientRect().right + 1;
      return Array.from(raiz.querySelectorAll('*'))
        .filter(e => e.getBoundingClientRect().right > limite)
        .map(e => e.tagName + '.' + e.className).slice(0, 5);
    }""")
    checar('nenhum elemento passa da borda em 400px', estouros == [], estouros)
    checar('a pagina nao rola na horizontal',
           p400.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1'))

    print('--- botao alinhado ao campo')
    pa = abrir(TURMAS_COORDENADOR)
    desalinho = pa.evaluate("""() => {
      const linha = document.querySelector('#cham-tela-turmas .row.ind-nao-imprime');
      const campo = linha.querySelector('select');
      const botao = linha.querySelector('button');
      return Math.abs(campo.getBoundingClientRect().bottom
                      - botao.getBoundingClientRect().bottom);
    }""")
    checar('o "Consultar" divide a base com o filtro', desalinho <= 1, desalinho)

    # Sem a secao de Eventos ao lado, a aba ficou com conteudo mais estreito, e
    # o container (que se ajusta ao conteudo da aba ativa) encolheu junto --
    # o suficiente para o "Consultar" comecar a quebrar sozinho por volta de
    # 760px com a base herdada (190px). Ver #cham-tela-turmas .ind-filtro.
    print('--- 760px: o "Consultar" nao quebra sozinho')
    p760 = abrir(TURMAS_COORDENADOR, 760)
    consultar(p760)
    desalinho760 = p760.evaluate("""() => {
      const linha = document.querySelector('#cham-tela-turmas .row.ind-nao-imprime');
      const campo = linha.querySelector('select');
      const botao = linha.querySelector('button');
      return Math.abs(campo.getBoundingClientRect().bottom
                      - botao.getBoundingClientRect().bottom);
    }""")
    checar('o "Consultar" ainda divide a base do filtro em 760px', desalinho760 <= 1, desalinho760)

    navegador.close()

print()
if falhas:
    print(f"{len(falhas)} FALHA(S): " + '; '.join(falhas))
    sys.exit(1)
print('TODOS OS INVARIANTES OK')
