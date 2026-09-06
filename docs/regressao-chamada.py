"""Chamada: filtro de comunidade/turma, e o recorte que cada papel enxerga.

O que estes testes protegem, em ordem de importancia:

  1. NADA APARECE ANTES DO "CONSULTAR". Turmas e eventos vinham todos de uma
     vez, e quem enxerga mais de uma comunidade (coordenador, coordenador
     paroquial) tinha que descartar o resto no olho ate achar a turma que
     queria -- mesmo problema que a tela de Turmas e Inscricoes ja resolveu.
  2. O FILTRO NUNCA OFERECE MAIS DO QUE O BACKEND JA DEVOLVEU. As opcoes de
     comunidade vem das PROPRIAS turmas do usuario (`/api/chamada/minhas-
     turmas`), nao de `/api/comunidades` (a paroquia inteira). E assim que um
     catequista, que so recebe as turmas dele, so ve a comunidade dele no
     combo -- sem nenhuma regra de permissao nova NESTA tela: quem barra de
     verdade continua sendo o EscopoAcessoService no servidor.
  3. O FILTRO TAMBEM VALE PARA OS EVENTOS. Evento nao tem comunidade, quem
     tem e a turma dentro dele -- filtrar por comunidade precisa esconder a
     LINHA da turma de fora do recorte, e o cartao inteiro quando nenhuma
     turma do evento sobra.

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

# Retiro com turmas de DUAS comunidades: filtrar por uma tem que sumir com a
# linha da outra, sem derrubar o cartao inteiro. Missa so tem turma de São
# José: filtrar por Matriz precisa sumir com o cartao inteiro.
EVENTOS = [
    {"idEvento": 1, "titulo": "Retiro de Páscoa", "local": "Salão paroquial",
     "publicoAlvo": None, "dataInicio": "2026-04-10", "dataFim": "2026-04-10",
     "turmas": [
         {"idTurma": 10, "nomeTurma": "Eucaristia I - Matriz", "matriculados": 14,
          "idEncontro": None, "situacao": None, "presentes": 0, "editavel": False},
         {"idTurma": 11, "nomeTurma": "Eucaristia I - São José", "matriculados": 12,
          "idEncontro": None, "situacao": None, "presentes": 0, "editavel": False},
     ]},
    {"idEvento": 2, "titulo": "Missa de Encerramento - São José", "local": "Matriz de São José",
     "publicoAlvo": None, "dataInicio": "2026-12-05", "dataFim": "2026-12-05",
     "turmas": [
         {"idTurma": 11, "nomeTurma": "Eucaristia I - São José", "matriculados": 12,
          "idEncontro": None, "situacao": None, "presentes": 0, "editavel": False},
     ]},
]


def stub(turmas):
    return """
    window.fetch = async (u) => {
      const s = String(typeof u === 'string' ? u : (u && u.url) || '');
      const j = (x) => new Response(JSON.stringify(x),
          {status: 200, headers: {'Content-Type': 'application/json'}});
      if (s.includes('/api/chamada/minhas-turmas')) return j(%(turmas)s);
      if (s.includes('/api/chamada/eventos')) return j(%(eventos)s);
      return j({});
    };
    localStorage.setItem('catequese.token', 't');
    localStorage.setItem('catequese.usuario', JSON.stringify(
      {nome: 'G', username: 'g', tipo: 'COORDENADOR_PAROQUIAL', admin: true, podeEditar: true}));
    """ % {"turmas": json.dumps(turmas), "eventos": json.dumps(EVENTOS)}


falhas = []


def checar(nome, condicao, detalhe=''):
    if not condicao:
        falhas.append(nome)
    print(f"  {'OK' if condicao else '!!'}  {nome}"
          f"{(' — ' + str(detalhe)) if detalhe and not condicao else ''}")


with sync_playwright() as p:
    navegador = p.chromium.launch(executable_path='/opt/pw-browsers/chromium')

    def abrir(turmas, largura=1280):
        page = navegador.new_page(viewport={'width': largura, 'height': 900},
                                  reduced_motion='reduce')
        page.add_init_script(stub(turmas))
        page.goto(url)
        page.wait_for_timeout(400)
        page.click('button.tab-btn[data-tab="chamada"]')
        page.wait_for_timeout(700)
        return page

    def consultar(page):
        page.click('#cham-consultar')
        page.wait_for_timeout(500)

    print('--- a lista so aparece quando se pede')
    page = abrir(TURMAS_COORDENADOR)
    checar('nenhuma turma antes do "Consultar"',
           page.eval_on_selector_all('#cham-turmas-lista [data-id-turma]', 'e => e.length') == 0)
    checar('a tela diz o que fazer',
           'Consultar' in page.inner_text('#cham-turmas-lista'))
    checar('eventos tambem esperam o "Consultar"',
           'Consultar' in page.inner_text('#cham-eventos-lista'))
    checar('o filtro de turma ja vem preenchido',
           page.eval_on_selector_all('#cham-filtro-turma option', 'e => e.length') == 4)

    page.select_option('#cham-filtro-comunidade', '1')
    page.wait_for_timeout(300)
    checar('mudar o filtro NAO consulta',
           page.eval_on_selector_all('#cham-turmas-lista [data-id-turma]', 'e => e.length') == 0)

    consultar(page)
    cartoes = page.eval_on_selector_all('#cham-turmas-lista [data-id-turma]', 'e => e.length')
    checar('o filtro de comunidade recorta a lista de turmas', cartoes == 2, cartoes)

    print('--- o filtro tambem recorta os eventos')
    textoEventos = page.inner_text('#cham-eventos-lista')
    checar('o retiro aparece (tem turma de Matriz)', 'Retiro de Páscoa' in textoEventos)
    checar('mas so com a turma de Matriz', 'Eucaristia I - São José' not in textoEventos)
    checar('a missa de São José some inteira (nenhuma turma no recorte)',
           'Missa de Encerramento' not in textoEventos)

    page.select_option('#cham-filtro-comunidade', '2')
    page.wait_for_timeout(300)
    consultar(page)
    textoEventos = page.inner_text('#cham-eventos-lista')
    checar('trocando para São José, a missa volta a aparecer',
           'Missa de Encerramento' in textoEventos)
    checar('e o retiro mostra so a turma de São José',
           'Eucaristia I - Matriz' not in textoEventos and 'Eucaristia I - São José' in textoEventos)

    print('--- o combo de comunidade nunca inventa uma comunidade que o usuario nao tem')
    pc = abrir(TURMAS_CATEQUISTA)
    opcoesComunidade = pc.eval_on_selector_all(
        '#cham-filtro-comunidade option', 'e => e.map(o => o.textContent.trim())')
    checar('catequista com uma turma so ve "Todas" e a comunidade dele',
           opcoesComunidade == ['Todas as comunidades', 'Matriz'], opcoesComunidade)
    consultar(pc)
    checar('e a turma dele aparece normalmente',
           'Eucaristia I - Matriz' in pc.inner_text('#cham-turmas-lista'))

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

    navegador.close()

print()
if falhas:
    print(f"{len(falhas)} FALHA(S): " + '; '.join(falhas))
    sys.exit(1)
print('TODOS OS INVARIANTES OK')
