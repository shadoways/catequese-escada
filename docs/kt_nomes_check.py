"""Nome de tipo declarado duas vezes no mesmo pacote Kotlin.

A armadilha: `FormacaoDetalheDTO` ja existia em AgendaDTO.kt como "o detalhe de
UMA formacao", e eu criei outro com o mesmo nome em IndicadoresDetalheDTO.kt
como "a tela de Formacao do relatorio". Mesmo pacote, mesmo nome:

    e: AgendaDTO.kt:149:12 Redeclaration: FormacaoDetalheDTO

E o erro seguinte e pior, porque nao fala de nome nenhum: o compilador escolhe
uma das duas declaracoes e reclama de PARAMETRO INEXISTENTE em quem usava a
outra --

    e: IndicadoresDetalheService.kt:456 Cannot find a parameter with this name: participaram

...o que manda quem le procurar erro de digitacao num arquivo que estava certo.

Como o Gradle nao roda no sandbox, este script e a conferencia possivel daqui.

    python3 docs/kt_nomes_check.py [pasta]
"""
import collections
import pathlib
import re
import sys

RAIZ = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'src/main/kotlin')

# Declaracao de topo: sem indentacao antes da palavra-chave. O que esta dentro
# de uma classe (aninhado, e portanto indentado) nao colide no pacote.
DECLARACAO = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public\s+|internal\s+|private\s+|sealed\s+|abstract\s+|open\s+|data\s+|value\s+|annotation\s+)*'
    r'(?:class|interface|object|enum\s+class)\s+([A-Za-z_]\w*)',
    re.MULTILINE
)
PACOTE = re.compile(r'^package\s+([\w.]+)', re.MULTILINE)


def declaracoes(texto):
    """Nomes declarados no nivel de topo, com a linha de cada um."""
    achados = []
    for linha_num, linha in enumerate(texto.split('\n'), start=1):
        if linha[:1].isspace():
            continue  # aninhado: nao concorre no pacote
        m = DECLARACAO.match(linha)
        if m:
            achados.append((m.group(1), linha_num))
    return achados


por_pacote = collections.defaultdict(list)
arquivos = sorted(RAIZ.rglob('*.kt'))
for caminho in arquivos:
    texto = caminho.read_text(encoding='utf-8')
    m = PACOTE.search(texto)
    pacote = m.group(1) if m else '(sem pacote)'
    for nome, linha in declaracoes(texto):
        por_pacote[(pacote, nome)].append((caminho, linha))

falhas = [(chave, ondes) for chave, ondes in por_pacote.items() if len(ondes) > 1]
for (pacote, nome), ondes in sorted(falhas):
    print(f"  !!  {nome} declarado {len(ondes)}x em {pacote}:")
    for caminho, linha in ondes:
        print(f"        {caminho}:{linha}")

print(f"\n{len(arquivos)} arquivo(s), {len(por_pacote)} tipo(s) — "
      f"{'SEM COLISAO' if not falhas else str(len(falhas)) + ' NOME(S) REPETIDO(S)'}")
sys.exit(1 if falhas else 0)
