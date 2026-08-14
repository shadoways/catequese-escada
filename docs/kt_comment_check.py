import sys, glob

def analisa(caminho):
    s = open(caminho, encoding='utf-8').read()
    i, n = 0, len(s)
    depth = 0
    aberturas = []
    linha = 1
    while i < n:
        c = s[i]
        if c == '\n':
            linha += 1; i += 1; continue
        if depth == 0:
            # string bruta
            if s.startswith('"""', i):
                j = s.find('"""', i+3)
                if j == -1: break
                linha += s.count('\n', i, j+3); i = j+3; continue
            # string normal
            if c == '"':
                i += 1
                while i < n and s[i] != '"':
                    if s[i] == '\\': i += 1
                    i += 1
                i += 1; continue
            # comentario de linha
            if s.startswith('//', i):
                j = s.find('\n', i)
                i = n if j == -1 else j
                continue
        if s.startswith('/*', i):
            depth += 1; aberturas.append(linha); i += 2; continue
        if s.startswith('*/', i) and depth > 0:
            depth -= 1; aberturas.pop(); i += 2; continue
        i += 1
    return depth, aberturas

problemas = 0
for f in sorted(glob.glob('src/main/kotlin/**/*.kt', recursive=True)):
    depth, aberturas = analisa(f)
    if depth != 0:
        problemas += 1
        print(f"FALHA  {f}  -> {depth} comentario(s) sem fechar, aberto(s) na(s) linha(s) {aberturas}")
print(f"\n{problemas} arquivo(s) com problema de comentario aninhado.")
