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
    #
    # `etapa: 1` aqui e de proposito, e nao um esquecimento: e o resto da tela
    # antiga, que oferecia "1o ano / 2o ano" para toda categoria. Esta turma
    # simula um cadastro feito naquela epoca -- Adultos nao tem fase, mas o
    # campo guardou um valor mesmo assim.
    {"idTurma": 20, "nome": "Adultos - Matriz", "ano": 2026, "categoria": "ADULTOS",
     "janela": "ANO", "exigeFrequencia": True, "etapa": 1, "nomeCatequista": "Célia",
     "matriculadosNoAno": 9, "idComunidade": 1, "nomeComunidade": "Matriz",
     "pendenteDeClassificacao": False, "descricao": None, "nivel": None},
    # A outra turma de Adultos, com a fase espuria DIFERENTE (2, nao 1) --
    # o pior caso: se a comparacao de fase nao for ignorada aqui, as duas
    # turmas de Adultos parecem percursos diferentes, e a transferencia entre
    # elas -- o caso normal, mesma categoria, outra comunidade -- some da
    # lista sem nenhum aviso. Foi exatamente isto que aconteceu na producao.
    {"idTurma": 21, "nome": "Adultos - São José", "ano": 2026, "categoria": "ADULTOS",
     "janela": "ANO", "exigeFrequencia": True, "etapa": 2, "nomeCatequista": "Diego",
     "matriculadosNoAno": 7, "idComunidade": 2, "nomeComunidade": "São José",
     "pendenteDeClassificacao": False, "descricao": None, "nivel": None},
    {"idTurma": 30, "nome": "Turma nova", "ano": 2026, "categoria": None,
     "janela": "NENHUMA", "exigeFrequencia": False, "etapa": None, "nomeCatequista": None,
     "matriculadosNoAno": 0, "idComunidade": None, "nomeComunidade": None,
     "pendenteDeClassificacao": True, "descricao": None, "nivel": None},
    # O ano anterior: era ele que vazava para a consulta de 2026, com "0
    # inscritos" ao lado de um titulo que dizia 2026.
    {"idTurma": 40, "nome": "Eucaristia - Matriz 2025", "ano": 2025, "categoria": "EUCARISTIA",
     "janela": "ANO", "exigeFrequencia": True, "etapa": 1, "nomeCatequista": "Ana",
     "matriculadosNoAno": 11, "idComunidade": 1, "nomeComunidade": "Matriz",
     "pendenteDeClassificacao": False, "descricao": None, "nivel": None},
    # Cadastro antigo sem ano: aparece em qualquer ano, de proposito -- esta e a
    # unica tela onde ele pode ser classificado.
    {"idTurma": 50, "nome": "Turma antiga", "ano": None, "categoria": None,
     "janela": "NENHUMA", "exigeFrequencia": False, "etapa": None, "nomeCatequista": None,
     "matriculadosNoAno": 0, "idComunidade": None, "nomeComunidade": None,
     "pendenteDeClassificacao": True, "descricao": None, "nivel": None},
]


# O servidor filtra por ano; o stub faz o mesmo, senao o teste passaria com o
# defeito de volta.
def do_ano(ano):
    return [t for t in TURMAS if t["ano"] in (None, ano)]

MATRICULAS = [
    {"idMatricula": 100, "idCatequisando": 1, "nomeCatequisando": "Ana Clara",
     "idTurma": 10, "nomeTurma": "Eucaristia I - Matriz", "ano": 2026,
     "dataMatricula": "2026-02-10", "situacao": "CURSANDO", "observacao": None,
     "atualizadoPor": None, "paroquiaDestino": None},
    {"idMatricula": 101, "idCatequisando": 2, "nomeCatequisando": "João Pedro",
     "idTurma": 10, "nomeTurma": "Eucaristia I - Matriz", "ano": 2026,
     "dataMatricula": "2026-02-10", "situacao": "TRANSFERIDO", "observacao": None,
     "atualizadoPor": "gabriel", "paroquiaDestino": "São Pedro"},
    {"idMatricula": 102, "idCatequisando": 3, "nomeCatequisando": "Marcos Vinícius",
     "idTurma": 20, "nomeTurma": "Adultos - Matriz", "ano": 2026,
     "dataMatricula": "2026-02-10", "situacao": "CURSANDO", "observacao": None,
     "atualizadoPor": None, "paroquiaDestino": None},
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
  if (s.includes('/matriculas')) {
    const m = s.match(/turmas\/(\d+)\/matriculas/);
    const idTurma = m ? Number(m[1]) : null;
    return j(%(matriculas)s.filter(x => x.idTurma === idTurma));
  }
  if (s.includes('/api/admin/turmas')) {
    const ano = Number((s.match(/ano=(\\d+)/) || [])[1] || 0);
    return j(ano === 2025 ? %(turmas2025)s : %(turmas)s);
  }
  return j({});
};
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario', JSON.stringify(
  {nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
""" % {"comunidades": json.dumps(COMUNIDADES),
       "turmas": json.dumps(do_ano(2026)), "turmas2025": json.dumps(do_ano(2025)),
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
           page.eval_on_selector_all('#adm-filtro-turma option', 'e => e.length') == 7)

    # Mudar o filtro nao pode consultar sozinho: quem monta o recorte mexe nos
    # dois campos, e responder no meio disso mostra resultado que ninguem pediu.
    page.select_option('#adm-filtro-comunidade', '1')
    page.wait_for_timeout(300)
    checar('mudar o filtro NAO consulta',
           page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma]', 'e => e.length') == 0)

    consultar(page)
    linhas = page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma]', 'e => e.length')
    checar('o filtro de comunidade recorta a lista', linhas == 2, linhas)

    print('--- o ano filtra a lista, e nao so a contagem')
    page.select_option('#adm-filtro-comunidade', '')
    consultar(page)
    nomes = page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma] td:first-child',
                                      'e => e.map(x => x.textContent)')
    checar('turma de 2025 NAO aparece na consulta de 2026',
           not any('2025' in n for n in nomes), nomes)
    checar('turma sem ano aparece', any('Turma antiga' in n for n in nomes), nomes)
    checar('e o aviso explica por que ela esta ai',
           'sem ano definido' in page.inner_text('#adm-status'))

    page.fill('#adm-ano', '2025')
    page.dispatch_event('#adm-ano', 'change')
    page.wait_for_timeout(500)
    checar('trocar de ano limpa a lista e espera o "Consultar"',
           page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma]', 'e => e.length') == 0)
    consultar(page)
    nomes = page.eval_on_selector_all('#adm-turmas-lista tr[data-abrir-turma] td:first-child',
                                      'e => e.map(x => x.textContent)')
    checar('em 2025 vem a turma de 2025', any('2025' in n for n in nomes), nomes)
    checar('e nenhuma de 2026', not any('2026' in n for n in nomes), nomes)
    checar('a contagem e a do ano pedido',
           page.eval_on_selector(
               'tr[data-abrir-turma="40"] td:nth-child(4)', 'e => e.textContent.trim()') == '11')

    page.fill('#adm-ano', '2026')
    page.dispatch_event('#adm-ano', 'change')
    page.wait_for_timeout(500)

    print('--- a listagem nao edita')
    consultar(page)
    checar('nenhum campo editavel na lista',
           page.eval_on_selector_all(
               '#adm-turmas-lista select, #adm-turmas-lista button, #adm-turmas-lista input',
               'e => e.length') == 0)
    cabecalhos = page.eval_on_selector_all('#adm-turmas-lista th', 'e => e.map(x => x.textContent.trim())')
    checar('as colunas sao Turma, Fase, Comunidade e Inscritos',
           cabecalhos == ['Turma', 'Fase', 'Comunidade', 'Inscritos'], cabecalhos)
    checar('a contagem de inscritos aparece na linha',
           page.eval_on_selector(
               'tr[data-abrir-turma="10"] td:nth-child(4)', 'e => e.textContent.trim()') == '14')
    checar('a contagem fica alinhada a direita',
           page.eval_on_selector(
               'tr[data-abrir-turma="10"] td:nth-child(4)',
               'e => getComputedStyle(e).textAlign') == 'right')
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
    # O select de CATEGORIA se chama "Categoria", nao "Turma" -- "Turma" e o
    # rotulo do select de NAVEGACAO, bem acima. As duas coisas com o mesmo
    # nome, uma do lado da outra, foi a confusao real que motivou a separacao:
    # pareciam a mesma pergunta, e uma delas gravava dado.
    checar('o campo de classificacao se chama "Categoria"',
           'Categoria' in page.inner_text('#adm-classificacao'))

    print('--- navegar entre turmas nao grava nada')
    checar('o select de navegacao chama "Comunidade" e "Ir para a turma"',
           'Comunidade' in page.inner_text('#adm-tela-matriculas')
           and 'Ir para a turma' in page.inner_text('#adm-tela-matriculas'))
    checar('o select de turma ja abre na turma atual, para mostrar onde se esta',
           page.eval_on_selector('#adm-nav-turma', 'e => e.value') == '10')
    checar('o select de comunidade ja abre na comunidade da turma atual',
           page.eval_on_selector('#adm-nav-comunidade', 'e => e.value') == '1')

    # Trocar a comunidade do FILTRO de navegacao e so filtro: nao toca na
    # comunidade da turma aberta.
    page.select_option('#adm-nav-comunidade', '2')
    page.wait_for_timeout(300)
    checar('mudar o filtro de navegacao NAO reclassifica a turma',
           not page.evaluate('window.__put.some(c => "idComunidade" in c && c.idComunidade === 2)'))
    opcoesNav = page.eval_on_selector_all('#adm-nav-turma option', 'e => e.map(o => o.textContent.trim())')
    checar('e encolhe o select de turma para a comunidade escolhida',
           'Eucaristia I - São José' in opcoesNav and 'Eucaristia I - Matriz' not in opcoesNav,
           opcoesNav)

    # Escolher uma turma ali ABRE ela -- e so isso.
    page.select_option('#adm-nav-turma', '11')
    page.wait_for_timeout(500)
    checar('escolher uma turma no select de navegacao abre ela',
           'Eucaristia I - São José' in page.inner_text('#adm-matriculas-titulo'))
    checar('e o filtro de navegacao reseta para a comunidade da turma nova',
           page.eval_on_selector('#adm-nav-comunidade', 'e => e.value') == '2')

    # Volta para a turma 10 para o resto dos testes seguir como estava.
    page.select_option('#adm-nav-comunidade', '')
    page.wait_for_timeout(200)
    page.select_option('#adm-nav-turma', '10')
    page.wait_for_timeout(500)

    print('--- comunidade nao muda mais por aqui')
    checar('a comunidade aparece como informacao, nao como select',
           page.eval_on_selector_all('#adm-edit-comunidade', 'e => e.length') == 0)
    checar('e mostra o nome certo', page.inner_text('#adm-edit-comunidade-atual').strip() == 'Matriz')

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
    # A comunidade nao tem select nesta tela, mas o PUT exige o campo mesmo
    # assim -- se ele fosse vazio, TROCAR SO A CATEGORIA apagaria a comunidade
    # da turma sem a pessoa nunca ter tocado nisso.
    checar('e a comunidade da turma NAO se perde ao trocar so a categoria',
           enviado['idComunidade'] == 1, enviado)

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

    print('--- fase espuria nao esconde a transferencia entre comunidades')
    # Adultos-Matriz (etapa=1) e Adultos-São José (etapa=2): as duas com uma
    # fase que a categoria nao tem, e DIFERENTE uma da outra -- o resto exato
    # de quando a tela oferecia "1o ano / 2o ano" para toda turma. Se a
    # comparacao de fase nao for ignorada fora de Eucaristia/Crisma, elas
    # parecem percursos diferentes e a transferencia normal desaparece.
    pt = abrir()
    consultar(pt)
    pt.click('tr[data-abrir-turma="20"]')
    pt.wait_for_timeout(500)
    pt.click('.adm-subnav-btn[data-adm-vista="transferencias"]')
    pt.wait_for_timeout(300)
    destinos = pt.eval_on_selector_all(
        '#adm-transferencias-lista [data-destino] option', 'e => e.map(o => o.textContent.trim())')
    checar('a outra turma de Adultos aparece como destino, apesar da fase espuria',
           'Adultos - São José' in destinos, destinos)

    # Botao flutuando meia duzia de pixels acima do campo que ele aciona era o
    # que fazia a barra de filtro parecer torta. Mede a base, nao o olho.
    print('--- botao alinhado ao campo')
    pa = abrir(1280)
    desalinho = pa.evaluate("""() => {
      const linha = document.querySelector('#adm-tela-turmas .row.ind-nao-imprime');
      const campo = linha.querySelector('select');
      const botao = linha.querySelector('button');
      return Math.abs(campo.getBoundingClientRect().bottom
                      - botao.getBoundingClientRect().bottom);
    }""")
    checar('o "Consultar" divide a base com o filtro', desalinho <= 1, desalinho)

    ano = pa.evaluate("""() => {
      const linha = document.querySelector('#adm-tela-turmas .row.ind-nao-imprime');
      const campo = linha.querySelector('input#adm-ano');
      const botao = linha.querySelector('button');
      return Math.abs(campo.getBoundingClientRect().bottom
                      - botao.getBoundingClientRect().bottom);
    }""")
    checar('o campo Ano divide a base com o "Consultar"', ano <= 1, ano)

    # O ano e filtro, entao mora na barra de filtro -- na linha do titulo ele
    # parecia pertencer ao "Encerrar ano", que e outra coisa inteiramente.
    checar('o Ano esta na barra de filtro, nao na linha do titulo',
           pa.eval_on_selector_all(
               '#adm-tela-turmas .row.ind-nao-imprime #adm-ano', 'e => e.length') == 1)

    navegador.close()

print()
if falhas:
    print(f"{len(falhas)} FALHA(S): " + '; '.join(falhas))
    sys.exit(1)
print('TODOS OS INVARIANTES OK')
