# 🚀 PASSO A PASSO FINAL - DO BUILD AO LOGIN FUNCIONANDO

## ✅ STATUS ATUAL

```
✅ Build: Sucesso
✅ Compilação: OK
✅ Dependências: Resolvidas
✅ Driver MariaDB: Configurado
⏭️ Próximas ações: 5 passos simples
```

---

## 5️⃣ PASSOS PARA TER TUDO FUNCIONANDO (15 minutos)

### PASSO 1: Criar Tabelas no Banco (2 min)

```bash
cd /home/usuario/workspace/catequese-escada
mysql -u root -p$abacabb123 -h localhost catequese < CREATE_AUTH_TABLES.sql
```

**O que faz:**
- ✅ Cria tabela `tb_usuario`
- ✅ Cria tabela `tb_usuario_role`
- ✅ Cria tabela `tb_password_reset_token`
- ✅ Insere usuário admin (admin@catequese.com / admin123)

**Como verificar:**
```bash
mysql -u root -p$abacabb123 -h localhost catequese -e "SHOW TABLES;"
```

---

### PASSO 2: Verificar Tabelas (1 min)

```bash
mysql -u root -p$abacabb123 -h localhost catequese < VERIFY_AUTH_TABLES.sql
```

**O que procurar:**
```
✅ tb_usuario JÁ EXISTE
✅ tb_usuario_role JÁ EXISTE
✅ tb_password_reset_token JÁ EXISTE
✅ total_usuarios > 0
```

---

### PASSO 3: Build do Projeto (3 min)

```bash
./gradlew clean build
```

**Deve terminar com:**
```
BUILD SUCCESSFUL in X seconds
```

---

### PASSO 4: Rodar a Aplicação (2 min)

```bash
./gradlew bootRun
```

**Aguarde até ver:**
```
✅ HikariPool-1 - Starting...
✅ HikariPool-1 - Connected to database
✅ Tomcat started on port(s): 8080 (http)
```

**Deixe rodando neste terminal!**

---

### PASSO 5: Testar em Outro Terminal (2 min)

```bash
# Abrir novo terminal

# Teste 1: Health Check
curl http://localhost:8080/api/auth/health

# Deve retornar:
# {
#   "status": "UP",
#   "module": "authentication"
# }

# Teste 2: Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@catequese.com","password":"admin123"}'

# Deve retornar um token JWT:
# {
#   "token": "eyJhbGciOiJIUzUxMiJ9...",
#   "email": "admin@catequese.com",
#   "nome": "Administrador",
#   "roles": ["COORDENADOR_PAROQUIAL"],
#   "expiresIn": 172800000
# }
```

---

## 🧪 CREDENCIAIS PARA TESTAR

### Admin (criado automaticamente)
```
Email: admin@catequese.com
Senha: admin123
```

### Usuários de Teste (opcional - rodar POPULATE_AUTH_TEST_DATA.sql)
```
joao.silva@catequese.com      / admin123
maria.santos@catequese.com    / admin123
pedro.oliveira@catequese.com  / admin123
ana.costa@catequese.com       / admin123
```

---

## ✅ CHECKLIST DE SUCESSO

- [ ] Rodei `CREATE_AUTH_TABLES.sql`
- [ ] Verifiquei com `VERIFY_AUTH_TABLES.sql`
- [ ] Rodei `./gradlew clean build` (sucesso)
- [ ] Rodei `./gradlew bootRun` (aplicação subiu)
- [ ] Testei `GET /api/auth/health` (200 OK)
- [ ] Testei `POST /api/auth/login` (recebi token JWT)
- [ ] Token é válido (não vazio)

---

## 🐛 TROUBLESHOOTING

### Erro: "Connection refused"
```bash
# MariaDB não está rodando
systemctl start mariadb
# ou
service mariadb start
```

### Erro: "Access denied"
```bash
# Senha errada em application.properties
# Verifique: spring.datasource.password=$abacabb123
```

### Erro: "Unknown database 'catequese'"
```bash
# Banco não existe
# Verifique se rodou CREATE_AUTH_TABLES.sql
```

### Erro: "Tomcat started... but [ERROR] Could not connect"
```bash
# Verificar logs da aplicação
# Procurar por: "HikariPool"
# Deve ter: "Connected to database"
```

---

## 📊 RESUMO FINAL

| Etapa | Comando | Tempo | Status |
|-------|---------|-------|--------|
| 1. Criar tabelas | `mysql < CREATE_AUTH_TABLES.sql` | 1 min | ⏭️ |
| 2. Verificar | `mysql < VERIFY_AUTH_TABLES.sql` | 1 min | ⏭️ |
| 3. Build | `./gradlew clean build` | 3 min | ✅ |
| 4. Rodar | `./gradlew bootRun` | 2 min | ⏭️ |
| 5. Testar | `curl http://localhost:8080/api/auth/health` | 1 min | ⏭️ |
| **TOTAL** | **5 passos** | **~8 min** | **Pronto!** |

---

## 🎯 VOCÊ ESTÁ PRONTO!

**Próximo passo:** Rodar PASSO 1 acima para criar as tabelas!

```bash
cd /home/usuario/workspace/catequese-escada
mysql -u root -p$abacabb123 -h localhost catequese < CREATE_AUTH_TABLES.sql
```

**Depois volte e execute os outros 4 passos em sequência.**

---

## 📚 DOCUMENTAÇÃO PARA REFERÊNCIA

- **AUTH_README.md** - Guia rápido de uso
- **AUTH_API_DOCUMENTATION.md** - Documentação completa da API
- **DATABASE_CONNECTION_STRINGS.txt** - Referência de strings de conexão
- **INSTALLATION_GUIDE.txt** - Guia detalhado de instalação

---

**Status Final:** ✅ **PRONTO PARA USAR**  
**Data:** 2026-03-04  
**Build:** SUCCESSFUL  

🚀 **Bora fazer o teste!**


