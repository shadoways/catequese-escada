"""
Auditoria estatica do Kotlin, alem dos dois verificadores existentes.

Motivacao: neste ambiente nao da para compilar (proxy bloqueia Gradle e
Maven Central). Os dois verificadores atuais pegam DUAS classes de erro
-- comentario aninhado e smart cast. Este script procura as classes de erro
que ja apareceram neste projeto ou que falham so na SUBIDA da aplicacao,
onde o diagnostico e pior.

O que ele checa:

1. METODO DE REPOSITORY INEXISTENTE. Spring Data deriva a query pelo nome do
   metodo. Se o nome nao existe na interface, o erro so aparece ao subir a
   aplicacao, como um "Could not create query for method" gigante. E o tipo
   de falha mais cara de diagnosticar.

2. IMPORT FALTANDO. Tipo do projeto usado num arquivo sem import e sem estar
   no mesmo pacote.

3. IMPORT NAO USADO. So aviso; nao quebra build (mas suja).

4. CAMPO INEXISTENTE EM DATA CLASS. Chamada com argumento nomeado que a data
   class nao declara.

5. PADRAO DE ROTA DENTRO DE COMENTARIO. Redundante com o kt_comment_check,
   mas aponta o local exato, e foi o erro entregue na F7.

Nao substitui o compilador. Nao valida tipos.
"""
import os
import re
import sys
from collections import defaultdict

if not os.path.isdir('src/main/kotlin'):
    raise SystemExit(
        "ERRO: rode na raiz do repositorio (onde existe src/main/kotlin). "
        "Diretorio atual: " + os.getcwd()
    )

RAIZ = 'src/main/kotlin'
arquivos = []
for base, _, nomes in os.walk(RAIZ):
    for n in nomes:
        if n.endswith('.kt'):
            arquivos.append(os.path.join(base, n))
arquivos.sort()

if not arquivos:
    raise SystemExit("ERRO: nenhum arquivo .kt encontrado.")


def sem_strings(texto):
    """
    Substitui o CONTEUDO de literais de string por espacos.

    Sem isto, ResourceNotFoundException("Turma nao encontrada") faz o
    verificador achar que o arquivo usa o tipo Turma sem import -- foi a
    causa de 18 falsos positivos na primeira versao. Interpolacao ${...} e
    preservada, porque ali dentro ha codigo de verdade.
    """
    saida = []
    i, n = 0, len(texto)
    while i < n:
        if texto.startswith('"""', i):
            j = texto.find('"""', i + 3)
            j = n if j == -1 else j + 3
            saida.append(' ' * (j - i))
            i = j
            continue
        if texto[i] == '"':
            saida.append(' ')
            i += 1
            while i < n and texto[i] != '"':
                if texto[i] == '\\':
                    saida.append('  ')
                    i += 2
                    continue
                if texto.startswith('${', i):
                    prof, j = 0, i + 1
                    while j < n:
                        if texto[j] == '{':
                            prof += 1
                        elif texto[j] == '}':
                            prof -= 1
                            if prof == 0:
                                break
                        j += 1
                    saida.append(texto[i:j + 1])
                    i = j + 1
                    continue
                saida.append(' ' if texto[i] != '\n' else '\n')
                i += 1
            saida.append(' ')
            i += 1
            continue
        saida.append(texto[i])
        i += 1
    return ''.join(saida)


def args_de_nivel_1(texto, inicio_paren):
    """
    Argumentos do PROPRIO parenteses, ignorando chamadas aninhadas.

    A primeira versao coletava os argumentos nomeados de tudo que estivesse
    dentro do parenteses externo, inclusive de outros construtores aninhados
    -- 26 falsos positivos. Aqui so entra o que esta em profundidade 1.
    """
    prof, i, n = 0, inicio_paren, len(texto)
    pedacos, atual = [], []
    while i < n:
        c = texto[i]
        if c in '([{':
            prof += 1
            if prof > 1:
                atual.append(c)
        elif c in ')]}':
            prof -= 1
            if prof == 0:
                break
            atual.append(c)
        elif c == ',' and prof == 1:
            pedacos.append(''.join(atual))
            atual = []
        else:
            if prof >= 1:
                atual.append(c)
        i += 1
    pedacos.append(''.join(atual))

    nomes = []
    for pedaco in pedacos:
        m = re.match(r'\s*(\w+)\s*=(?!=)', pedaco)
        if m:
            nomes.append(m.group(1))
    return nomes


def sem_comentarios(texto):
    """Remove comentarios de bloco e de linha, respeitando aninhamento."""
    saida = []
    i, n, prof = 0, len(texto), 0
    while i < n:
        if texto.startswith('/*', i):
            prof += 1
            i += 2
            continue
        if texto.startswith('*/', i) and prof > 0:
            prof -= 1
            i += 2
            continue
        if prof == 0 and texto.startswith('//', i):
            j = texto.find('\n', i)
            i = n if j == -1 else j
            continue
        if prof == 0:
            saida.append(texto[i])
        elif texto[i] == '\n':
            saida.append('\n')
        i += 1
    return ''.join(saida)


fontes = {}
for caminho in arquivos:
    bruto = open(caminho, encoding='utf-8').read()
    fontes[caminho] = {'bruto': bruto, 'codigo': sem_strings(sem_comentarios(bruto))}

# ---- Mapa de tipos declarados no projeto -----------------------------------
tipo_para_pacote = {}
pacote_do_arquivo = {}
metodos_de_interface = defaultdict(set)
campos_de_data_class = {}

DECL = re.compile(
    r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public\s+|internal\s+|private\s+)?'
    r'(?:data\s+|sealed\s+|abstract\s+|open\s+|enum\s+)*'
    r'(class|interface|object)\s+(\w+)', re.M)

for caminho, dados in fontes.items():
    codigo = dados['codigo']
    m = re.search(r'^package\s+([\w.]+)', codigo, re.M)
    pacote = m.group(1) if m else ''
    pacote_do_arquivo[caminho] = pacote
    for tipo, nome in DECL.findall(codigo):
        tipo_para_pacote[nome] = pacote

# Metodos declarados em cada interface de repositorio.
for caminho, dados in fontes.items():
    codigo = dados['codigo']
    for m in re.finditer(r'interface\s+(\w+)\s*:\s*JpaRepository', codigo):
        nome_repo = m.group(1)
        corpo = codigo[m.end():]
        # Metodos herdados do JpaRepository/CrudRepository.
        herdados = {
            'findAll', 'findById', 'save', 'saveAll', 'delete', 'deleteById',
            'deleteAll', 'count', 'existsById', 'flush', 'saveAndFlush',
            'getReferenceById', 'findAllById', 'deleteAllById'
        }
        proprios = set(re.findall(r'\bfun\s+(\w+)', corpo))
        metodos_de_interface[nome_repo] = herdados | proprios

# Campos de cada data class (para checar argumento nomeado).
for caminho, dados in fontes.items():
    codigo = dados['codigo']
    for m in re.finditer(r'data\s+class\s+(\w+)\s*\(', codigo):
        nome = m.group(1)
        i = m.end() - 1
        prof, j = 0, i
        while j < len(codigo):
            if codigo[j] == '(':
                prof += 1
            elif codigo[j] == ')':
                prof -= 1
                if prof == 0:
                    break
            j += 1
        params = codigo[i + 1:j]
        campos = set(re.findall(r'\b(?:val|var)\s+(\w+)\s*:', params))
        # Chave por pacote+nome: existem tipos homonimos em pacotes vizinhos
        # (foi assim que 'DocumentoStatusDTO' apareceu duas vezes). Guardar so
        # pelo nome simples fazia um sobrescrever o outro e gerar 3 falsos
        # positivos -- mas tambem revelou a colisao, que era um problema real.
        campos_de_data_class[(pacote_do_arquivo[caminho], nome)] = campos

problemas = []
avisos = []

# ---- 1. Metodo de repository inexistente -----------------------------------
for caminho, dados in fontes.items():
    codigo = dados['codigo']
    # Propriedades que sao repositorios: nome: XxxRepository
    repos_locais = dict(re.findall(r'(\w+)\s*:\s*(\w*Repository)\b', codigo))
    for var, tipo_repo in repos_locais.items():
        conhecidos = metodos_de_interface.get(tipo_repo)
        if not conhecidos:
            continue
        for m in re.finditer(re.escape(var) + r'\.(\w+)\s*\(', codigo):
            metodo = m.group(1)
            if metodo not in conhecidos:
                linha = codigo[:m.start()].count('\n') + 1
                problemas.append(
                    f"{caminho}:{linha}: {var}.{metodo}(...) NAO existe em "
                    f"{tipo_repo}. Spring Data so falha ao SUBIR a aplicacao."
                )

# ---- 2 e 3. Imports --------------------------------------------------------
for caminho, dados in fontes.items():
    codigo = dados['codigo']
    pacote = pacote_do_arquivo[caminho]
    importados = dict(
        (linha.rsplit('.', 1)[1], linha)
        for linha in re.findall(r'^import\s+([\w.]+)', codigo, re.M)
        if '.' in linha
    )
    corpo = re.sub(r'^package\s+[\w.]+', '', codigo, flags=re.M)
    corpo = re.sub(r'^import\s+[\w.]+.*$', '', corpo, flags=re.M)

    usados = set(re.findall(r'\b([A-Z]\w+)\b', corpo))
    declarados_aqui = set(nome for _, nome in DECL.findall(codigo))

    for nome in sorted(usados):
        if nome in declarados_aqui or nome in importados:
            continue
        dono = tipo_para_pacote.get(nome)
        if dono and dono != pacote:
            linha_uso = corpo[:corpo.find(nome)].count('\n') + 1
            problemas.append(
                f"{caminho}: usa '{nome}' (de {dono}) SEM import."
            )

    for nome, caminho_import in importados.items():
        if not re.search(r'\b' + re.escape(nome) + r'\b', corpo):
            avisos.append(f"{caminho}: import nao usado -> {caminho_import}")

# ---- 4. Argumento nomeado inexistente em data class ------------------------
for caminho, dados in fontes.items():
    codigo = dados['codigo']
    pacote_deste = pacote_do_arquivo[caminho]
    importados_aqui = set(re.findall(r'^import\s+([\w.]+)', codigo, re.M))
    for (pacote_dc, nome_dc), campos in campos_de_data_class.items():
        if not campos:
            continue
        # So checa o tipo que ESTE arquivo realmente enxerga.
        visivel = (pacote_dc == pacote_deste or
                   f"{pacote_dc}.{nome_dc}" in importados_aqui)
        if not visivel:
            continue
        for m in re.finditer(r'\b' + re.escape(nome_dc) + r'\s*\(', codigo):
            for nomeado in args_de_nivel_1(codigo, m.end() - 1):
                if nomeado not in campos:
                    linha = codigo[:m.start()].count('\n') + 1
                    problemas.append(
                        f"{caminho}:{linha}: {nome_dc}(...) recebe '{nomeado} =' "
                        f"mas a data class nao tem esse campo."
                    )

# ---- 5. Padrao de rota dentro de comentario de bloco -----------------------
for caminho, dados in fontes.items():
    bruto = dados['bruto']
    for i, linha in enumerate(bruto.split('\n'), 1):
        if '/*' in linha.replace('/**', '').replace('/*', '', 0):
            pass
    # Procura barra-asterisco em linha que comeca com * (interior de KDoc).
    dentro = False
    for i, linha in enumerate(bruto.split('\n'), 1):
        stripped = linha.strip()
        if stripped.startswith('/*'):
            dentro = True
        if dentro and re.search(r'/\*', stripped[2:] if stripped.startswith('/*') else stripped):
            problemas.append(
                f"{caminho}:{i}: barra-asterisco DENTRO de comentario de bloco. "
                f"Em Kotlin comentario aninha e o arquivo para de compilar."
            )
        if '*/' in stripped:
            dentro = False

print(f"Arquivos analisados: {len(arquivos)}")
print(f"Data classes mapeadas: {len(campos_de_data_class)}")
print(f"Repositorios mapeados: {len(metodos_de_interface)}")
print()

for p in problemas:
    print("PROBLEMA  " + p)
print()
for a in avisos[:40]:
    print("aviso     " + a)
if len(avisos) > 40:
    print(f"aviso     ... e mais {len(avisos) - 40}")

print()
print(f"{len(problemas)} problema(s), {len(avisos)} aviso(s).")
sys.exit(1 if problemas else 0)
