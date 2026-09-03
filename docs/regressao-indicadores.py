"""Tela de Indicadores: comparacao, filtro e o caso sem base.

O que estes testes protegem, em ordem de importancia:

  1. NENHUM NUMERO SEM COMPARACAO. E a regra que define a tela; se um cartao
     puder sair sozinho, ela volta a ser um contador.
  2. Base zero vira "novo", nao "+100%"; base pequena mostra so a diferenca.
  3. Primeiro ano apurado nao inventa uma variacao de 0%.
  4. Clicar numa comunidade filtra, e a ficha do filtro aparece escrita --
     filtro esquecido e o erro mais caro que uma tela de relatorio comete.
  5. 403 explica que o relatorio e exclusivo do coordenador paroquial, em vez
     de deixar a tela muda.
"""
from playwright.sync_api import sync_playwright
import json, pathlib, sys

url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()

OPCOES = {"anos": [2026, 2025, 2024], "comunidades": [
    {"id": 1, "nome": "São José"},
    {"id": 2, "nome": "Nossa Senhora Aparecida do Perpétuo Socorro"},
]}


def ind(rotulo, valor, base, situacao="COMPARAVEL", direcao="MAIOR",
        pct=False, detalhe=None):
    variacao = None if base is None else valor - base
    relativo = None
    if situacao == "COMPARAVEL" and base:
        relativo = variacao / base * 100
    return {"rotulo": rotulo, "valor": valor, "base": base, "variacao": variacao,
            "variacaoPercentual": relativo, "situacao": situacao,
            "direcaoBoa": direcao, "percentual": pct, "detalhe": detalhe}


def relatorio(**over):
    base = {
        "ano": 2026, "anoBase": 2025, "ateODia": "2026-09-03", "anoEmCurso": True,
        "idComunidade": None, "nomeComunidade": None,
        "cabecalho": "Catequese em 2026 · comparado com 2025 · ate 03/09 nos dois anos · paroquia inteira",
        "avisos": ["2 turma(s) ainda sem comunidade definida — 47 matricula(s) aparecem em \"Sem comunidade definida\"."],
        "catequisandos": ind("Catequisandos", 312, 287),
        "pessoasDistintas": None,
        "catequistas": ind("Catequistas", 34, 31),
        "evolucaoCatequisandos": [{"ano": a, "valor": v} for a, v in
                                  [(2023, 240), (2024, 265), (2025, 287), (2026, 312)]],
        "evolucaoCatequistas": [{"ano": a, "valor": v} for a, v in
                                [(2023, 28), (2024, 30), (2025, 31), (2026, 34)]],
        "movimento": {
            "entraram": ind("Entraram", 61, 54),
            "permaneceram": ind("Permaneceram", 251, 233),
            "concluiram": ind("Concluíram", 24, 21),
            "abandonaram": ind("Abandonaram", 9, 14, "COMPARAVEL", "MENOR"),
            "transferidos": 3, "saldo": 25,
            "retencao": ind("Retenção", 92.6, 88.1, "COMPARAVEL", "MAIOR", True),
        },
        "situacaoMatriculas": [
            {"chave": "CURSANDO", "rotulo": "Cursando", "valor": 312, "base": 287},
            {"chave": "DESISTENTE", "rotulo": "Desistente", "valor": 9, "base": 14},
        ],
        "porComunidade": [
            {"idComunidade": 1, "nome": "São José",
             "catequisandos": ind("Catequisandos", 180, 165),
             "catequistas": ind("Catequistas", 19, 18)},
            {"idComunidade": 2, "nome": "Nossa Senhora Aparecida do Perpétuo Socorro",
             "catequisandos": ind("Catequisandos", 85, 0, "NOVO"),
             "catequistas": ind("Catequistas", 8, 7, "BASE_PEQUENA")},
            {"idComunidade": None, "nome": "Sem comunidade definida",
             "catequisandos": ind("Catequisandos", 47, 52, "COMPARAVEL", "MAIOR"),
             "catequistas": ind("Catequistas", 7, 6, "BASE_PEQUENA")},
        ],
        "formacoes": [
            {"nivel": "DIOCESANO", "rotulo": "Diocesano", "formacoes": 1,
             "encontrosRealizados": 4,
             "inscritos": ind("Inscritos", 40, 38), "participaram": ind("Participaram", 6, 22),
             "atingiramMinimo": ind("Atingiram o mínimo", 4, 18),
             "taxaParticipacao": ind("Participação", 15.0, 57.9, "COMPARAVEL", "MAIOR", True),
             "minimo": 80},
            {"nivel": "REGIONAL", "rotulo": "Regional", "formacoes": 0, "encontrosRealizados": 0,
             "inscritos": ind("Inscritos", 0, 0), "participaram": ind("Participaram", 0, 0),
             "atingiramMinimo": ind("Atingiram o mínimo", 0, 0),
             "taxaParticipacao": ind("Participação", 0, 0, "COMPARAVEL", "MAIOR", True), "minimo": 80},
            {"nivel": "PAROQUIAL", "rotulo": "Paroquial", "formacoes": 2, "encontrosRealizados": 9,
             "inscritos": ind("Inscritos", 34, 31), "participaram": ind("Participaram", 30, 26),
             "atingiramMinimo": ind("Atingiram o mínimo", 25, 20),
             "taxaParticipacao": ind("Participação", 88.2, 83.9, "COMPARAVEL", "MAIOR", True),
             "minimo": 80},
        ],
        "frequencia": {
            "media": ind("Frequência média", 84.2, 86.9, "COMPARAVEL", "MAIOR", True),
            "abaixoDoMinimo": ind("Abaixo do mínimo", 18, 12, "COMPARAVEL", "MENOR"),
            "emRisco": ind("Em risco", 25, 30, "COMPARAVEL", "MENOR"),
            "turmasApuradas": 14, "turmasSemApuracao": 2, "turmasNaoSeAplica": 3, "minimo": 80,
        },
        "eventos": {
            "total": ind("Eventos", 41, 38, "COMPARAVEL", "NEUTRA"),
            "realizados": ind("Realizados", 33, 30, "COMPARAVEL", "NEUTRA"),
            "cancelados": ind("Cancelados", 2, 5, "COMPARAVEL", "MENOR"),
            "porTipo": [
                {"chave": "FORMACAO", "rotulo": "Formação", "valor": 13, "base": 11},
                {"chave": "SACRAMENTO", "rotulo": "Sacramento", "valor": 6, "base": 8},
                {"chave": "RITO_RICA", "rotulo": "Rito do RICA", "valor": 4, "base": 3},
                {"chave": "ENCONTRO", "rotulo": "Encontro", "valor": 18, "base": 16},
            ],
        },
    }
    base.update(over)
    return base


PRIMEIRO_ANO = relatorio(
    anoBase=None,
    cabecalho="Catequese em 2024 · primeiro ano apurado · paroquia inteira",
    catequisandos=ind("Catequisandos", 240, None, "SEM_BASE"),
    catequistas=ind("Catequistas", 28, None, "SEM_BASE"),
    evolucaoCatequisandos=[{"ano": 2024, "valor": 240}],
    evolucaoCatequistas=[{"ano": 2024, "valor": 28}],
    movimento={
        "entraram": ind("Entraram", 240, None, "SEM_BASE"),
        "permaneceram": ind("Permaneceram", 0, None, "SEM_BASE"),
        "concluiram": ind("Concluíram", 0, None, "SEM_BASE"),
        "abandonaram": ind("Abandonaram", 0, None, "SEM_BASE", "MENOR"),
        "transferidos": 0, "saldo": 240,
        "retencao": ind("Retenção", 0, None, "SEM_BASE", "MAIOR", True),
    },
)

FILTRADO = relatorio(
    idComunidade=1, nomeComunidade="São José",
    cabecalho="Catequese em 2026 · comparado com 2025 · São José",
)


def stub(payloads, status=200):
    return """
window.__pedidos = [];
window.fetch = async (u, o) => {
  const s = String(typeof u === 'string' ? u : (u && u.url) || '');
  window.__pedidos.push(s);
  const j = (x, st) => new Response(JSON.stringify(x),
      {status: st || 200, headers: {'Content-Type': 'application/json'}});
  if (s.includes('/api/indicadores/opcoes')) return j(%s);
  if (s.includes('/api/indicadores')) {
    if (%d !== 200) return j({message: 'proibido'}, %d);
    if (s.includes('idComunidade=1')) return j(%s);
    return j(%s);
  }
  return j({});
};
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario', JSON.stringify(
  {nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
""" % (json.dumps(OPCOES), status, status, json.dumps(FILTRADO), json.dumps(payloads))


falhas = []


def checar(nome, condicao, detalhe=''):
    marca = 'OK' if condicao else '!!'
    if not condicao:
        falhas.append(nome)
    print(f"  {marca}  {nome}{(' — ' + detalhe) if detalhe and not condicao else ''}")


with sync_playwright() as p:
    navegador = p.chromium.launch(executable_path='/opt/pw-browsers/chromium')

    def abrir(js, largura=1280):
        page = navegador.new_page(viewport={'width': largura, 'height': 900},
                                  reduced_motion='reduce')
        page.add_init_script(js)
        page.goto(url)
        page.wait_for_timeout(400)
        page.click('button.tab-btn[data-tab="indicadores"]')
        page.wait_for_timeout(900)
        return page

    print('--- ano com base de comparacao')
    page = abrir(stub(relatorio()))
    texto = page.inner_text('#tab-indicadores')

    cartoes = page.eval_on_selector_all('.ind-cartao', 'els => els.length')
    sem_comparacao = page.eval_on_selector_all(
        '.ind-cartao', 'els => els.filter(e => !e.querySelector(".ind-var")).length')
    checar('todo cartao tem comparacao', cartoes > 0 and sem_comparacao == 0,
           f'{sem_comparacao} de {cartoes} sem .ind-var')
    checar('a base aparece escrita', 'contra 287 em 2025' in texto)
    checar('cabecalho diz ate que dia comparou', 'ate 03/09 nos dois anos' in texto)
    checar('aviso de turma sem comunidade aparece', 'sem comunidade definida' in texto.lower())
    checar('base zero vira "novo"', 'novo — não havia nenhum em 2025' in texto)
    checar('base pequena nao vira percentual',
           '+1' in texto and '(+16,7%)' not in texto)
    checar('tabela acompanha o grafico',
           page.eval_on_selector_all('.ind-tabela', 'e => e.length') >= 5)
    checar('desenhou os graficos (barras, colunas e divergente)',
           page.eval_on_selector_all('.ind-barras', 'e => e.length') >= 2
           and page.eval_on_selector_all('.ind-colunas', 'e => e.length') == 2
           and page.eval_on_selector_all('.ind-diverge', 'e => e.length') == 1)
    checar('o bloco de fundos esta reservado', 'tela de lançamentos' in texto)
    # Num ano COM base, nenhum cartao pode dizer "primeiro ano apurado": seria
    # mentira, e foi o que a tela escreveu embaixo de "Entraram 61" na primeira
    # versao, porque o servico nao apurava o fluxo do ano anterior.
    checar('nenhum cartao mente "primeiro ano apurado"', 'primeiro ano apurado' not in texto)
    checar('cada evolucao diz que a escala e propria',
           texto.count('escala própria') == 2)
    # Arredondar a variacao para inteiro ja transformou +8,7% em "+9%" e
    # +4,5 p.p. em "+5 p.p.". Num relatorio isso e defeito: e o numero que
    # alguem vai repetir em reuniao e conferir depois.
    checar('percentual nao perde a casa decimal', '(+8,7%)' in texto)
    checar('variacao de percentual sai em pontos percentuais',
           '+4,5 p.p.' in texto)

    print('--- filtro por comunidade (clicar no grafico)')
    page.click('[data-comunidade="1"]')
    page.wait_for_timeout(700)
    texto = page.inner_text('#tab-indicadores')
    checar('a ficha do filtro aparece escrita', 'Comunidade: São José' in texto)
    checar('o cabecalho troca junto', 'São José' in page.inner_text('#ind-cabecalho'))
    checar('o select acompanha o clique',
           page.eval_on_selector('#ind-comunidade', 'e => e.value') == '1')

    page.click('#ind-limpar-tudo')
    page.wait_for_timeout(700)
    checar('limpar volta para a paroquia inteira',
           page.eval_on_selector('#ind-fichas', 'e => e.hidden') is True)

    print('--- clicar em "Sem comunidade definida" nao filtra')
    antes = page.eval_on_selector('#ind-comunidade', 'e => e.value')
    page.click('[data-comunidade=""]')
    page.wait_for_timeout(500)
    checar('balde sem comunidade nao vira filtro',
           page.eval_on_selector('#ind-comunidade', 'e => e.value') == antes)
    page.close()

    print('--- primeiro ano apurado')
    page = abrir(stub(PRIMEIRO_ANO))
    texto = page.inner_text('#tab-indicadores')
    checar('diz "primeiro ano apurado"', 'primeiro ano apurado' in texto)
    checar('nao inventa variacao de 0%', '(+0%)' not in texto and '▲ +0' not in texto)
    page.close()

    print('--- sem permissao (403)')
    page = abrir(stub(relatorio(), status=403))
    texto = page.inner_text('#tab-indicadores')
    checar('403 explica o motivo', 'exclusivo do coordenador paroquial' in texto)
    checar('oferece tentar de novo', page.eval_on_selector_all('#ind-tentar', 'e => e.length') == 1)
    page.close()

    print('--- layout')
    for largura in (1280, 760, 400):
        page = abrir(stub(relatorio()), largura=largura)
        rola = page.evaluate(
            'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1')
        # Tabela larga PODE passar da largura do pai: ela vive dentro de um
        # container com overflow-x proprio, que e exatamente o comportamento
        # desejado. O defeito seria a PAGINA rolar de lado -- por isso o
        # elemento so conta como estouro quando nao ha rolagem propria no
        # caminho ate a aba.
        estoura = page.evaluate("""() => {
          const pai = document.getElementById('tab-indicadores');
          const limite = pai.getBoundingClientRect().right + 1;
          const temRolagemPropria = (el) => {
            for (let n = el.parentElement; n && n !== pai; n = n.parentElement) {
              const ox = getComputedStyle(n).overflowX;
              if (ox === 'auto' || ox === 'scroll') return true;
            }
            return false;
          };
          return [...pai.querySelectorAll('*')]
            .filter(e => e.getBoundingClientRect().right > limite)
            .filter(e => !temRolagemPropria(e)).length;
        }""")
        checar(f'{largura}px: pagina nao rola na horizontal', not rola)
        checar(f'{largura}px: nada estoura o pai', estoura == 0, f'{estoura} elementos')
        page.close()

    navegador.close()

print(f"\n{'TUDO OK' if not falhas else str(len(falhas)) + ' FALHA(S): ' + ', '.join(falhas)}")
sys.exit(1 if falhas else 0)
