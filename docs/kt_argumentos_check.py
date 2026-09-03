"""Argumento nomeado que nao existe no data class, e obrigatorio que faltou.

Sem Gradle no sandbox, os erros mais caros sao os que o compilador pegaria em
um segundo:

    e: Cannot find a parameter with this name: participaram
    e: No value passed for parameter 'formacao'

Este script confere as construcoes de `data class` do proprio projeto:
para cada `Nome(` com argumentos nomeados, os nomes existem? E os parametros
sem valor padrao, foram todos passados?

Limites assumidos de proposito (a alternativa seria escrever um parser Kotlin):
so entende chamada com argumentos NOMEADOS -- que e o estilo do projeto -- e
ignora a chamada que mistura posicional com nomeado.

    python3 docs/kt_argumentos_check.py [pasta]
"""
import pathlib
import re
import sys

RAIZ = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'src/main/kotlin')

DATA_CLASS = re.compile(r'^data class (\w+)\s*\(', re.MULTILINE)
PARAM = re.compile(r'^\s*(?:@\w+\s+)*(?:val|var)\s+(\w+)\s*:\s*(.+?)(,|$)')


def corta_parenteses(texto, inicio):
    """Devolve o conteudo do parenteses aberto em `inicio` e onde ele fecha."""
    nivel = 0
    for i in range(inicio, len(texto)):
        if texto[i] == '(':
            nivel += 1
        elif texto[i] == ')':
            nivel -= 1
            if nivel == 0:
                return texto[inicio + 1:i], i
    return '', len(texto)


def sem_comentarios(texto):
    """Tira // e /* */ para nao confundir a varredura."""
    fora, i, n = [], 0, len(texto)
    while i < n:
        if texto.startswith('//', i):
            j = texto.find('\n', i)
            i = n if j == -1 else j
        elif texto.startswith('/*', i):
            nivel, i = 1, i + 2
            while i < n and nivel:
                if texto.startswith('/*', i):
                    nivel += 1; i += 2
                elif texto.startswith('*/', i):
                    nivel -= 1; i += 2
                else:
                    i += 1
        else:
            fora.append(texto[i]); i += 1
    return ''.join(fora)


# ---- 1. catalogo dos data class do projeto -------------------------------

catalogo = {}  # nome -> (obrigatorios, todos)
arquivos = sorted(RAIZ.rglob('*.kt'))
for caminho in arquivos:
    texto = sem_comentarios(caminho.read_text(encoding='utf-8'))
    for m in DATA_CLASS.finditer(texto):
        corpo, _ = corta_parenteses(texto, m.end() - 1)
        todos, obrigatorios = [], []
        for linha in corpo.split('\n'):
            p = PARAM.match(linha)
            if not p:
                continue
            nome, tipo = p.group(1), p.group(2)
            todos.append(nome)
            if '=' not in tipo:
                obrigatorios.append(nome)
        if todos:
            catalogo[m.group(1)] = (obrigatorios, todos)

# ---- 2. confere as construcoes -------------------------------------------

falhas = []
CHAMADA = re.compile(r'\b([A-Z]\w+)\s*\(')

for caminho in arquivos:
    bruto = caminho.read_text(encoding='utf-8')
    texto = sem_comentarios(bruto)
    for m in CHAMADA.finditer(texto):
        nome = m.group(1)
        if nome not in catalogo:
            continue
        corpo, _ = corta_parenteses(texto, m.end() - 1)
        # so argumento nomeado: `x = ...` no nivel de topo do parenteses
        usados, nivel, atual = [], 0, []
        for ch in corpo:
            if ch in '([{':
                nivel += 1
            elif ch in ')]}':
                nivel -= 1
            if ch == ',' and nivel == 0:
                usados.append(''.join(atual)); atual = []
            else:
                atual.append(ch)
        usados.append(''.join(atual))

        nomes = []
        posicional = False
        for pedaco in usados:
            pedaco = pedaco.strip()
            if not pedaco:
                continue
            arg = re.match(r'^(\w+)\s*=(?!=)', pedaco)
            if arg:
                nomes.append(arg.group(1))
            else:
                posicional = True
        if posicional or not nomes:
            continue  # fora do escopo desta conferencia

        obrigatorios, todos = catalogo[nome]
        linha = texto[:m.start()].count('\n') + 1
        for usado in nomes:
            if usado not in todos:
                falhas.append((caminho, linha, nome, f"parametro inexistente: {usado}"))
        for faltando in obrigatorios:
            if faltando not in nomes:
                falhas.append((caminho, linha, nome, f"faltou o obrigatorio: {faltando}"))

for caminho, linha, tipo, problema in falhas:
    print(f"  !!  {caminho}:{linha}  {tipo}(...) — {problema}")

print(f"\n{len(catalogo)} data class conferido(s) — "
      f"{'TUDO OK' if not falhas else str(len(falhas)) + ' PROBLEMA(S)'}")
sys.exit(1 if falhas else 0)
