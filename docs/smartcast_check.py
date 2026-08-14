"""Procura padroes que exigem smart cast em propriedade (nao compila quando a
classe e aberta: entidades JPA com o plugin allOpen)."""
import re, glob, sys
import os

# O glob abaixo e RELATIVO. Rodar este script de fora da raiz do repositorio
# fazia ele escanear ZERO arquivos e imprimir "0 problemas" -- um sucesso
# vazio, que ja deixou passar um erro de compilacao para producao.
# A partir daqui, ele se recusa a rodar no lugar errado.
if not os.path.isdir('src/main/kotlin'):
    raise SystemExit(
        "ERRO: rode este script na raiz do repositorio (onde existe "
        "src/main/kotlin). Diretorio atual: " + os.getcwd()
    )


problemas = 0
for f in sorted(glob.glob('src/main/kotlin/**/*.kt', recursive=True)):
    linhas = open(f, encoding='utf-8').read().split('\n')
    # propriedades nulaveis declaradas no arquivo
    props = set(re.findall(r'val\s+(\w+)\s*:\s*[\w<>]+\?', '\n'.join(linhas)))
    for i, l in enumerate(linhas, 1):
        if l.strip().startswith('//') or l.strip().startswith('*'):
            continue
        # "x != null && ... x" na mesma linha, sendo x uma propriedade nulavel
        for m in re.finditer(r'\b(\w+)\s*!=\s*null\s*&&', l):
            nome = m.group(1)
            if nome in props and re.search(r'&&.*\b' + re.escape(nome) + r'\b', l):
                print(f"RISCO {f}:{i}  -> '{nome}' usado apos '!= null' na mesma expressao")
                print(f"      {l.strip()}")
                problemas += 1
        # "if (obj.prop != null)" seguido de uso de obj.prop
        for m in re.finditer(r'\b(\w+\.\w+)\s*!=\s*null\s*&&', l):
            alvo = m.group(1)
            if re.search(r'&&.*' + re.escape(alvo), l):
                print(f"RISCO {f}:{i}  -> '{alvo}' usado apos '!= null' na mesma expressao")
                print(f"      {l.strip()}")
                problemas += 1

print(f"\n{problemas} ponto(s) de risco de smart cast.")
sys.exit(0)
