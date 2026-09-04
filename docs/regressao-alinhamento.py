"""Botao ao lado de campo divide a base com ele -- em todas as abas.

Um <label> e rotulo + campo empilhados, entao e mais alto que um <button>
sozinho. Numa `.row` centralizada, o botao flutuava na altura do ROTULO, meia
duzia de pixels acima da caixa do campo que ele aciona, e a barra de filtro
ficava torta sem que ninguem soubesse dizer por que.

Isto e o tipo de coisa que se conserta numa tela e volta na proxima, porque
ninguem mede: a diferenca e pequena o bastante para passar no olho e grande o
bastante para a tela parecer desleixada. Aqui ela e medida.

Duas larguras, porque a quebra muda o problema: em 1280 tudo cabe na linha; em
760 os campos comecam a disputar espaco, e um botao que quebra sozinho para a
linha de baixo fica longe do campo que consulta -- outro desalinho, de outra
natureza. A tolerancia de 80px separa os dois casos: acima disso houve quebra
de linha, e a comparacao seria entre linhas diferentes.
"""
from playwright.sync_api import sync_playwright
import pathlib, sys

url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()

ABAS = ['menu', 'cadastro', 'chamada', 'agenda', 'frequencia', 'consulta',
        'dashboard', 'indicadores', 'admin', 'usuarios', 'configuracoes']

# Sem servidor: tudo responde vazio. O que se mede aqui e geometria de
# formulario, e ela nao depende do conteudo das listas.
STUB = """
window.fetch = async () => new Response('[]',
    {status: 200, headers: {'Content-Type': 'application/json'}});
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario', JSON.stringify(
  {nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
"""

MEDIR = """(aba) => {
  const fora = [];
  document.querySelectorAll('.tab-content:not([hidden]) .row').forEach((linha, i) => {
    const campos = [...linha.children].filter(e => e.tagName === 'LABEL');
    const botoes = [...linha.children].filter(e => e.tagName === 'BUTTON');
    if (!campos.length || !botoes.length) return;
    if (!linha.getClientRects().length) return;
    campos.forEach(c => botoes.forEach(b => {
      const rc = c.getBoundingClientRect(), rb = b.getBoundingClientRect();
      // Mais de 80px de diferenca no topo e quebra de linha, nao desalinho.
      if (Math.abs(rc.top - rb.top) > 80) return;
      const d = Math.abs(rc.bottom - rb.bottom);
      if (d > 1.5) fora.push(`${aba}#${i}: "${b.textContent.trim().slice(0, 18)}" `
                             + `${Math.round(d)}px fora da base do campo`);
    }));
  });
  return fora;
}"""

falhas = []

with sync_playwright() as p:
    navegador = p.chromium.launch(executable_path='/opt/pw-browsers/chromium')
    for largura in (1280, 760):
        print(f'--- {largura}px')
        page = navegador.new_page(viewport={'width': largura, 'height': 900},
                                  reduced_motion='reduce')
        page.add_init_script(STUB)
        page.goto(url)
        page.wait_for_timeout(500)
        for aba in ABAS:
            try:
                page.click(f'button.tab-btn[data-tab="{aba}"]')
            except Exception:
                continue
            page.wait_for_timeout(400)
            fora = page.evaluate(MEDIR, aba)
            falhas += fora
            print(f"  {'OK' if not fora else '!!'}  {aba}"
                  + (('  ' + ' | '.join(fora)) if fora else ''))
        page.close()
    navegador.close()

print()
if falhas:
    print(f'{len(falhas)} DESALINHAMENTO(S)')
    sys.exit(1)
print('TODO BOTAO DIVIDE A BASE COM O CAMPO AO LADO')
