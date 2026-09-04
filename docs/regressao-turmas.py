"""Turmas e inscricoes: listagem so de leitura, fase condicional e as duas abas.

O que estes testes protegem, em ordem de importancia:

  1. A LISTAGEM NAO EDITA. Ela responde "onde esta cada turma?" e mais nada.
     Um <select> ou <button> dentro dela devolve a tela ao estado em que ver e
     alterar eram o mesmo gesto -- e mexer na turma errada era o preco de olhar.
  2. NADA APARECE ANTES DO "CONSULTAR". A tela abria despejando o ano inteiro,
     e quem entrou atras de uma comunidade tinha de descartar o resto no olho.
  3. FASE SO EM EUCARISTIA E CRISMA. Oferecer "segunda fase" numa turma de
     adultos e convidar a responder uma pergunta que nao existe -- e o valor
     escolhido ficava gravado. Duracao de dois anos NAO e o criterio: adultos
     tambem dura dois.
  4. UM BOTAO POR LINHA. "Salvar" e "Transferir" lado a lado disputavam o mesmo
     clique com pesos muito diferentes: um corrige a situacao de quem ficou, o
     outro tira a pessoa da turma.
  5. Inscrever nao mora aqui: inscricao exige dados que so o cadastro pergunta.
"""
from playwright.sync_api import sync_playwright
import json, pathlib, sys

url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()

COMUNIDADES = [
    {"idComunidade": 1, "nome": "Matriz", "ativo": True},
    {"idComunidade": 2, "nome": "São José", "ativo": True},
]

TURMAS = [
    {"idTurma": 10, "nome": "Eucaristia I - Matriz", "ano": 2026, "categoria": "EUCARISTIA",
     "janela": "ANO", "exigeFrequencia": True, "etapa": 1, "nomeCatequista": "Ana",
     "matriculadosNoAno": 14, "idComunidade": 1, "nomeComunidade": "Matriz",
     "pendenteDeClassificacao": False, "descricao": None, "nivel": None},
    {"idTurma": 11, "nome": "Eucaristia I - São José", "ano": 2026, "categoria": "EUCARISTIA",
     "janela": "ANO", "exigeFrequencia": True, "etapa": 1, "nomeCatequista": "Bruno",
     "matriculadosNoAno": 12, "idComunidade": 2, "nomeComunidade": "São José",
     "pendenteDeClassificacao": False, "descricao": None, "nivel": None},
    # Sem fase: e o caso que a tela errava, oferecendo "2a fase" para adultos.
    {"idTurma": 20, "nome": "Adultos - Matriz", "ano": 2026, "categoria": "ADULTOS",
     "janela": "ANO", "exigeFrequencia": True, "etapa": None, "nomeCatequista": "Célia",
     "matriculadosNoAno": 9, "idComunidade": 1, "nomeComunidade": "Matriz",
     "pendenteDeClassificacao": False, "descricao": None, "nivel": None},
    {"idTurma": 30, "nome": "Turma nova", "ano": 2026, "categoria": None,
     "janela": "NENHUMA", "exigeFrequencia": False, "etapa": None, "nomeCatequista": None,
     "matriculadosNoAno": 0, "idComunidade": None, "nomeComunidade": None,
     "pendenteDeClassificacao": True, "descricao": None, "nivel": None},
]

MATRICULAS = [
    {"idMatricula": 100, "idCatequisando": 1, "nomeCatequisando": "Ana Clara",
     "idTurma": 10, "nomeTurma": "Eucaristia I - Matriz", "ano": 2026,
     "dataMatricula": "2026-02-10", "situacao": "CURSANDO", "observacao": None,
     "atualizadoPor": None, "paroquiaDestino": None},
    {"idMatricula": 101, "idCatequisando": 2, "nomeCatequisando": "João Pedro",
     "idTurma": 10, "nomeTurma": "Eucaristia I - Matriz", "ano": 2026,
     "dataMatricula": "2026-02-10", "situacao": "TRANSFERIDO", "observacao": None,
     "atualizadoPor": "gabriel", "paroquiaDestino": "São Pedro"},
]

STUB = """
window.__put = [];
window.fetch = async (u, o) => {
  const s = String(typeof u === 'string' ? u : (u && u.url) || '');
  const j = (x, st) => new Response(JSON.stringify(x),
      {status: st || 200, headers: {'Content-Type': 'application/json'}});
  if (s.includes('/api/comunidades')) return j(%(comunidades)s);
  if (s.includes('/classificacao')) {
    const corpo = JSON.parse(o.body);
    window.__put.push(corpo);
    const base = %(turmas)s.find(t => s.includes('/turmas/' + t.idTurma + '/'));
    return j(Object.assign({}, base, corpo,
      {pendenteDeClassificacao: corpo.categoria === null}));
  }
  if (s.includes('/matriculas')) return j(%(matriculas)s);
  if (s.includes('/api/admin/turmas')) return j(%(turmas)s);
  return j({});
};
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario', JSON.stringify(
  {nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
""" % {"comunidades": json.dumps(COMUNIDADES), "turmas": json.dumps(TURMAS),
       "matriculas": json.dumps(MATRICULAS)}

falhas = []


def checar(nome, condicao, detalhe=''):
    if not condicao:
        falhas.append(nome)
    print(f"  {'OK' if condicao else '!!'}  {nome}"
          f"{(' — ' + str(detalhe)) if detalhe and not condicao else ''}")


with sync_playwright() as p:
    navegador = p.chromium.launch(executable_path='/opt/pw-browsers/chromium')

    def abrir(largura=1280):
        page = navegador.new_page(viewport={'width': largura, 'height': 900},
                                  reduced_motion='reduce')
        page.add_init_script(STUB)
        page.goto(url)
        page.wait_for_timeout(400)
        page.click('button.tab-btn[data-tab="admin"]')
        page.wait_for_timeout(700)
        return page

    def consultar(page):
        page.click('#adm-consultar')
        page.wait_for_timeout(500)

    print('--- a lista so aparece quando se pede')
    page = abrir()
    checar('nenhuma turma antes do "Consultar"',
           page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma]', 'e => e.length') == 0)
    checar('a tela diz o que fazer',
           'Consultar' in page.inner_text('#adm-turmas-lista'))
    checar('o filtro de turma ja vem preenchido',
           page.eval_on_selector_all('#adm-filtro-turma option', 'e => e.length') == len(TURMAS) + 1)

    # Mudar o filtro nao pode consultar sozinho: quem monta o recorte mexe nos
    # dois campos, e responder no meio disso mostra resultado que ninguem pediu.
    page.select_option('#adm-filtro-comunidade', '1')
    page.wait_for_timeout(300)
    checar('mudar o filtro NAO consulta',
           page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma]', 'e => e.length') == 0)

    consultar(page)
    linhas = page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma]', 'e => e.length')
    checar('o filtro de comunidade recorta a lista', linhas == 2, linhas)

    print('--- a listagem nao edita')
    page.select_option('#adm-filtro-comunidade', '')
    consultar(page)
    checar('nenhum campo editavel na lista',
           page.eval_on_selector_all(
               '#adm-turmas-lista select, #adm-turmas-lista button, #adm-turmas-lista input',
               'e => e.length') == 0)
    cabecalhos = page.eval_on_selector_all('#adm-turmas-lista th', 'e => e.map(x => x.textContent.trim())')
    checar('as colunas sao Turma, Fase e Comunidade',
           cabecalhos == ['Turma', 'Fase', 'Comunidade'], cabecalhos)
    texto = page.inner_text('#adm-turmas-lista')
    checar('"Categoria" e "Ano do percurso" sumiram do cabecalho',
           'Categoria' not in cabecalhos and 'Ano do percurso' not in cabecalhos)
    checar('Eucaristia mostra "Primeira fase"', 'Primeira fase' in texto)
    checar('turma sem fase mostra tracinho',
           page.eval_on_selector(
               'tr[data-abrir-turma="20"] td:nth-child(2)', 'e => e.textContent.trim()') == '—')
    checar('turma sem categoria tambem mostra tracinho na fase',
           page.eval_on_selector(
               'tr[data-abrir-turma="30"] td:nth-child(2)', 'e => e.textContent.trim()') == '—')
    checar('comunidade vazia mostra tracinho',
           page.eval_on_selector(
               'tr[data-abrir-turma="30"] td:nth-child(3)', 'e => e.textContent.trim()') == '—')

    print('--- a linha leva para a edicao')
    page.click('tr[data-abrir-turma="10"]')
    page.wait_for_timeout(500)
    checar('a tela de edicao abriu',
           page.eval_on_selector('#adm-tela-matriculas', 'e => !e.hidden'))
    checar('a listagem sumiu',
           page.eval_on_selector('#adm-tela-turmas', 'e => e.hidden'))
    checar('o titulo nomeia a turma',
           'Eucaristia I - Matriz' in page.inner_text('#adm-matriculas-titulo'))
    checar('o campo se chama "Turma", nao "Categoria"',
           'Turma' in page.inner_text('#adm-classificacao')
           and 'Categoria' not in page.inner_text('#adm-classificacao'))

    print('--- fase so onde existe fase')
    checar('Eucaristia mostra o campo de fase',
           page.eval_on_selector('#adm-edit-fase-campo', 'e => !e.hidden'))
    opcoes = page.eval_on_selector_all('#adm-edit-etapa option', 'e => e.map(x => x.textContent)')
    checar('as opcoes falam em fase, nao em ano',
           'Primeira fase' in opcoes and 'Segunda fase' in opcoes
           and not any('ano' in o for o in opcoes), opcoes)

    page.select_option('#adm-edit-categoria', 'ADULTOS')
    page.wait_for_timeout(400)
    checar('Adultos esconde o campo de fase',
           page.eval_on_selector('#adm-edit-fase-campo', 'e => e.hidden'))
    enviado = page.evaluate('window.__put[window.__put.length - 1]')
    checar('e a fase gravada vai NULA', enviado['etapa'] is None, enviado)

    page.select_option('#adm-edit-categoria', 'CRISMA')
    page.wait_for_timeout(400)
    checar('Crisma mostra o campo de fase de novo',
           page.eval_on_selector('#adm-edit-fase-campo', 'e => !e.hidden'))

    page.select_option('#adm-edit-categoria', 'PRE_CATEQUESE')
    page.wait_for_timeout(300)
    checar('Pre-catequese nao tem fase',
           page.eval_on_selector('#adm-edit-fase-campo', 'e => e.hidden'))
    page.select_option('#adm-edit-categoria', 'CATECUMENATO')
    page.wait_for_timeout(300)
    checar('Catecumenato nao tem fase',
           page.eval_on_selector('#adm-edit-fase-campo', 'e => e.hidden'))
    page.select_option('#adm-edit-categoria', 'PERSEVERANCA')
    page.wait_for_timeout(300)
    checar('Perseveranca nao tem fase',
           page.eval_on_selector('#adm-edit-fase-campo', 'e => e.hidden'))

    print('--- inscrever saiu daqui')
    checar('nao ha botao "Inscrever"',
           page.eval_on_selector_all(
               '#adm-tela-matriculas button',
               'e => e.filter(b => b.textContent.trim() === "Inscrever").length') == 0)
    checar('nao ha campo de escolher catequisando',
           page.eval_on_selector_all('#adm-novo-catequisando', 'e => e.length') == 0)

    print('--- um botao por linha, e transferir e aba a parte')
    salvar = page.eval_on_selector_all('#adm-matriculas-lista [data-salvar-situacao]', 'e => e.length')
    checar('cada inscricao tem "Salvar"', salvar == 1, salvar)
    checar('e NENHUMA tem "Transferir"',
           page.eval_on_selector_all('#adm-matriculas-lista [data-transferir]', 'e => e.length') == 0)
    checar('quem foi transferido diz para onde',
           'São Pedro' in page.inner_text('#adm-matriculas-lista'))
    checar('a aba de transferencias comeca escondida',
           page.eval_on_selector('#adm-vista-transferencias', 'e => e.hidden'))

    page.click('.adm-subnav-btn[data-adm-vista="transferencias"]')
    page.wait_for_timeout(300)
    checar('a aba de transferencias abre',
           page.eval_on_selector('#adm-vista-transferencias', 'e => !e.hidden'))
    checar('e a de inscricoes fecha',
           page.eval_on_selector('#adm-vista-inscricoes', 'e => e.hidden'))
    checar('cada linha tem seu botao "Transferir"',
           page.eval_on_selector_all('#adm-transferencias-lista [data-transferir]', 'e => e.length') == 1)
    checar('quem ja saiu nao aparece para transferir',
           'João Pedro' not in page.inner_text('#adm-transferencias-lista'))
    checar('"outra paróquia" continua sendo destino',
           'outra paróquia' in page.inner_text('#adm-transferencias-lista'))

    print('--- voltar mostra a classificacao nova')
    page.click('#adm-voltar-turmas')
    page.wait_for_timeout(400)
    checar('a listagem voltou',
           page.eval_on_selector('#adm-tela-turmas', 'e => !e.hidden'))
    checar('a turma editada aparece com a categoria nova',
           'Perseverança' in page.eval_on_selector(
               'tr[data-abrir-turma="10"]', 'e => e.textContent'))

    print('--- 400px: nada estoura o pai')
    p400 = abrir(400)
    consultar(p400)
    p400.click('tr[data-abrir-turma="10"]')
    p400.wait_for_timeout(500)
    estouros = p400.evaluate("""() => {
      const raiz = document.getElementById('tab-admin');
      const limite = raiz.getBoundingClientRect().right + 1;
      return Array.from(raiz.querySelectorAll('*'))
        .filter(e => e.getBoundingClientRect().right > limite)
        .map(e => e.tagName + '.' + e.className).slice(0, 5);
    }""")
    checar('nenhum elemento passa da borda em 400px', estouros == [], estouros)
    checar('a pagina nao rola na horizontal',
           p400.evaluate('document.documentElement.scrollWidth <= window.innerWidth + 1'))

    navegador.close()

print()
if falhas:
    print(f"{len(falhas)} FALHA(S): " + '; '.join(falhas))
    sys.exit(1)
print('TODOS OS INVARIANTES OK')
