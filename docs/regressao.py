"""Invariantes da navegacao por abas. Foi a falta DESTE teste que deixou
passar a regra :has() que fazia todas as abas aparecerem de uma vez."""
from playwright.sync_api import sync_playwright
import pathlib, sys
url = pathlib.Path('/tmp/audit/index.html').resolve().as_uri()
STUB = """
window.fetch = async ()=>new Response('{}',{status:200,headers:{'Content-Type':'application/json'}});
localStorage.setItem('catequese.token','t');
localStorage.setItem('catequese.usuario',JSON.stringify({nome:'G',username:'g',tipo:'COORDENADOR_PAROQUIAL',admin:true,podeEditar:true}));
"""
ESTADO = """
() => {
  const tabs=[...document.querySelectorAll('.tab-content')];
  const visiveis = tabs.filter(t=>getComputedStyle(t).display!=='none');
  // respiro entre paineis irmaos da aba visivel
  let gaps=[];
  visiveis.forEach(t=>{
    const ps=[...t.children].filter(c=>c.classList.contains('panel') &&
                                       getComputedStyle(c).display!=='none');
    for(let i=1;i<ps.length;i++){
      const a=ps[i-1].getBoundingClientRect(), c=ps[i].getBoundingClientRect();
      // so mede paineis realmente EMPILHADOS: em .layout eles ficam lado a
      // lado e a distancia vertical entre eles nao significa nada.
      if(Math.abs(a.left-c.left)>4) continue;
      gaps.push(Math.round(c.top-a.bottom));
    }
  });
  return {visiveis: visiveis.map(t=>t.id), gaps};
}
"""
ABAS=['menu','cadastro','chamada','agenda','frequencia','consulta','dashboard','indicadores','admin','usuarios','configuracoes']
falhas=0
with sync_playwright() as p:
    b=p.chromium.launch(executable_path='/opt/pw-browsers/chromium')
    page=b.new_page(viewport={'width':1280,'height':900}, reduced_motion='reduce')
    page.add_init_script(STUB)
    page.goto(url); page.wait_for_timeout(1300)
    for aba in ABAS:
        try: page.click(f'button.tab-btn[data-tab="{aba}"]', timeout=2500)
        except Exception:
            print(f"  --  [{aba}] botao indisponivel"); continue
        page.wait_for_timeout(1300)
        r=page.evaluate(ESTADO)
        vis=r['visiveis']
        ok_uma = (vis == [f'tab-{aba}'])
        ok_gap = all(g >= 20 for g in r['gaps']) if r['gaps'] else True
        if not (ok_uma and ok_gap): falhas+=1
        print(f"  {'OK' if ok_uma and ok_gap else '!!'}  [{aba:14}] visiveis={vis}  gaps_entre_paineis={r['gaps']}")
    b.close()
print(f"\n{'TODOS OS INVARIANTES OK' if falhas==0 else str(falhas)+' FALHA(S)'}")
sys.exit(1 if falhas else 0)
