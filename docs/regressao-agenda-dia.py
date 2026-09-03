from playwright.sync_api import sync_playwright
import json, pathlib, sys
url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()

OPC={"niveisQuePodeCriar":[{"valor":"PAROQUIAL","rotulo":"Paroquial"}],
 "tipos":[{"valor":"ENCONTRO","rotulo":"Encontro"}],"comunidades":[],"turmas":[],
 "formacoes":[],"podeCriar":True,"motivoNaoPodeCriar":None}
def ev(i,t,d,pode=True,**k):
    b={"idEvento":i,"titulo":t,"tipo":"ENCONTRO","tipoRotulo":"Encontro","nivel":"PAROQUIAL",
       "nivelRotulo":"Paroquial","idComunidade":None,"comunidadeNome":None,"idTurma":None,
       "turmaNome":None,"idFormacao":None,"formacaoNome":None,"descricao":None,"dataInicio":d,
       "dataFim":None,"horaInicio":"19h","local":"Matriz","situacao":"PREVISTO",
       "motivoCancelamento":None,"podeEditar":pode,"minhaFrequencia":None}
    b.update(k); return b
EVS=[ev(1,"Assembleia diocesana","2026-08-15"),
     ev(2,"Missa de envio","2026-08-15"),
     ev(3,"Formação paroquial","2026-08-15",pode=False),
     ev(4,"Encontro regional","2026-08-22")]
AG={"ano":2026,"resumo":{},"eventos":EVS}
STUB = """
window.__del=[];
window.fetch = async (u,o)=>{
  const s=String(typeof u==='string'?u:(u&&u.url)||''); const m=(o&&o.method)||'GET';
  const j=(x,st)=>new Response(JSON.stringify(x),{status:st||200,headers:{'Content-Type':'application/json'}});
  if(s.indexOf('/api/agenda/opcoes')!==-1) return j(OPC);
  if(s.indexOf('/api/agenda/conflitos')!==-1) return j({temConflito:false,conflitos:[]});
  if(s.indexOf('/api/agenda/eventos/')!==-1 && m==='DELETE'){
    const id=Number(s.split('/').pop()); window.__del.push(id);
    window.AG={...window.AG, eventos: window.AG.eventos.filter(e=>e.idEvento!==id)};
    return new Response(null,{status:204});
  }
  if(s.indexOf('/api/agenda')!==-1) return j(window.AG);
  return j({});
};
window.confirm = () => true;
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario',JSON.stringify({nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
"""
falhas=[]
def check(nome, cond, detalhe=""):
    print(f"  {'OK' if cond else '!!'}  {nome}{('  -> '+detalhe) if detalhe else ''}")
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
    print(f"\n--- mes: {page.locator('#agenda-mes-rotulo').inner_text()} ---\n")

    print("CENARIO 1: clicar em dia COM eventos")
    page.locator('[data-dia="2026-08-15"] .agenda-cal-num').click(); page.wait_for_timeout(600)
    check("lista do dia abriu", page.locator('#agenda-dia-painel').is_visible())
    check("formulario NAO abriu", page.locator('#agenda-form-painel').is_hidden())
    check("titulo com a data", "15 de agosto" in page.locator('#agenda-dia-titulo').inner_text(),
          page.locator('#agenda-dia-titulo').inner_text())
    check("3 eventos listados", page.locator('#agenda-dia-lista .agenda-ev').count()==3,
          str(page.locator('#agenda-dia-lista .agenda-ev').count()))
    check("2 botoes Editar (o 3o e sem permissao)",
          page.locator('#agenda-dia-lista .agenda-editar').count()==2,
          str(page.locator('#agenda-dia-lista .agenda-editar').count()))
    check("2 botoes Excluir", page.locator('#agenda-dia-lista .agenda-excluir').count()==2)
    check("celula marcada como aberta", page.locator('.agenda-cal-dia--aberto').count()==1)
    page.screenshot(path='/tmp/audit/dia-lista.png', full_page=False)

    print("\nCENARIO 2: o + adiciona outro evento no mesmo dia")
    page.locator('[data-novo-dia="2026-08-15"]').click(); page.wait_for_timeout(600)
    check("formulario abriu", page.locator('#agenda-form-painel').is_visible())
    check("data ja preenchida", page.locator('#agenda-f-data').input_value()=="2026-08-15",
          page.locator('#agenda-f-data').input_value())
    check("lista do dia continua aberta", page.locator('#agenda-dia-painel').is_visible())
    page.click('#agenda-cancelar'); page.wait_for_timeout(400)

    print("\nCENARIO 3: clicar em dia SEM eventos")
    page.locator('[data-novo="2026-08-10"]').click(); page.wait_for_timeout(600)
    check("formulario abriu direto", page.locator('#agenda-form-painel').is_visible())
    check("data preenchida", page.locator('#agenda-f-data').input_value()=="2026-08-10")
    page.click('#agenda-cancelar'); page.wait_for_timeout(400)

    print("\nCENARIO 4: editar pela lista do dia")
    page.locator('[data-dia="2026-08-15"] .agenda-cal-num').click(); page.wait_for_timeout(500)
    page.locator('#agenda-dia-lista .agenda-editar').first.click(); page.wait_for_timeout(600)
    check("abriu em modo edicao",
          page.locator('#agenda-form-titulo').inner_text()=="Editar evento")
    check("titulo carregado", page.locator('#agenda-f-titulo').input_value()=="Assembleia diocesana",
          page.locator('#agenda-f-titulo').input_value())
    page.click('#agenda-cancelar'); page.wait_for_timeout(400)

    print("\nCENARIO 5: excluir pela lista do dia")
    n0 = page.locator('#agenda-dia-lista .agenda-ev').count()
    page.locator('#agenda-dia-lista .agenda-excluir').first.click(); page.wait_for_timeout(900)
    check("DELETE chamado", len(page.evaluate("window.__del"))==1, str(page.evaluate("window.__del")))
    check("lista continua aberta", page.locator('#agenda-dia-painel').is_visible())
    n1 = page.locator('#agenda-dia-lista .agenda-ev').count()
    check("um evento a menos", n1==n0-1, f"{n0} -> {n1}")

    print("\nCENARIO 6: excluir os que sobraram fecha a lista")
    for _ in range(5):
        if not page.locator('#agenda-dia-lista .agenda-excluir').count(): break
        page.locator('#agenda-dia-lista .agenda-excluir').first.click(); page.wait_for_timeout(800)
    # sobrou 1 sem permissao de excluir -> lista segue aberta
    check("lista aberta com o evento sem permissao",
          page.locator('#agenda-dia-painel').is_visible() and
          page.locator('#agenda-dia-lista .agenda-ev').count()==1)

    print("\nCENARIO 7: trocar de mes fecha a lista do dia")
    page.click('#agenda-mes-anterior'); page.wait_for_timeout(600)
    check("lista fechou", page.locator('#agenda-dia-painel').is_hidden())

    b.close()
print(f"\n{'TODOS OS CENARIOS OK' if not falhas else str(len(falhas))+' FALHA(S): '+str(falhas)}")
sys.exit(1 if falhas else 0)
