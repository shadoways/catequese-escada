# ✅ PROBLEMA DE SENHA RESOLVIDO - USE O NOVO SCRIPT

## 🔧 O Problema

A senha com `$` em `application-dev.properties` não estava sendo interpretada corretamente. Spring Boot estava lendo `$abacabb123` como uma variável não definida.

## ✅ A Solução

Criei um script `run-dev.sh` que:
1. Recebe a senha como parâmetro
2. Testa a conexão antes de rodar
3. Passa a senha via variável de ambiente (seguro)
4. Inicia a aplicação com o profile correto

---

## 🚀 COMO USAR

### Passo 1: Criar as Tabelas (uma única vez)

```bash
mysql -u root -pSUA_SENHA -h localhost catequese < CREATE_AUTH_TABLES.sql
```

Substitua `SUA_SENHA` pela sua senha do root (sem espaço após `-p`).

### Passo 2: Rodar a Aplicação

```bash
./run-dev.sh SUA_SENHA
```

**Exemplos:**
```bash
./run-dev.sh abacabb123
./run-dev.sh root
./run-dev.sh password123
```

O script vai:
1. ✅ Testar a conexão ao banco
2. ✅ Informar se conectou com sucesso
3. ✅ Rodar a aplicação
4. ✅ Esperado: "Tomcat started on port(s): 8080"

### Passo 3: Testar em Outro Terminal

```bash
curl http://localhost:8080/api/auth/health
```

---

## 📋 O QUE MUDOU

### Antes ❌
```
spring.datasource.password=$abacabb123
→ Spring interpretava como variável não definida
```

### Depois ✅
```bash
export DEV_DB_PASSWORD="$abacabb123"
→ Variável de ambiente passa o valor corretamente
```

---

## 🗂️ Estrutura de Configuração

Agora você tem 3 perfis:

```
application.properties              ← Base (comum)
application-dev.properties          ← Desenvolvimento (usa variáveis ENV)
application-prod.properties         ← Produção (Aiven)
```

### Desenvolvimento
```bash
./run-dev.sh sua_senha
```
✅ MariaDB local  
✅ Sem SSL  
✅ Debug SQL ligado

### Produção
```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="jdbc:mysql://..."
export SPRING_DATASOURCE_USERNAME="..."
export SPRING_DATASOURCE_PASSWORD="..."
./gradlew bootRun
```
✅ MySQL Aiven  
✅ Com SSL  
✅ Debug SQL desligado

---

## ⚠️ IMPORTANTE

Sua senha do MariaDB **NÃO** é `$abacabb123` se o script disser que falhou.

**Opções:**
1. **Você sabe a senha?** Use-a no script
2. **Esqueceu a senha?** Resete o root do MariaDB:
   ```bash
   sudo systemctl stop mariadb
   sudo /usr/sbin/mysqld --skip-grant-tables &
   mysql -u root
   # No MySQL:
   FLUSH PRIVILEGES;
   ALTER USER 'root'@'localhost' IDENTIFIED BY 'nova_senha';
   EXIT;
   sudo systemctl restart mariadb
   ```

---

## 🧪 TESTE RÁPIDO

```bash
# 1. Verificar se MariaDB está rodando
systemctl status mariadb

# 2. Testar conexão manual
mysql -u root -pSUA_SENHA -h localhost -e "SELECT 1;"

# 3. Rodar script de dev
./run-dev.sh SUA_SENHA

# 4. Em outro terminal
curl http://localhost:8080/api/auth/health
```

---

## ✅ AGORA VOCÊ ESTÁ PRONTO!

```bash
./run-dev.sh $abacabb123
```

ou

```bash
./run-dev.sh sua_senha_correta
```

Aguarde:
```
✅ Conexão ao banco OK!
🚀 Iniciando aplicação...
```

Depois em outro terminal:
```bash
curl http://localhost:8080/api/auth/health
```

---

**Status:** ✅ **PRONTO PARA USAR**  
**Script:** `run-dev.sh`  
**Data:** 2026-03-04

