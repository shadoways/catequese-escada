from playwright.sync_api import sync_playwright
import json, pathlib, sys
url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()
OPC={"niveisQuePodeCriar":[{"valor":"PAROQUIAL","rotulo":"Paroquial"}],
 "tipos":[{"valor":"ENCONTRO","rotulo":"Encontro"}],"comunidades":[],"turmas":[],
 "formacoes":[],"podeCriar":True,"motivoNaoPodeCriar":None}
def ev(i,t,d):
    return {"idEvento":i,"titulo":t,"tipo":"ENCONTRO","tipoRotulo":"Encontro","nivel":"PAROQUIAL",
      "nivelRotulo":"Paroquial","idComunidade":None,"comunidadeNome":None,"idTurma":None,
      "turmaNome":None,"idFormacao":None,"formacaoNome":None,"descricao":None,"dataInicio":d,
      "dataFim":None,"horaInicio":"19h","local":"Matriz","situacao":"PREVISTO",
      "motivoCancelamento":None,"podeEditar":True,"minhaFrequencia":None}
AG={"ano":2026,"resumo":{},"eventos":[ev(1,"Assembleia","2026-08-15"),ev(2,"Missa","2026-08-15"),
    ev(3,"Regional","2026-08-22")]}
STUB="""
window.fetch = async (u,o)=>{
  const s=String(typeof u==='string'?u:(u&&u.url)||'');
  const j=(x)=>new Response(JSON.stringify(x),{status:200,headers:{'Content-Type':'application/json'}});
  if(s.indexOf('/api/agenda/opcoes')!==-1) return j(OPC);
  if(s.indexOf('/api/agenda/conflitos')!==-1) return j({temConflito:false,conflitos:[]});
  if(s.indexOf('/api/agenda')!==-1) return j(AG);
  return j({});
};
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario',JSON.stringify({nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
"""
EST="""() => ({
  lista: !document.getElementById('agenda-dia-painel').hidden,
  titulo: document.getElementById('agenda-dia-titulo').textContent,
  n: document.querySelectorAll('#agenda-dia-lista .agenda-ev').length,
  form: !document.getElementById('agenda-form-painel').hidden,
  fdata: document.getElementById('agenda-f-data').value,
  ftit: document.getElementById('agenda-f-titulo').value,
  marcados: document.querySelectorAll('.agenda-cal-dia--aberto').length
})"""
falhas=[]
def ck(nome,cond,det=""):
    print(f"  {'OK' if cond else '!!'}  {nome}{('  -> '+det) if det else ''}")
    if not cond: falhas.append(nome)

with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/opt/pw-browsers/chromium')
    page=b.new_page(viewport={'width':1150,'height':1200}, reduced_motion='reduce')
    page.add_init_script("window.OPC=%s;window.AG=%s;"%(json.dumps(OPC),json.dumps(AG)))
    page.add_init_script(STUB)
    page.goto(url); page.wait_for_timeout(1100)
    page.click('button.tab-btn[data-tab="agenda"]'); page.wait_for_timeout(900)
    for _ in range(14):
        if page.locator('#agenda-mes-rotulo').inner_text().startswith('Agosto'): break
        page.click('#agenda-mes-anterior'); page.wait_for_timeout(200)
    dia=lambda d: page.locator(f'[data-dia="{d}"] .agenda-cal-num')
    vazio=lambda d: page.locator(f'[data-novo="{d}"] .agenda-cal-num')
    mais=lambda d: page.locator(f'[data-novo-dia="{d}"]')

    print("\n1) dia com evento -> OUTRO dia com evento")
    dia("2026-08-15").click(); page.wait_for_timeout(500)
    mais("2026-08-15").click(); page.wait_for_timeout(600)
    page.fill('#agenda-f-titulo','rascunho 15')
    dia("2026-08-22").click(); page.wait_for_timeout(700)
    e=page.evaluate(EST)
    ck("lista trocou para o 22","22 de agosto" in e['titulo'], e['titulo'])
    ck("so 1 evento (o do 22)", e['n']==1, str(e['n']))
    ck("formulario do 15 fechou", not e['form'])
    ck("so 1 dia destacado", e['marcados']==1, str(e['marcados']))

    print("\n2) dia com evento (form aberto) -> dia VAZIO")
    mais("2026-08-22").click(); page.wait_for_timeout(600)
    vazio("2026-08-10").click(); page.wait_for_timeout(700)
    e=page.evaluate(EST)
    ck("lista fechou", not e['lista'])
    ck("formulario aberto no dia novo", e['form'] and e['fdata']=="2026-08-10", e['fdata'])
    ck("nenhum dia destacado", e['marcados']==0, str(e['marcados']))

    print("\n3) dia VAZIO (form aberto) -> dia com evento")
    page.fill('#agenda-f-titulo','rascunho 10')
    dia("2026-08-15").click(); page.wait_for_timeout(700)
    e=page.evaluate(EST)
    ck("lista do 15 abriu", e['lista'] and "15 de agosto" in e['titulo'], e['titulo'])
    ck("formulario do 10 fechou", not e['form'])

    print("\n4) RECLICAR o MESMO dia nao descarta o rascunho")
    mais("2026-08-15").click(); page.wait_for_timeout(600)
    page.fill('#agenda-f-titulo','nao pode sumir')
    dia("2026-08-15").click(); page.wait_for_timeout(700)
    e=page.evaluate(EST)
    ck("formulario continua aberto", e['form'])
    ck("rascunho preservado", e['ftit']=="nao pode sumir", e['ftit'])

    print("\n5) 'Novo evento' do topo fecha a lista do dia")
    dia("2026-08-15").click(); page.wait_for_timeout(500)
    page.click('#agenda-novo'); page.wait_for_timeout(600)
    e=page.evaluate(EST)
    ck("lista fechou", not e['lista'])
    ck("formulario abriu sem data", e['form'] and e['fdata']=="", repr(e['fdata']))

    print("\n6) '+' e 'Novo evento neste dia' MANTEM a lista")
    dia("2026-08-15").click(); page.wait_for_timeout(500)
    mais("2026-08-15").click(); page.wait_for_timeout(600)
    ck("lista continua aberta pelo +", page.evaluate(EST)['lista'])
    page.click('#agenda-cancelar'); page.wait_for_timeout(300)
    dia("2026-08-15").click(); page.wait_for_timeout(400)
    page.click('#agenda-dia-novo'); page.wait_for_timeout(600)
    e=page.evaluate(EST)
    ck("lista continua aberta pelo botao do rodape", e['lista'])
    ck("data preenchida", e['fdata']=="2026-08-15", e['fdata'])

    print("\n7) botao Fechar continua funcionando")
    page.click('#agenda-dia-fechar'); page.wait_for_timeout(400)
    e=page.evaluate(EST)
    ck("lista fechou", not e['lista'])
    ck("nenhum dia destacado", e['marcados']==0)
    b.close()
print(f"\n{'TODAS AS TRANSICOES OK' if not falhas else str(len(falhas))+' FALHA(S): '+str(falhas)}")
sys.exit(1 if falhas else 0)
