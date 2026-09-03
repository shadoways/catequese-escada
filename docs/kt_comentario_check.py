"""Comentario de bloco em Kotlin ANINHA. Este script acha quem nao fecha.

A armadilha que motivou o script: escrever uma rota com curinga dentro de um
KDoc --

    /**
     * A regra esta em /api/indicadores/**
     */

-- a sequencia barra-asterisco-asterisco no meio do texto abre um comentario
ANINHADO (diferente de Java e de C, onde nao aninha). Dali em diante o arquivo
inteiro vira comentario, e o compilador so reclama na ultima linha:

    e: IndicadoresController.kt:36:1 Unclosed comment
    e: IndicadoresController.kt:5:43 Unresolved reference: IndicadoresService

O primeiro erro aponta o FIM do arquivo e o segundo aponta um import que esta
correto -- nenhum dos dois aponta a linha do problema. Dai valer um teste.

    python3 docs/kt_comentario_check.py [pasta]
"""
import pathlib
import sys

RAIZ = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'src/main/kotlin')


def conferir(texto):
    """Devolve (profundidade_final, linha_do_ultimo_abre_sem_par)."""
    i, linha, profundidade = 0, 1, 0
    abertos = []
    n = len(texto)
    while i < n:
        c = texto[i]
        prox = texto[i + 1] if i + 1 < n else ''

        if c == '\n':
            linha += 1
            i += 1
            continue

        if profundidade > 0:
            if c == '/' and prox == '*':
                profundidade += 1
                abertos.append(linha)
                i += 2
                continue
            if c == '*' and prox == '/':
                profundidade -= 1
                abertos.pop()
                i += 2
                continue
            i += 1
            continue

        # --- fora de comentario ---
        if c == '/' and prox == '/':
            while i < n and texto[i] != '\n':
                i += 1
            continue
        if c == '/' and prox == '*':
            profundidade = 1
            abertos.append(linha)
            i += 2
            continue
        if texto.startswith('"""', i):
            fim = texto.find('"""', i + 3)
            trecho = texto[i:(fim + 3) if fim != -1 else n]
            linha += trecho.count('\n')
            i = (fim + 3) if fim != -1 else n
            continue
        if c == '"' or c == "'":
            aspa = c
            i += 1
            while i < n and texto[i] != aspa:
                if texto[i] == '\\':
                    i += 1
                if i < n and texto[i] == '\n':
                    linha += 1
                i += 1
            i += 1
            continue
        i += 1

    return profundidade, (abertos[0] if abertos else None)


falhas = []
arquivos = sorted(RAIZ.rglob('*.kt'))
for caminho in arquivos:
    profundidade, linha = conferir(caminho.read_text(encoding='utf-8'))
    if profundidade:
        falhas.append((caminho, profundidade, linha))
        print(f"  !!  {caminho}: {profundidade} comentario(s) sem fechar; "
              f"o primeiro abre na linha {linha}")

print(f"\n{len(arquivos)} arquivo(s) conferido(s) — "
      f"{'TODOS OK' if not falhas else str(len(falhas)) + ' COM PROBLEMA'}")
sys.exit(1 if falhas else 0)
