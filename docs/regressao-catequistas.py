"""Consultar Catequistas: quem enxerga a aba, e o curriculo mostra o que a API manda.

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
  3. O CURRICULO DESCREVE FALTA E JUSTIFICATIVA, nao so presenca -- foi pedido
     explicito do Gabriel ("deve ficar descrito que nao participou, ou por
     falta, ou se deu justificativa"), por isso cada encontro vira um selo
     proprio (Presente/Faltou/Justificada), nunca so uma data.
  4. SEM INSCRICAO NO ANO tem aviso proprio, igual ao que tela-formacao.md ja
     usa -- nao inventar um segundo jeito de dizer a mesma coisa.
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


def stub(tipo):
    curriculos = {2: CURRICULO_PREENCHIDO, 4: CURRICULO_SEM_INSCRICAO}
    return """
    window.fetch = async (u) => {
      const s = String(typeof u === 'string' ? u : (u && u.url) || '');
      const j = (x) => new Response(JSON.stringify(x),
          {status: 200, headers: {'Content-Type': 'application/json'}});
      const curriculos = %(curriculos)s;
      if (s.includes('/api/catequistas/curriculo')) return j(%(lista)s);
      const m = s.match(/\\/api\\/catequistas\\/(\\d+)\\/curriculo/);
      if (m) return j(curriculos[m[1]] || curriculos[Number(m[1])]);
      return j({});
    };
    localStorage.setItem('catequese.token', 't');
    localStorage.setItem('catequese.usuario', JSON.stringify(
      {nome: 'G', username: 'g', tipo: '%(tipo)s', admin: %(admin)s, podeEditar: true}));
    """ % {
        "lista": json.dumps(LISTA),
        "curriculos": json.dumps(curriculos),
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

    print('--- abrir o curriculo de quem tem formacao')
    page.click('#cat-lista [data-id="2"]')
    page.wait_for_timeout(400)
    checar('a lista some e o curriculo aparece',
           page.eval_on_selector('#cat-tela-lista', 'e => e.hidden') is True and
           page.eval_on_selector('#cat-tela-curriculo', 'e => e.hidden') is False)
    texto = page.inner_text('#cat-curriculo-corpo')
    checar('a coluna diocesana aparece com a formacao', 'Formação diocesana 2026' in texto)
    checar('a coluna paroquial aparece com a formacao', 'Escola de catequistas 2026' in texto)
    checar('regional sem inscricao diz isso', 'Nenhuma inscrição neste nível' in texto)

    print('--- cada encontro descreve presenca, falta OU justificativa -- nao so a data')
    checar('tem um selo "Presente"', 'Presente' in texto)
    checar('tem um selo "Faltou" (nao so a data)', 'Faltou' in texto)
    checar('tem um selo "Justificada"', 'Justificada' in texto)

    print('--- o checkbox bate com atingiuMinimo DAQUELA formacao, nao o agregado')
    marcados = page.eval_on_selector_all(
        '.cat-formacao input[type="checkbox"]', 'e => e.map(x => x.checked)')
    checar('diocesana (50%, minimo 80%) desmarcada, paroquial (100%) marcada',
           marcados == [False, True], marcados)

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

    print('--- 400px: nada estoura o pai')
    p400 = abrir('COORDENADOR_PAROQUIAL', 400)
    p400.click('button.tab-btn[data-tab="catequistas"]')
    p400.wait_for_timeout(400)
    p400.click('#cat-lista [data-id="2"]')
    p400.wait_for_timeout(400)
    estouros = p400.evaluate("""() => {
      const raiz = document.getElementById('tab-catequistas');
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
