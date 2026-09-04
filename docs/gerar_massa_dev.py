"""Gera sql/dados-dev/MASSA_DEV.sql -- a massa de teste do banco de desenvolvimento.

POR QUE UM GERADOR, E NAO SQL ESCRITO A MAO: sao milhares de linhas com
integridade referencial entre nove tabelas. A mao, um id errado so aparece
quando a tela quebra. Aqui os ids sao objetos Python, a conferencia de
integridade roda ANTES de emitir, e a distribuicao de frequencia e construida
para cair nas faixas certas em vez de sair no acaso.

Determinista (semente fixa): rodar de novo produz exatamente o mesmo arquivo,
entao o diff do git mostra so o que eu mudei de proposito.

    python3 docs/gerar_massa_dev.py
"""
import datetime
import pathlib
import random
import sys
from collections import defaultdict

random.seed(20260904)

# Todo id da massa comeca aqui. E o que permite o bloco COMO DESFAZER apagar
# exatamente o que este script criou, sem tocar em nada que o Gabriel cadastrou
# pela tela.
BASE = 900000

ANO_ATUAL = 2026
ANO_ANTERIOR = 2025
HOJE = datetime.date(2026, 9, 4)

MINIMO = 80      # CalculoFrequencia.MINIMO_PADRAO
ALERTA = 85      # Configuracao.FREQUENCIA_AVISO_PADRAO

# Nome da comunidade conforme o Gabriel listou. "Nossa Senhora Aparecida" foi
# lido como UM nome: as outras seguem o mesmo padrao ("Nossa Senhora da/das X"),
# e "Nossa Senhora" sozinha nao e nome de comunidade. Se forem duas, e so
# separar aqui e rodar de novo.
COMUNIDADES = [
    "Matriz",
    "Nossa Senhora Aparecida",
    "Nossa Senhora das Dores",
    "Nossa Senhora da Esperanca",
    "Santo Antonio",
    "Sao Jose",
]

CATEQUISTAS_REAIS = [
    "Gabriel", "Stela", "Marcio", "Rodrigo", "Sebastiao",
    "Ana", "Richardson", "Tamiris",
]

NOMES_INVENTADOS = [
    "Adriana Prado", "Alexandre Bittencourt", "Aline Moretti", "Antonio Vilela",
    "Beatriz Camargo", "Bruno Sarmento", "Carla Nogueira", "Cesar Toledo",
    "Cristiane Bastos", "Daniel Furtado", "Denise Aragao", "Eduardo Peixoto",
    "Elaine Vasques", "Fabio Rezende", "Fernanda Quirino", "Geraldo Amancio",
    "Helena Portugal", "Igor Sampaio", "Isabel Trindade", "Joana Pimentel",
    "Leandro Cavalcante", "Lucia Andrade", "Marcelo Bandeira", "Mariana Teixeira",
    "Nilton Barroso", "Patricia Salgado", "Paulo Queiroz", "Renata Fontoura",
    "Roberto Guimaraes", "Silvana Machado", "Tadeu Monteiro", "Vera Lucia Coelho",
]

PRENOMES = [
    "Ana", "Beatriz", "Carlos", "Daniela", "Eduardo", "Fernanda", "Gabriel",
    "Helena", "Igor", "Julia", "Kaique", "Larissa", "Matheus", "Natalia",
    "Otavio", "Paula", "Rafael", "Sofia", "Thiago", "Valentina", "Yasmin",
    "Bruno", "Camila", "Diego", "Elisa", "Felipe", "Giovana", "Heitor",
    "Isadora", "Joao", "Leticia", "Miguel", "Nicole", "Pedro", "Rebeca",
    "Samuel", "Tatiana", "Vinicius", "Alice", "Bernardo", "Clara", "Davi",
]
SOBRENOMES = [
    "Almeida", "Barbosa", "Cardoso", "Duarte", "Esteves", "Ferreira", "Gomes",
    "Henriques", "Ibrahim", "Jesus", "Klein", "Lima", "Mendes", "Nunes",
    "Oliveira", "Pereira", "Quintana", "Ramos", "Santos", "Tavares", "Ubaldo",
    "Vieira", "Xavier", "Zanetti", "Correia", "Dias", "Freitas", "Guedes",
]

# (categoria, rotulo da turma, quantos anos de percurso)
CATEGORIAS = [
    ("PRE_CATEQUESE", "Pre-catequese"),
    ("EUCARISTIA", "Eucaristia"),
    ("CRISMA", "Crisma"),
    ("ADULTOS", "Adultos"),
    ("CATECUMENATO", "Catecumenato"),
    ("PERSEVERANCA", "Perseveranca"),
]


def esc(texto):
    return texto.replace("'", "''")


def nome_pessoa(usados):
    while True:
        n = f"{random.choice(PRENOMES)} {random.choice(SOBRENOMES)}"
        if n not in usados:
            usados.add(n)
            return n


class Massa:
    def __init__(self):
        self.linhas = defaultdict(list)   # tabela -> lista de tuplas de valores
        self.proximo = defaultdict(lambda: BASE + 1)

    def novo_id(self, tabela):
        i = self.proximo[tabela]
        self.proximo[tabela] = i + 1
        return i

    def add(self, tabela, valores):
        self.linhas[tabela].append(valores)


m = Massa()

# ---------------------------------------------------------------- comunidades
comunidades = []
for nome in COMUNIDADES:
    idc = m.novo_id("tb_comunidade")
    comunidades.append({"id": idc, "nome": nome})
    m.add("tb_comunidade", (idc, nome, f"Comunidade {nome}", 1))

# ---------------------------------------------------------------- catequistas
catequistas = []
usados = set()
for nome in CATEQUISTAS_REAIS + NOMES_INVENTADOS:
    idk = m.novo_id("tb_catequista")
    usados.add(nome)
    com = comunidades[len(catequistas) % len(comunidades)]
    nasc = datetime.date(random.randint(1965, 1998), random.randint(1, 12), random.randint(1, 28))
    inicio = datetime.date(random.randint(2012, 2024), random.randint(1, 12), random.randint(1, 28))
    catequistas.append({"id": idk, "nome": nome, "comunidade": com})
    m.add("tb_catequista", (
        idk, nome,
        f"(11) 9{random.randint(1000, 9999)}-{random.randint(1000, 9999)}",
        f"catequista{idk}@exemplo.org", None, nasc, inicio, 1,
    ))

# --------------------------------------------------------------------- turmas
# Cada comunidade tem um conjunto de turmas que se repete nos dois anos, para a
# comparacao ano a ano falar das MESMAS turmas. A Matriz, por ser a maior, tem
# uma turma a mais.
turmas = []          # todas, dos dois anos
turmas_por_ano = defaultdict(list)

def cria_turmas(ano):
    for com in comunidades:
        grade = [("EUCARISTIA", 1), ("CRISMA", 1), ("CRISMA", 2)]
        if com["nome"] == "Matriz":
            grade += [("ADULTOS", 1), ("CATECUMENATO", 1), ("PRE_CATEQUESE", 1)]
        elif com["nome"] in ("Sao Jose", "Nossa Senhora Aparecida"):
            grade += [("ADULTOS", 1)]
        elif com["nome"] == "Santo Antonio":
            grade += [("PERSEVERANCA", 1)]

        for categoria, etapa in grade:
            rotulo = dict(CATEGORIAS)[categoria]
            nome = f"{rotulo} {etapa} - {com['nome']} {ano}" if categoria == "CRISMA" \
                else f"{rotulo} - {com['nome']} {ano}"
            idt = m.novo_id("tb_turma")
            # O responsavel principal e um catequista daquela comunidade; a
            # equipe leva mais um, porque turma com um catequista so nao
            # sobrevive a uma gripe.
            daqui = [k for k in catequistas if k["comunidade"]["id"] == com["id"]]
            principal = random.choice(daqui)
            auxiliar = random.choice([k for k in catequistas if k["id"] != principal["id"]])
            t = {
                "id": idt, "nome": nome, "ano": ano, "categoria": categoria,
                "etapa": etapa if categoria == "CRISMA" else None,
                "comunidade": com, "principal": principal, "auxiliar": auxiliar,
            }
            turmas.append(t)
            turmas_por_ano[ano].append(t)
            m.add("tb_turma", (
                idt, nome, f"Turma de {rotulo} da comunidade {com['nome']}",
                ano, rotulo, principal["id"], categoria, t["etapa"], com["id"],
            ))
            m.add("tb_turma_catequista", (
                m.novo_id("tb_turma_catequista"), idt, principal["id"], 1,
                f"{ano}-02-10 19:00:00",
            ))
            m.add("tb_turma_catequista", (
                m.novo_id("tb_turma_catequista"), idt, auxiliar["id"], 0,
                f"{ano}-02-10 19:00:00",
            ))

cria_turmas(ANO_ANTERIOR)
cria_turmas(ANO_ATUAL)

# -------------------------------------------------------------- catequisandos
# Uma pessoa e criada UMA vez e pode ter matricula nos dois anos. E isso que
# faz "entraram / permaneceram / sairam" significar alguma coisa: se cada ano
# tivesse gente nova, a retencao seria sempre zero.
catequisandos = []
nomes_usados = set()

def novo_catequisando(com, categoria):
    idc = m.novo_id("tb_catequisando")
    nome = nome_pessoa(nomes_usados)
    idade = {
        "PRE_CATEQUESE": (6, 8), "EUCARISTIA": (9, 11), "CRISMA": (12, 16),
        "ADULTOS": (19, 55), "CATECUMENATO": (18, 60), "PERSEVERANCA": (16, 19),
    }[categoria]
    nascimento = datetime.date(
        ANO_ATUAL - random.randint(*idade), random.randint(1, 12), random.randint(1, 28)
    )
    batizado = 0 if categoria == "CATECUMENATO" else 1
    eucaristia = 1 if categoria in ("CRISMA", "PERSEVERANCA") else 0
    pessoa = {"id": idc, "nome": nome, "comunidade": com}
    catequisandos.append(pessoa)
    m.add("tb_catequisando", (
        idc, nome,
        f"(11) 9{random.randint(1000, 9999)}-{random.randint(1000, 9999)}",
        None, nascimento,
        f"Responsavel de {nome.split()[0]}" if idade[1] <= 17 else None,
        f"(11) 9{random.randint(1000, 9999)}-{random.randint(1000, 9999)}" if idade[1] <= 17 else None,
        batizado, eucaristia, 1, com["id"],
    ))
    return pessoa

# ------------------------------------------------------------------ matriculas
matriculas = []       # dicts: pessoa, turma, ano, situacao
por_pessoa_ano = {}

def matricular(pessoa, turma, ano, situacao, dia=None):
    idm = m.novo_id("tb_matricula")
    data = dia or datetime.date(ano, random.randint(2, 3), random.randint(1, 28))
    reg = {"id": idm, "pessoa": pessoa, "turma": turma, "ano": ano, "situacao": situacao}
    matriculas.append(reg)
    por_pessoa_ano[(pessoa["id"], ano)] = reg
    m.add("tb_matricula", (
        idm, pessoa["id"], turma["id"], ano, data, situacao, None,
        f"{data} 20:00:00", f"{data} 20:00:00", "script-massa",
    ))
    return reg

# --- 2025: o ano fechado -----------------------------------------------------
# Situacoes de ano encerrado: a maioria concluiu, alguns nao concluiram,
# alguns desistiram e um punhado foi transferido. E essa mistura que da
# conteudo para "quem saiu" e para a evasao.
for turma in turmas_por_ano[ANO_ANTERIOR]:
    quantos = random.randint(10, 20)
    for _ in range(quantos):
        pessoa = novo_catequisando(turma["comunidade"], turma["categoria"])
        sorte = random.random()
        if turma["categoria"] in ("PRE_CATEQUESE", "PERSEVERANCA"):
            situacao = "CONCLUIDO" if sorte < 0.90 else "DESISTENTE"
        elif sorte < 0.70:
            situacao = "CONCLUIDO"
        elif sorte < 0.84:
            situacao = "NAO_CONCLUIDO"
        elif sorte < 0.95:
            situacao = "DESISTENTE"
        else:
            situacao = "TRANSFERIDO"
        matricular(pessoa, turma, ANO_ANTERIOR, situacao)

# --- 2026: o ano em curso ----------------------------------------------------
# Quem continua: quem NAO concluiu o percurso inteiro e nao desistiu. Crisma 1
# vira Crisma 2; Eucaristia concluida sai (foi para a Crisma no ano seguinte ou
# terminou o percurso).
def turma_seguinte(turma):
    alvo = None
    if turma["categoria"] == "CRISMA" and turma["etapa"] == 1:
        alvo = ("CRISMA", 2)
    elif turma["categoria"] == "EUCARISTIA":
        alvo = ("CRISMA", 1)
    elif turma["categoria"] in ("ADULTOS", "CATECUMENATO"):
        alvo = (turma["categoria"], turma["etapa"])
    if alvo is None:
        return None
    for t in turmas_por_ano[ANO_ATUAL]:
        if (t["comunidade"]["id"] == turma["comunidade"]["id"]
                and t["categoria"] == alvo[0] and t["etapa"] == alvo[1]):
            return t
    return None

continuaram = 0
for reg in [r for r in matriculas if r["ano"] == ANO_ANTERIOR]:
    if reg["situacao"] in ("DESISTENTE", "TRANSFERIDO"):
        continue
    seguinte = turma_seguinte(reg["turma"])
    if seguinte is None:
        continue
    # 82% de quem podia continuar continua. O resto vira "abandonou" no
    # movimento -- que e exatamente o numero que a tela precisa mostrar.
    if random.random() < 0.82:
        matricular(reg["pessoa"], seguinte, ANO_ATUAL, "CURSANDO")
        continuaram += 1

# Entradas novas ate encher a turma
for turma in turmas_por_ano[ANO_ATUAL]:
    ja = sum(1 for r in matriculas if r["ano"] == ANO_ATUAL and r["turma"]["id"] == turma["id"])
    alvo = random.randint(10, 20)
    for _ in range(max(0, alvo - ja)):
        pessoa = novo_catequisando(turma["comunidade"], turma["categoria"])
        matricular(pessoa, turma, ANO_ATUAL, "CURSANDO")

# Alguem sempre desiste no meio do ano em curso.
for reg in [r for r in matriculas if r["ano"] == ANO_ATUAL]:
    if random.random() < 0.045:
        reg["situacao"] = "DESISTENTE"
        m.linhas["tb_matricula"] = [
            (l[:5] + ("DESISTENTE",) + l[6:]) if l[0] == reg["id"] else l
            for l in m.linhas["tb_matricula"]
        ]

# ------------------------------------------------------- etapas do catecumeno
ETAPAS = ["PRE_CATECUMENATO", "CATECUMENATO", "PURIFICACAO_ILUMINACAO", "MISTAGOGIA"]
for reg in matriculas:
    if reg["turma"]["categoria"] != "CATECUMENATO":
        continue
    etapa = ETAPAS[min(3, random.randint(0, 2))]
    m.add("tb_etapa_catecumeno", (
        m.novo_id("tb_etapa_catecumeno"), reg["pessoa"]["id"], etapa,
        datetime.date(reg["ano"], 3, 1), None, None, "script-massa",
        f"{reg['ano']}-03-01 20:00:00",
    ))

# ---------------------------------------------------- encontros e presencas
# A frequencia e construida por FAIXA, nao no acaso: a tela precisa mostrar
# gente regular, gente perto do limite (entre 80% e 85%) e gente abaixo do
# minimo. Sorteio puro concentraria tudo no meio e as tres faixas nunca
# apareceriam juntas.
FAIXAS = [
    (0.62, 0.90, 1.00),   # regular folgado
    (0.16, 0.80, 0.849),  # perto do limite: passou do minimo, mas nao do alerta
    (0.14, 0.55, 0.799),  # abaixo do minimo
    (0.08, 0.86, 0.95),   # regular apertado
]

def sorteia_faixa():
    r = random.random()
    acumulado = 0.0
    for peso, lo, hi in FAIXAS:
        acumulado += peso
        if r <= acumulado:
            return random.uniform(lo, hi)
    return random.uniform(0.85, 1.0)

def datas_de_encontro(ano, quantos):
    # Sabados, a partir de marco.
    d = datetime.date(ano, 3, 1)
    while d.weekday() != 5:
        d += datetime.timedelta(days=1)
    datas = []
    while len(datas) < quantos:
        if d <= (HOJE if ano == ANO_ATUAL else datetime.date(ano, 11, 30)):
            datas.append(d)
        d += datetime.timedelta(days=7)
        if d.year != ano:
            break
    return datas

for turma in turmas:
    categoria = turma["categoria"]
    if categoria in ("PRE_CATEQUESE", "PERSEVERANCA"):
        continue  # nao apura frequencia; sem encontro fechado, nada a contar
    quantos = 20 if turma["ano"] == ANO_ANTERIOR else 12
    datas = datas_de_encontro(turma["ano"], quantos)
    inscritos = [r for r in matriculas
                 if r["ano"] == turma["ano"] and r["turma"]["id"] == turma["id"]]
    metas = {r["pessoa"]["id"]: sorteia_faixa() for r in inscritos}
    contagem = defaultdict(int)

    for i, data in enumerate(datas):
        cancelado = (i == 7 and random.random() < 0.35)
        idenc = m.novo_id("tb_encontro")
        situacao = "CANCELADO" if cancelado else "FECHADO"
        m.add("tb_encontro", (
            idenc, turma["id"], data, f"Encontro {i + 1}", situacao,
            "Feriado na comunidade" if cancelado else None, None,
            turma["principal"]["nome"], f"{data} 15:00:00",
            None if cancelado else turma["principal"]["nome"],
            None if cancelado else f"{data} 17:00:00", 0,
        ))
        if cancelado:
            continue  # encontro cancelado nao entra na conta de ninguem

        for reg in inscritos:
            pid = reg["pessoa"]["id"]
            # Desistente para de aparecer da metade do ano em diante.
            if reg["situacao"] == "DESISTENTE" and i > len(datas) // 2:
                estado = "FALTA"
            else:
                falta_justificada = random.random() < 0.05
                if falta_justificada:
                    estado = "JUSTIFICADA"
                else:
                    contagem[pid] += 1
                    estado = "PRESENTE" if random.random() < metas[pid] else "FALTA"
            m.add("tb_presenca", (
                m.novo_id("tb_presenca"), data,
                1 if estado == "PRESENTE" else 0, pid, idenc, estado,
                "Atestado medico" if estado == "JUSTIFICADA" else None,
                turma["principal"]["nome"], f"{data} 17:00:00",
            ))

# -------------------------------------------------------------------- eventos
eventos = []

def novo_evento(titulo, tipo, nivel, data, ano, situacao="REALIZADO",
                id_comunidade=None, id_turma=None, id_formacao=None, local="Salao paroquial"):
    ide = m.novo_id("tb_evento")
    e = {"id": ide, "tipo": tipo, "nivel": nivel, "data": data, "ano": ano}
    eventos.append(e)
    m.add("tb_evento", (
        ide, titulo, nivel, None, None, data, None, local, tipo,
        id_comunidade, id_turma, id_formacao, "19h30", situacao,
        "Chuva forte" if situacao == "CANCELADO" else None,
        "script-massa", f"{data} 09:00:00",
    ))
    return e

# ------------------------------------------------------------------ formacoes
# Tres niveis por ano, com participacao propositalmente desigual: a diocesana
# tem muito inscrito e pouca presenca, que e o caso que a tela precisa gritar.
formacoes = []
for ano in (ANO_ANTERIOR, ANO_ATUAL):
    for nivel, nome, encontros, alvo_presenca in [
        ("DIOCESANO", f"Formacao diocesana {ano}", 4, 0.30),
        ("REGIONAL", f"Formacao regional {ano}", 5, 0.62),
        ("PAROQUIAL", f"Escola de catequistas {ano}", 8, 0.86),
    ]:
        idf = m.novo_id("tb_formacao")
        f = {"id": idf, "nivel": nivel, "ano": ano, "alvo": alvo_presenca}
        formacoes.append(f)
        m.add("tb_formacao", (
            idf, nome, nivel, ano,
            f"Formacao de catequistas, nivel {nivel.lower()}.", 80, "ENCERRADA"
            if ano == ANO_ANTERIOR else "ABERTA", "script-massa",
            f"{ano}-02-01 10:00:00",
        ))

        # Quem se inscreve: na diocesana entra quase todo mundo; na paroquial,
        # os que atuam de fato.
        proporcao = {"DIOCESANO": 1.0, "REGIONAL": 0.55, "PAROQUIAL": 0.85}[nivel]
        inscritos = random.sample(catequistas, int(len(catequistas) * proporcao))
        for k in inscritos:
            m.add("tb_formacao_inscrito", (
                m.novo_id("tb_formacao_inscrito"), idf, k["id"],
                f"{ano}-02-15 20:00:00",
            ))

        # Cada catequista tem uma propensao propria: assim o ranking "quem foi
        # e quem nao foi" tem topo e fundo, em vez de todo mundo no meio.
        propensao = {k["id"]: max(0.0, min(1.0, random.gauss(alvo_presenca, 0.28)))
                     for k in inscritos}
        # Quem se inscreveu e NUNCA foi. E o caso que a tela precisa gritar, e
        # sorteio gaussiano quase nunca produz zero -- se todo inscrito aparece
        # ao menos uma vez, "participaram" fica igual a "inscritos" e a coluna
        # nao diz nada.
        faltosos = {"DIOCESANO": 0.32, "REGIONAL": 0.14, "PAROQUIAL": 0.06}[nivel]
        for k in random.sample(inscritos, int(len(inscritos) * faltosos)):
            propensao[k["id"]] = 0.0

        datas = datas_de_encontro(ano, encontros)
        for i, data in enumerate(datas):
            ev = novo_evento(
                f"{nome} - encontro {i + 1}", "FORMACAO", nivel, data, ano,
                situacao="REALIZADO", id_formacao=idf,
            )
            for k in inscritos:
                if random.random() < propensao[k["id"]]:
                    estado = "PRESENTE"
                elif random.random() < 0.15:
                    estado = "JUSTIFICADA"
                else:
                    estado = "FALTA"
                m.add("tb_presenca_formacao", (
                    m.novo_id("tb_presenca_formacao"), ev["id"], k["id"], estado,
                    "Trabalho" if estado == "JUSTIFICADA" else None,
                    "script-massa", f"{data} 21:00:00",
                ))

# ----------------------------------------------- outros eventos da agenda
for ano in (ANO_ANTERIOR, ANO_ATUAL):
    limite = HOJE if ano == ANO_ATUAL else datetime.date(ano, 12, 31)
    agenda = [
        ("Batismo comunitario", "SACRAMENTO", "PAROQUIAL", (3, 15)),
        ("Primeira Eucaristia", "SACRAMENTO", "PAROQUIAL", (10, 12)),
        ("Crisma", "SACRAMENTO", "PAROQUIAL", (11, 8)),
        ("Rito de eleicao", "RITO_RICA", "PAROQUIAL", (2, 22)),
        ("Primeiro escrutinio", "RITO_RICA", "PAROQUIAL", (3, 23)),
        ("Segundo escrutinio", "RITO_RICA", "PAROQUIAL", (3, 30)),
        ("Terceiro escrutinio", "RITO_RICA", "PAROQUIAL", (4, 6)),
        ("Entrega do Simbolo", "RITO_RICA", "PAROQUIAL", (4, 9)),
        ("Entrega da Oracao do Senhor", "RITO_RICA", "PAROQUIAL", (4, 16)),
        ("Assembleia diocesana", "ENCONTRO", "DIOCESANO", (5, 10)),
        ("Encontro regional de coordenadores", "ENCONTRO", "REGIONAL", (6, 14)),
    ]
    for titulo, tipo, nivel, (mes, dia) in agenda:
        data = datetime.date(ano, mes, dia)
        situacao = "REALIZADO" if data <= limite else "PREVISTO"
        if random.random() < 0.06:
            situacao = "CANCELADO"
        novo_evento(f"{titulo} {ano}", tipo, nivel, data, ano, situacao=situacao)

    # Eventos de comunidade e de turma, que sao os que exercitam a permissao.
    for com in comunidades:
        data = datetime.date(ano, random.randint(4, 9), random.randint(1, 28))
        situacao = "REALIZADO" if data <= limite else "PREVISTO"
        novo_evento(f"Festa da comunidade {com['nome']} {ano}", "ENCONTRO",
                    "COMUNIDADE", data, ano, situacao=situacao,
                    id_comunidade=com["id"], local=f"Comunidade {com['nome']}")

    for turma in random.sample(turmas_por_ano[ano], 6):
        data = datetime.date(ano, random.randint(5, 9), random.randint(1, 28))
        situacao = "REALIZADO" if data <= limite else "PREVISTO"
        novo_evento(f"Retiro da {turma['nome']}", "ENCONTRO", "TURMA", data, ano,
                    situacao=situacao, id_comunidade=turma["comunidade"]["id"],
                    id_turma=turma["id"], local="Sitio da paroquia")

# ============================================================================
# CONFERENCIA DE INTEGRIDADE -- antes de emitir uma linha de SQL
# ============================================================================
def conferir():
    problemas = []
    ids = {t: {l[0] for l in linhas} for t, linhas in m.linhas.items()}

    def fk(tabela, coluna, valor, alvo):
        if valor is not None and valor not in ids.get(alvo, set()):
            problemas.append(f"{tabela}.{coluna}={valor} nao existe em {alvo}")

    for l in m.linhas["tb_turma"]:
        fk("tb_turma", "id_catequista", l[5], "tb_catequista")
        fk("tb_turma", "id_comunidade", l[8], "tb_comunidade")
    for l in m.linhas["tb_turma_catequista"]:
        fk("tb_turma_catequista", "id_turma", l[1], "tb_turma")
        fk("tb_turma_catequista", "id_catequista", l[2], "tb_catequista")
    for l in m.linhas["tb_catequisando"]:
        fk("tb_catequisando", "id_comunidade", l[10], "tb_comunidade")
    for l in m.linhas["tb_matricula"]:
        fk("tb_matricula", "id_catequisando", l[1], "tb_catequisando")
        fk("tb_matricula", "id_turma", l[2], "tb_turma")
    for l in m.linhas["tb_encontro"]:
        fk("tb_encontro", "id_turma", l[1], "tb_turma")
    for l in m.linhas["tb_presenca"]:
        fk("tb_presenca", "id_catequisando", l[3], "tb_catequisando")
        fk("tb_presenca", "id_encontro", l[4], "tb_encontro")
    for l in m.linhas["tb_evento"]:
        fk("tb_evento", "id_comunidade", l[9], "tb_comunidade")
        fk("tb_evento", "id_turma", l[10], "tb_turma")
        fk("tb_evento", "id_formacao", l[11], "tb_formacao")
    for l in m.linhas["tb_formacao_inscrito"]:
        fk("tb_formacao_inscrito", "id_formacao", l[1], "tb_formacao")
        fk("tb_formacao_inscrito", "id_catequista", l[2], "tb_catequista")
    for l in m.linhas["tb_presenca_formacao"]:
        fk("tb_presenca_formacao", "id_evento", l[1], "tb_evento")
        fk("tb_presenca_formacao", "id_catequista", l[2], "tb_catequista")
    for l in m.linhas["tb_etapa_catecumeno"]:
        fk("tb_etapa_catecumeno", "id_catequisando", l[1], "tb_catequisando")

    # As duas UNIQUE que existem de verdade no banco
    for tabela, cols, nome in [
        ("tb_formacao_inscrito", (1, 2), "uk_formacao_catequista"),
        ("tb_presenca_formacao", (1, 2), "uk_presenca_formacao"),
    ]:
        vistos = set()
        for l in m.linhas[tabela]:
            chave = tuple(l[c] for c in cols)
            if chave in vistos:
                problemas.append(f"{nome} duplicada: {chave}")
            vistos.add(chave)

    # Uma matricula por pessoa/turma/ano (nao ha UNIQUE no banco, mas o
    # AdminCatequeseService trata como se houvesse)
    vistos = set()
    for l in m.linhas["tb_matricula"]:
        chave = (l[1], l[2], l[3])
        if chave in vistos:
            problemas.append(f"matricula duplicada: {chave}")
        vistos.add(chave)

    return problemas


problemas = conferir()
if problemas:
    print(f"{len(problemas)} PROBLEMA(S) DE INTEGRIDADE:")
    for p in problemas[:20]:
        print("  ", p)
    sys.exit(1)

# ============================================================================
# EMISSAO
# ============================================================================
COLUNAS = {
    "tb_comunidade": "id_comunidade, nome, descricao, ativo",
    "tb_catequista": "id_catequista, nome, telefone, email, endereco, data_nascimento, data_inicio, ativo",
    "tb_turma": "id_turma, nome, descricao, ano, nivel, id_catequista, categoria, etapa, id_comunidade",
    "tb_turma_catequista": "id_turma_catequista, id_turma, id_catequista, principal, criado_em",
    "tb_catequisando": ("id_catequisando, nome, telefone, email, data_nascimento, nome_responsavel, "
                        "telefone_responsavel, foi_batizado, fez_primeira_eucaristia, ativo, id_comunidade"),
    "tb_matricula": ("id_matricula, id_catequisando, id_turma, ano, data_matricula, situacao, "
                     "observacao, criado_em, atualizado_em, atualizado_por"),
    "tb_encontro": ("id_encontro, id_turma, data, tema, situacao, motivo_cancelamento, id_evento, "
                    "aberto_por, aberto_em, fechado_por, fechado_em, fechamento_automatico"),
    "tb_presenca": ("id_presenca, data, presente, id_catequisando, id_encontro, situacao, "
                    "justificativa, marcado_por, marcado_em"),
    "tb_etapa_catecumeno": ("id_etapa, id_catequisando, etapa, data_inicio, data_fim, observacao, "
                            "registrado_por, registrado_em"),
    "tb_evento": ("id_evento, titulo, nivel, publico_alvo, descricao, data_inicio, data_fim, local, "
                  "tipo, id_comunidade, id_turma, id_formacao, hora_inicio, situacao, "
                  "motivo_cancelamento, criado_por, criado_em"),
    "tb_formacao": "id_formacao, nome, nivel, ano, descricao, percentual_minimo, situacao, criado_por, criado_em",
    "tb_formacao_inscrito": "id_formacao_inscrito, id_formacao, id_catequista, inscrito_em",
    "tb_presenca_formacao": ("id_presenca_formacao, id_evento, id_catequista, situacao, justificativa, "
                             "marcado_por, marcado_em"),
}

# Ordem de insercao: pai antes de filho, para o FK nunca reclamar.
ORDEM = [
    "tb_comunidade", "tb_catequista", "tb_turma", "tb_turma_catequista",
    "tb_catequisando", "tb_matricula", "tb_encontro", "tb_presenca",
    "tb_etapa_catecumeno", "tb_formacao", "tb_formacao_inscrito",
    "tb_evento", "tb_presenca_formacao",
]

def literal(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, (datetime.date, datetime.datetime)):
        return f"'{v}'"
    return "'" + esc(str(v)) + "'"


# ---------------------------------------------------------------------------
# PREVIA: o que a tela de Indicadores deve mostrar com esta massa.
#
# Serve para duas coisas: dizer ao Gabriel o que esperar, e me deixar conferir
# que a massa exercita as tres faixas de frequencia em vez de concentrar tudo
# no meio -- massa que so tem caso feliz nao testa nada.
# ---------------------------------------------------------------------------
RESUMO = []


def previa():
    def print(*a):  # noqa: A001 - acumula em vez de so imprimir
        RESUMO.append(" ".join(str(x) for x in a))

    for ano in (ANO_ANTERIOR, ANO_ATUAL):
        n = sum(1 for r in matriculas if r["ano"] == ano)
        print(f"  matriculas em {ano}: {n}")

    agora = {r["pessoa"]["id"] for r in matriculas if r["ano"] == ANO_ATUAL}
    antes = {r["pessoa"]["id"] for r in matriculas if r["ano"] == ANO_ANTERIOR}
    sairam = antes - agora
    porpessoa = defaultdict(list)
    for r in matriculas:
        if r["ano"] == ANO_ANTERIOR and r["pessoa"]["id"] in sairam:
            porpessoa[r["pessoa"]["id"]].append(r["situacao"])
    concl = sum(1 for i in sairam if "CONCLUIDO" in porpessoa[i])
    transf = sum(1 for i in sairam
                 if porpessoa[i] and all(s == "TRANSFERIDO" for s in porpessoa[i]))
    aband = len(sairam) - concl - transf
    denom = len(antes) - concl - transf
    ret = len(agora & antes) / denom * 100 if denom else 0
    print(f"  entraram {len(agora - antes)} | permaneceram {len(agora & antes)} | "
          f"concluiram {concl} | abandonaram {aband} | transferidos {transf}")
    print(f"  retencao {ret:.1f}%")

    # frequencia do ano em curso, pela regra do CalculoFrequencia
    presencas = defaultdict(lambda: [0, 0])  # pessoa -> [considerados, presentes]
    fechados = {l[0] for l in m.linhas["tb_encontro"] if l[4] == "FECHADO"}
    for l in m.linhas["tb_presenca"]:
        if l[4] not in fechados or l[5] == "JUSTIFICADA":
            continue
        presencas[l[3]][0] += 1
        if l[5] == "PRESENTE":
            presencas[l[3]][1] += 1
    doAno = {r["pessoa"]["id"] for r in matriculas if r["ano"] == ANO_ATUAL}
    faixas = defaultdict(int)
    soma, quantos = 0.0, 0
    for pid in doAno:
        cons, pres = presencas.get(pid, [0, 0])
        if cons == 0:
            faixas["sem apuracao"] += 1
            continue
        pct = pres / cons * 100
        soma += pct; quantos += 1
        if pct < MINIMO:
            faixas["abaixo do minimo"] += 1
        elif pct < ALERTA:
            faixas["perto do limite"] += 1
        else:
            faixas["regular"] += 1
    print(f"  frequencia {ANO_ATUAL}: media {soma / quantos:.1f}% "
          + " | ".join(f"{k} {v}" for k, v in sorted(faixas.items())))

    for f in [x for x in formacoes if x["ano"] == ANO_ATUAL]:
        insc = {l[2] for l in m.linhas["tb_formacao_inscrito"] if l[1] == f["id"]}
        evs = {l[0] for l in m.linhas["tb_evento"] if l[11] == f["id"]}
        pres = defaultdict(int)
        for l in m.linhas["tb_presenca_formacao"]:
            if l[1] in evs and l[3] == "PRESENTE":
                pres[l[2]] += 1
        # `pres` e defaultdict: usar pres[k] aqui CRIARIA a chave e "participaram"
        # passaria a contar todo inscrito. Ja aconteceu nesta previa.
        participaram = sum(1 for k in insc if pres.get(k, 0) > 0)
        atingiu = sum(1 for k in insc if evs and pres.get(k, 0) / len(evs) * 100 >= 80)
        print(f"  formacao {f['nivel']:<10} inscritos {len(insc):>3} | "
              f"participaram {participaram:>3} | atingiram o minimo {atingiu:>3}")



previa()

saida = []
saida.append(f"""-- =====================================================================
-- MASSA DE DADOS PARA O BANCO DE DESENVOLVIMENTO
-- =====================================================================
--
-- GERADO por docs/gerar_massa_dev.py (semente fixa: rodar de novo da o mesmo
-- arquivo). NAO EDITE A MAO -- edite o gerador e rode de novo.
--
-- NAO RODE EM PRODUCAO. Sao {ANO_ANTERIOR} e {ANO_ATUAL} inteiros de catequese
-- inventada, para a tela de Indicadores ter o que comparar.
--
-- COMO RODAR (DBeaver): abra, selecione tudo e execute como SCRIPT (Alt+X).
-- Instrucao a instrucao so roda a primeira.
--
-- O QUE ENTRA:
--   {len(m.linhas['tb_comunidade']):>6} comunidades
--   {len(m.linhas['tb_catequista']):>6} catequistas
--   {len(m.linhas['tb_turma']):>6} turmas ({len(turmas_por_ano[ANO_ANTERIOR])} em {ANO_ANTERIOR}, {len(turmas_por_ano[ANO_ATUAL])} em {ANO_ATUAL})
--   {len(m.linhas['tb_catequisando']):>6} catequisandos
--   {len(m.linhas['tb_matricula']):>6} matriculas
--   {len(m.linhas['tb_encontro']):>6} encontros
--   {len(m.linhas['tb_presenca']):>6} presencas de catequisando
--   {len(m.linhas['tb_formacao']):>6} formacoes
--   {len(m.linhas['tb_presenca_formacao']):>6} presencas de catequista
--   {len(m.linhas['tb_evento']):>6} eventos
--
-- TODO id comeca em {BASE}. E isso que deixa o bloco COMO DESFAZER, no fim,
-- apagar exatamente o que este script criou, sem tocar no que voce cadastrou
-- pela tela.
--
-- NAO CRIA USUARIO (tb_usuario). Senha e hash BCrypt: inventar um aqui daria
-- conta que ninguem consegue usar. Crie pela tela de Usuarios e vincule ao
-- catequista pelo nome.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- Limpeza do que ESTE script criou antes (e so isso: id >= {BASE}).
-- Deixa o script re-executavel sem duplicar nada.""")

for tabela in reversed(ORDEM):
    pk = COLUNAS[tabela].split(",")[0].strip()
    saida.append(f"DELETE FROM {tabela} WHERE {pk} >= {BASE};")

saida.append("\nSET FOREIGN_KEY_CHECKS = 1;\n")

LOTE = 200
for tabela in ORDEM:
    linhas = m.linhas[tabela]
    if not linhas:
        continue
    saida.append(f"\n-- ---------------------------------------------------------------")
    saida.append(f"-- {tabela}: {len(linhas)} linha(s)")
    saida.append(f"-- ---------------------------------------------------------------")
    for inicio in range(0, len(linhas), LOTE):
        pedaco = linhas[inicio:inicio + LOTE]
        saida.append(f"INSERT INTO {tabela} ({COLUNAS[tabela]}) VALUES")
        corpo = [",\n".join("  (" + ", ".join(literal(v) for v in l) + ")" for l in pedaco)]
        saida.append(corpo[0] + ";")

saida.append(f"""

-- =====================================================================
-- CONFERENCIA
-- =====================================================================
-- Rode depois e confira que os numeros batem com o cabecalho:
--
--   SELECT ano, COUNT(*) FROM tb_matricula WHERE id_matricula >= {BASE} GROUP BY ano;
--   SELECT situacao, COUNT(*) FROM tb_matricula WHERE ano = {ANO_ATUAL} GROUP BY situacao;
--   SELECT c.nome, COUNT(*) FROM tb_matricula m
--     JOIN tb_turma t ON t.id_turma = m.id_turma
--     JOIN tb_comunidade c ON c.id_comunidade = t.id_comunidade
--    WHERE m.ano = {ANO_ATUAL} GROUP BY c.nome ORDER BY 2 DESC;
--   SELECT situacao, COUNT(*) FROM tb_encontro WHERE id_encontro >= {BASE} GROUP BY situacao;
--   SELECT f.nivel, COUNT(DISTINCT i.id_catequista) inscritos
--     FROM tb_formacao f JOIN tb_formacao_inscrito i ON i.id_formacao = f.id_formacao
--    WHERE f.ano = {ANO_ATUAL} GROUP BY f.nivel;
--
-- Depois abra Indicadores: o Resumo geral tem de mostrar {ANO_ATUAL} comparado
-- com {ANO_ANTERIOR}, com variacao em todos os cartoes.
--
-- O QUE A TELA DEVE MOSTRAR com esta massa (calculado pelo gerador, aplicando
-- as mesmas regras do CalculoFrequencia):
--
{chr(10).join("-- " + l for l in RESUMO)}

-- =====================================================================
-- COMO DESFAZER
-- =====================================================================
-- Apaga so o que este script criou:
--
--   SET FOREIGN_KEY_CHECKS = 0;""")
for tabela in reversed(ORDEM):
    pk = COLUNAS[tabela].split(",")[0].strip()
    saida.append(f"--   DELETE FROM {tabela} WHERE {pk} >= {BASE};")
saida.append("--   SET FOREIGN_KEY_CHECKS = 1;")

destino = pathlib.Path("sql/dados-dev/MASSA_DEV.sql")
destino.parent.mkdir(parents=True, exist_ok=True)
destino.write_text("\n".join(saida) + "\n", encoding="utf-8")

total = sum(len(v) for v in m.linhas.values())
print(f"integridade: OK")
print(f"{destino}: {total} linhas em {len(m.linhas)} tabelas, "
      f"{destino.stat().st_size / 1024:.0f} KB")
for t in ORDEM:
    print(f"  {t:24} {len(m.linhas[t]):>6}")
print(f"\ncontinuaram de {ANO_ANTERIOR} para {ANO_ATUAL}: {continuaram}")

print("\n--- previa dos indicadores ---")
for l in RESUMO:
    print(l)
