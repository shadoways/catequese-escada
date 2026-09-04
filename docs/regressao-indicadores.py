"""Tela de Indicadores: as cinco telas, os filtros de cada uma e a comparacao.

O que estes testes protegem, em ordem de importancia:

  1. NENHUM NUMERO SEM COMPARACAO. E a regra que define o relatorio; se um
     cartao puder sair sozinho, a tela volta a ser um contador.
  2. CADA TELA COM O FILTRO DELA, e o filtro de uma NAO vaza para a outra --
     so o ano viaja. Filtro herdado invisivel e a pior forma de ler um numero
     errado.
  3. Trocar de comunidade limpa turma e catequista: manter um vinculo que nao
     pertence mais ao recorte devolveria tela vazia sem explicacao.
  4. Base zero vira "novo"; base pequena mostra so a diferenca; sem ano
     anterior nao se inventa variacao de 0%.
  5. 403 explica que o relatorio e exclusivo do coordenador paroquial.
"""
from playwright.sync_api import sync_playwright
import json, pathlib, sys

url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()

OPCOES = {
    "anos": [2026, 2025, 2024],
    "comunidades": [{"id": 1, "nome": "São José"},
                    {"id": 2, "nome": "Nossa Senhora Aparecida do Perpétuo Socorro"}],
    "turmas": [{"id": 10, "nome": "Crisma I", "idComunidade": 1},
               {"id": 11, "nome": "Eucaristia A", "idComunidade": 1},
               {"id": 20, "nome": "Adultos", "idComunidade": 2}],
    "catequistas": [{"id": 100, "nome": "Ana", "idComunidade": 1},
                    {"id": 200, "nome": "Bruno", "idComunidade": 2}],
    "situacoesMatricula": [{"valor": "CURSANDO", "rotulo": "Cursando"},
                           {"valor": "DESISTENTE", "rotulo": "Desistente"}],
    "tiposEvento": [{"valor": "FORMACAO", "rotulo": "Formação"},
                    {"valor": "SACRAMENTO", "rotulo": "Sacramento"}],
    "niveisEvento": [{"valor": "PAROQUIAL", "rotulo": "Paroquial"},
                     {"valor": "TURMA", "rotulo": "Turma"}],
}


def ind(rotulo, valor, base, situacao="COMPARAVEL", direcao="MAIOR", pct=False, detalhe=None):
    variacao = None if base is None else valor - base
    relativo = variacao / base * 100 if (situacao == "COMPARAVEL" and base) else None
    return {"rotulo": rotulo, "valor": valor, "base": base, "variacao": variacao,
            "variacaoPercentual": relativo, "situacao": situacao,
            "direcaoBoa": direcao, "percentual": pct, "detalhe": detalhe}


RESUMO = {
    "ano": 2026, "anoBase": 2025, "ateODia": "2026-09-03", "anoEmCurso": True,
    "idComunidade": None, "nomeComunidade": None,
    "cabecalho": "Catequese em 2026 · comparado com 2025 · até 03/09 nos dois anos · paróquia inteira",
    "avisos": ["2 turma(s) ainda sem comunidade definida."],
    "catequisandos": ind("Catequisandos", 312, 287),
    "pessoasDistintas": None,
    "catequistas": ind("Catequistas", 34, 31),
    "formacoesNoAno": ind("Formações", 3, 2, "COMPARAVEL", "NEUTRA"),
    "eventosNoAno": ind("Eventos", 41, 38, "COMPARAVEL", "NEUTRA"),
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
    "porComunidade": [
        {"idComunidade": 1, "nome": "São José",
         "catequisandos": ind("Catequisandos", 180, 165),
         "catequistas": ind("Catequistas", 19, 18)},
        {"idComunidade": 2, "nome": "Nossa Senhora Aparecida do Perpétuo Socorro",
         "catequisandos": ind("Catequisandos", 85, 0, "NOVO"),
         "catequistas": ind("Catequistas", 8, 7, "BASE_PEQUENA")},
        {"idComunidade": None, "nome": "Sem comunidade definida",
         "catequisandos": ind("Catequisandos", 47, 52),
         "catequistas": ind("Catequistas", 7, 6, "BASE_PEQUENA")},
    ],
}

PRIMEIRO_ANO = dict(
    RESUMO, anoBase=None,
    cabecalho="Catequese em 2024 · primeiro ano apurado · paróquia inteira",
    catequisandos=ind("Catequisandos", 240, None, "SEM_BASE"),
    catequistas=ind("Catequistas", 28, None, "SEM_BASE"),
    formacoesNoAno=ind("Formações", 1, None, "SEM_BASE", "NEUTRA"),
    eventosNoAno=ind("Eventos", 12, None, "SEM_BASE", "NEUTRA"),
    porComunidade=[],
    movimento={
        "entraram": ind("Entraram", 240, None, "SEM_BASE"),
        "permaneceram": ind("Permaneceram", 0, None, "SEM_BASE"),
        "concluiram": ind("Concluíram", 0, None, "SEM_BASE"),
        "abandonaram": ind("Abandonaram", 0, None, "SEM_BASE", "MENOR"),
        "transferidos": 0, "saldo": 240,
        "retencao": ind("Retenção", 0, None, "SEM_BASE", "MAIOR", True)})

MATRICULAS = {
    "cabecalho": "Matrículas · 2026 · comparado com 2025 · paróquia inteira",
    "total": ind("Matrículas", 345, 330),
    "cursando": ind("Cursando", 312, 287),
    "desistentes": ind("Desistentes", 9, 14, "COMPARAVEL", "MENOR"),
    "concluiram": ind("Concluíram", 24, 21),
    "porAno": [{"ano": 2024, "cursando": 265, "concluiram": 18, "naoConcluiram": 4,
                "transferidos": 2, "desistentes": 11, "total": 300},
               {"ano": 2025, "cursando": 287, "concluiram": 21, "naoConcluiram": 5,
                "transferidos": 3, "desistentes": 14, "total": 330},
               {"ano": 2026, "cursando": 312, "concluiram": 24, "naoConcluiram": 0,
                "transferidos": 0, "desistentes": 9, "total": 345}],
    "porTurma": [{"idTurma": 10, "turma": "Crisma I", "comunidade": "São José",
                  "categoria": "CRISMA", "cursando": 28, "concluiram": 0,
                  "desistentes": 3, "total": 31}],
    "avisos": [],
}

FREQUENCIA = {
    "cabecalho": "Frequência · 2026 · comparado com 2025 · paróquia inteira",
    "aproveitamento": ind("Aproveitamento médio", 84.2, 86.9, "COMPARAVEL", "MAIOR", True,
                          "212 catequisandos apurados"),
    "regulares": ind("Regulares", 169, 180),
    "pertoDoLimite": ind("Perto do limite", 25, 30, "COMPARAVEL", "MENOR",
                         detalhe="já no mínimo, mas abaixo de 85% — uma falta derruba"),
    "abaixo": ind("Abaixo do mínimo", 18, 12, "COMPARAVEL", "MENOR"),
    # alerta (85) e MAIOR que o minimo (80) -- e assim no Configuracao de
    # verdade. A fixture tinha 70 e escondia o texto invertido da tela.
    "minimo": 80, "alerta": 85,
    "turmas": [{"idTurma": 10, "turma": "Crisma I", "comunidade": "São José",
                "categoria": "CRISMA", "exigeFrequencia": True, "apurados": 28,
                "media": 71.4, "regulares": 12, "pertoDoLimite": 10, "abaixo": 6,
                "encontrosFechados": 14, "encontrosCancelados": 1},
               {"idTurma": 30, "turma": "Pré-catequese", "comunidade": "São José",
                "categoria": "PRE_CATEQUESE", "exigeFrequencia": False, "apurados": 0,
                "media": None, "regulares": 0, "pertoDoLimite": 0, "abaixo": 0,
                "encontrosFechados": 0, "encontrosCancelados": 0}],
    "catequisandos": [],
    "avisos": ["Escolha uma turma para ver a frequência de cada catequisando."],
}

FREQUENCIA_TURMA = dict(
    FREQUENCIA,
    cabecalho="Frequência · 2026 · comparado com 2025 · Crisma I",
    avisos=[],
    catequisandos=[
        {"idCatequisando": 1, "nome": "Ana Clara", "idTurma": 10, "turma": "Crisma I",
         "percentual": 42.9, "situacao": "ABAIXO_DO_MINIMO", "presencas": 6, "faltas": 8,
         "justificadas": 0, "encontros": 14},
        {"idCatequisando": 2, "nome": "João Pedro", "idTurma": 10, "turma": "Crisma I",
         "percentual": 92.9, "situacao": "REGULAR", "presencas": 13, "faltas": 1,
         "justificadas": 0, "encontros": 14}])

FORMACAO = {
    "cabecalho": "Formação de catequistas · 2026 · paróquia inteira",
    "inscritos": ind("Inscritos", 40, None, "SEM_BASE"),
    "participaram": ind("Participaram", 26, None, "SEM_BASE", detalhe="com ao menos uma presença"),
    "atingiramMinimo": ind("Atingiram o mínimo", 18, None, "SEM_BASE"),
    "porNivel": [
        {"nivel": "DIOCESANO", "rotulo": "Diocesano", "formacoes": 1, "encontrosRealizados": 4,
         "inscritos": ind("Inscritos", 40, None, "SEM_BASE"),
         "participaram": ind("Participaram", 6, None, "SEM_BASE"),
         "atingiramMinimo": ind("Atingiram o mínimo", 4, None, "SEM_BASE"),
         "taxaParticipacao": ind("Participação", 15.0, None, "SEM_BASE", "MAIOR", True),
         "minimo": 80}],
    "catequistas": [
        {"idCatequista": 100, "nome": "Ana", "comunidade": "São José", "formacoes": 2,
         "encontrosPossiveis": 9, "presencas": 9, "percentual": 100.0, "atingiuMinimo": True},
        {"idCatequista": 200, "nome": "Bruno", "comunidade": "Nossa Senhora Aparecida",
         "formacoes": 2, "encontrosPossiveis": 9, "presencas": 1, "percentual": 11.1,
         "atingiuMinimo": False}],
    "comunidades": [
        {"idComunidade": 1, "nome": "São José", "catequistas": 19, "participaram": 17,
         "percentual": 89.5},
        {"idComunidade": 2, "nome": "Nossa Senhora Aparecida", "catequistas": 8,
         "participaram": 2, "percentual": 25.0}],
    "formacoes": [
        {"idFormacao": 1, "nome": "Formação diocesana 2026", "nivel": "DIOCESANO",
         "rotuloNivel": "Diocesano", "encontrosRealizados": 4, "inscritos": 40,
         "participaram": 6, "atingiram": 4, "minimo": 80}],
    "avisos": [],
}

EVENTOS = {
    "cabecalho": "Eventos · 2026 · comparado com 2025 · paróquia inteira",
    "total": ind("Eventos", 41, 38, "COMPARAVEL", "NEUTRA"),
    "realizados": ind("Realizados", 33, 30, "COMPARAVEL", "NEUTRA"),
    "cancelados": ind("Cancelados", 2, 5, "COMPARAVEL", "MENOR"),
    "porTipo": [{"chave": "FORMACAO", "rotulo": "Formação", "valor": 13, "base": 11},
                {"chave": "SACRAMENTO", "rotulo": "Sacramento", "valor": 6, "base": 8}],
    "porNivel": [{"chave": "PAROQUIAL", "rotulo": "Paroquial", "valor": 20, "base": 18},
                 {"chave": "TURMA", "rotulo": "Turma", "valor": 21, "base": 20}],
    "eventos": [
        {"idEvento": 1, "titulo": "Encontro diocesano de catequistas", "tipo": "FORMACAO",
         "rotuloTipo": "Formação", "nivel": "DIOCESANO", "rotuloNivel": "Diocesano",
         "data": "2026-03-14", "situacao": "REALIZADO", "local": "Matriz",
         "comunidade": None, "turma": None, "formacao": "Formação diocesana 2026",
         "publico": "Toda a diocese", "catequistasPresentes": 6, "catequisandosPresentes": None},
        {"idEvento": 2, "titulo": "Retiro da Crisma", "tipo": "ENCONTRO",
         "rotuloTipo": "Encontro", "nivel": "TURMA", "rotuloNivel": "Turma",
         "data": "2026-05-10", "situacao": "REALIZADO", "local": "Sítio",
         "comunidade": "São José", "turma": "Crisma I", "formacao": None,
         "publico": "Turma Crisma I", "catequistasPresentes": None,
         "catequisandosPresentes": 24},
        {"idEvento": 3, "titulo": "Missa de envio", "tipo": "SACRAMENTO",
         "rotuloTipo": "Sacramento", "nivel": "PAROQUIAL", "rotuloNivel": "Paroquial",
         "data": "2026-08-02", "situacao": "CANCELADO", "local": "Matriz",
         "comunidade": None, "turma": None, "formacao": None,
         "publico": "Toda a paróquia", "catequistasPresentes": None,
         "catequisandosPresentes": None}],
    "avisos": ["1 evento(s) realizados sem nenhuma chamada registrada."],
}


def stub(status=200, resumo=None):
    return """
window.__rotas = [];
window.fetch = async (u, o) => {
  const s = String(typeof u === 'string' ? u : (u && u.url) || '');
  const j = (x, st) => new Response(JSON.stringify(x),
      {status: st || 200, headers: {'Content-Type': 'application/json'}});
  if (s.includes('/api/indicadores/opcoes')) return j(%(opcoes)s);
  if (s.includes('/api/indicadores')) {
    window.__rotas.push(s);
    if (%(status)d !== 200) return j({message: 'proibido'}, %(status)d);
    if (s.includes('/matriculas')) return j(%(matriculas)s);
    if (s.includes('/frequencia')) {
      return j(s.includes('idTurma=') ? %(freqTurma)s : %(freq)s);
    }
    if (s.includes('/formacao')) return j(%(formacao)s);
    if (s.includes('/eventos')) return j(%(eventos)s);
    return j(%(resumo)s);
  }
  return j({});
};
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario', JSON.stringify(
  {nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
""" % {
        "opcoes": json.dumps(OPCOES), "status": status,
        "matriculas": json.dumps(MATRICULAS), "freq": json.dumps(FREQUENCIA),
        "freqTurma": json.dumps(FREQUENCIA_TURMA), "formacao": json.dumps(FORMACAO),
        "eventos": json.dumps(EVENTOS), "resumo": json.dumps(resumo or RESUMO),
    }


falhas = []


def checar(nome, condicao, detalhe=''):
    if not condicao:
        falhas.append(nome)
    print(f"  {'OK' if condicao else '!!'}  {nome}"
          f"{(' — ' + str(detalhe)) if detalhe and not condicao else ''}")


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

    def ir(page, vista):
        page.click(f'.ind-subnav-btn[data-vista="{vista}"]')
        page.wait_for_timeout(700)

    def filtros(page):
        return page.eval_on_selector_all(
            '#ind-filtros select[data-campo]', 'els => els.map(e => e.dataset.campo)')

    def sem_comparacao(page):
        return page.eval_on_selector_all(
            '.ind-cartao', 'els => els.filter(e => !e.querySelector(".ind-var")).length')

    print('--- resumo geral')
    page = abrir(stub())
    texto = page.inner_text('#tab-indicadores')
    checar('o painel se chama "Resumo geral"', 'Resumo geral' in texto)
    checar('todo cartao tem comparacao',
           page.eval_on_selector_all('.ind-cartao', 'e => e.length') > 0
           and sem_comparacao(page) == 0, sem_comparacao(page))
    checar('a base aparece escrita', 'contra 287 em 2025' in texto)
    checar('percentual nao perde a casa decimal', '(+8,7%)' in texto)
    checar('variacao de percentual sai em pontos percentuais', '+4,5 p.p.' in texto)
    checar('base zero vira "novo"', 'novo — não havia nenhum em 2025' in texto)
    checar('"Crescimento ano a ano" explica a pergunta', 'crescendo ou encolhendo' in texto)
    checar('"Onde está a catequese" explica a pergunta',
           'se distribuem entre as comunidades' in texto)
    checar('o resumo tem os quatro atalhos',
           page.eval_on_selector_all('.ind-atalho', 'e => e.length') == 4)
    checar('o resumo tem dois filtros', filtros(page) == ['ano', 'idComunidade'], filtros(page))

    print('--- cada tela com o filtro dela')
    ir(page, 'matriculas')
    checar('matriculas: 4 filtros',
           filtros(page) == ['ano', 'idComunidade', 'idTurma', 'situacao'], filtros(page))
    checar('matriculas mostra o historico ano a ano',
           'Ano a ano' in page.inner_text('#ind-conteudo'))
    checar('matriculas sem cartao mudo', sem_comparacao(page) == 0)

    ir(page, 'frequencia')
    checar('frequencia: 3 filtros',
           filtros(page) == ['ano', 'idComunidade', 'idTurma'], filtros(page))
    texto = page.inner_text('#ind-conteudo')
    # alerta (85) e MAIOR que o minimo (80): "perto do limite" e quem ja passou
    # do minimo e esta prestes a perde-lo. A primeira versao do texto dizia o
    # contrario -- "entre 85% e 80%" -- e nao existe faixa nenhuma nesse sentido.
    checar('frequencia explica "perto do limite" na ordem certa',
           'já atingiu o mínimo mas está abaixo de 85%' in texto)
    checar('turma que nao apura aparece como "não se aplica"', 'não se aplica' in texto)
    checar('pede a turma para descer ao catequisando',
           'Escolha uma turma' in page.inner_text('#ind-avisos'))

    ir(page, 'formacao')
    checar('formacao: 4 filtros',
           filtros(page) == ['ano', 'nivel', 'idComunidade', 'idCatequista'], filtros(page))
    texto = page.inner_text('#ind-conteudo')
    checar('formacao lista catequista a catequista', 'Catequista a catequista' in texto)
    checar('formacao mostra quem nao atingiu', 'Não atingiu' in texto)
    checar('formacao mostra participacao por comunidade',
           'Qual comunidade mandou mais gente' in texto)

    ir(page, 'eventos')
    checar('eventos: 4 filtros',
           filtros(page) == ['ano', 'tipo', 'nivel', 'idComunidade'], filtros(page))
    texto = page.inner_text('#ind-conteudo')
    checar('eventos lista cada evento com o publico', 'Toda a diocese' in texto)
    checar('eventos separa presenca de catequista e de catequisando',
           'Catequistas' in texto and 'Catequisandos' in texto)
    checar('presenca nao registrada e explicada', 'não que ninguém foi' in texto)

    print('--- filtro nao vaza de uma tela para outra')
    ir(page, 'frequencia')
    page.select_option('#ind-filtros select[data-campo="idTurma"]', '10')
    page.wait_for_timeout(700)
    checar('escolher turma desce ao catequisando',
           'Catequisando a catequisando' in page.inner_text('#ind-conteudo'))
    ir(page, 'formacao')
    checar('formacao nao herda a turma da frequencia',
           'idTurma' not in page.evaluate('window.__rotas[window.__rotas.length - 1]'))
    ir(page, 'frequencia')
    checar('voltar mantem a turma da propria tela',
           page.eval_on_selector('#ind-filtros select[data-campo="idTurma"]', 'e => e.value') == '10')

    print('--- trocar de comunidade limpa turma e catequista')
    page.select_option('#ind-filtros select[data-campo="idComunidade"]', '2')
    page.wait_for_timeout(700)
    checar('turma foi limpa ao trocar de comunidade',
           page.eval_on_selector('#ind-filtros select[data-campo="idTurma"]', 'e => e.value') == '')
    checar('a lista de turmas encolheu para a comunidade',
           page.eval_on_selector_all('#ind-filtros select[data-campo="idTurma"] option',
                                     'e => e.length') == 2)

    print('--- ficha do filtro e limpar')
    checar('a ficha do filtro aparece escrita', 'Comunidade:' in page.inner_text('#ind-avisos'))
    page.click('#ind-avisos [data-limpar="*"]')
    page.wait_for_timeout(700)
    checar('limpar zera os filtros da tela',
           page.eval_on_selector('#ind-filtros select[data-campo="idComunidade"]',
                                 'e => e.value') == '')
    checar('limpar nao mexe no ano',
           page.eval_on_selector('#ind-filtros select[data-campo="ano"]', 'e => e.value') == '2026')

    print('--- o ano viaja entre as telas')
    page.select_option('#ind-filtros select[data-campo="ano"]', '2025')
    page.wait_for_timeout(700)
    ir(page, 'eventos')
    checar('o ano escolhido continua na proxima tela',
           page.eval_on_selector('#ind-filtros select[data-campo="ano"]', 'e => e.value') == '2025')
    page.close()

    print('--- primeiro ano apurado')
    page = abrir(stub(resumo=PRIMEIRO_ANO))
    texto = page.inner_text('#tab-indicadores')
    checar('diz que nao ha base', 'sem base de comparação' in texto)
    checar('nao inventa variacao de 0%', '(+0%)' not in texto)
    page.close()

    print('--- sem permissao (403)')
    page = abrir(stub(status=403))
    texto = page.inner_text('#tab-indicadores')
    checar('403 explica o motivo', 'exclusivo do coordenador paroquial' in texto)
    checar('oferece tentar de novo', page.eval_on_selector_all('#ind-tentar', 'e => e.length') == 1)
    page.close()

    print('--- layout, nas cinco telas')
    for largura in (1280, 760, 400):
        page = abrir(stub(), largura=largura)
        for vista in ('resumo', 'matriculas', 'frequencia', 'formacao', 'eventos'):
            ir(page, vista)
            rola = page.evaluate(
                'document.documentElement.scrollWidth > document.documentElement.clientWidth + 1')
            # Tabela larga PODE passar da largura do pai: ela vive dentro de um
            # container com overflow-x proprio, que e o comportamento desejado.
            # O defeito seria a PAGINA rolar de lado.
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
            checar(f'{largura}px/{vista}: pagina nao rola na horizontal', not rola)
            checar(f'{largura}px/{vista}: nada estoura o pai', estoura == 0, estoura)
        page.close()

    navegador.close()

print(f"\n{'TUDO OK' if not falhas else str(len(falhas)) + ' FALHA(S): ' + ', '.join(falhas)}")
sys.exit(1 if falhas else 0)
