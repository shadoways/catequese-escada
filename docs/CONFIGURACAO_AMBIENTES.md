# 🔧 CONFIGURAÇÃO POR AMBIENTE - DESENVOLVIMENTO vs PRODUÇÃO

## 📋 Estrutura de Profiles

Agora você tem 3 arquivos de configuração:

```
application.properties          ← Base (comum)
application-dev.properties      ← Desenvolvimento (MariaDB local)
application-prod.properties     ← Produção (MySQL Aiven)
```

---

## ✅ COMO USAR

### DESENVOLVIMENTO (Local - MariaDB)

```bash
# Opção 1: Variável de ambiente
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun

# Opção 2: Parâmetro gradle
./gradlew bootRun --args='--spring.profiles.active=dev'

# Opção 3: Arquivo local (criar .gradle.properties)
# (não recomendado - use opção 1 ou 2)
```

**Configuração:**
- URL: `jdbc:mariadb://localhost:3306/catequese`
- User: `root`
- Senha: variável `DEV_DB_PASSWORD`
- Driver: `org.mariadb.jdbc.Driver`
- Dialect: `MariaDBDialect`
- SSL: `false`

---

### PRODUÇÃO (Aiven - MySQL)

```bash
# Definir variáveis de ambiente PRIMEIRO:
export SPRING_DATASOURCE_URL="jdbc:mysql://mysql-escada-catequese-escada.j.aivencloud.com:26254/catequese?useSSL=true&requireSSL=true&serverTimezone=UTC"
export SPRING_DATASOURCE_USERNAME="cq-escada"
export SPRING_DATASOURCE_PASSWORD="SUA_SENHA_AIVEN"
export SPRING_PROFILES_ACTIVE=prod

# Depois rodar:
./gradlew bootRun
```

**Configuração:**
- URL: variável `SPRING_DATASOURCE_URL`
- User: variável `SPRING_DATASOURCE_USERNAME`
- Senha: variável `SPRING_DATASOURCE_PASSWORD`
- Driver: `com.mysql.cj.jdbc.Driver`
- Dialect: `MySQL8Dialect`
- SSL: `true`

---

## 🚀 PASSO A PASSO - RODAR EM DESENVOLVIMENTO

### 1. Build
```bash
./gradlew clean build
```

### 2. Rodar com profile DEV
```bash
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```

### 3. Testar
```bash
curl http://localhost:8080/api/auth/health
```

---

## 🚀 PASSO A PASSO - RODAR EM PRODUÇÃO (Render/Railway)

### 1. Definir variáveis de ambiente no painel

Na plataforma (Render, Railway, Heroku, etc):

```
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:mysql://mysql-escada-...
SPRING_DATASOURCE_USERNAME=cq-escada
SPRING_DATASOURCE_PASSWORD=SUA_SENHA_AIVEN
JWT_SECRET=seu_secret_seguro
```

### 2. Deploy
```bash
git push origin main
# Plataforma faz rebuild + deploy automático
```

---

## 📊 COMPARAÇÃO

| Aspecto | Desenvolvimento | Produção |
|---------|-----------------|----------|
| **Profile** | `dev` | `prod` |
| **Banco** | MariaDB local | MySQL Aiven |
| **Host** | localhost:3306 | aivencloud.com:26254 |
| **Driver** | mariadb-java-client | mysql-connector-j |
| **Dialect** | MariaDBDialect | MySQL8Dialect |
| **SSL** | false | true |
| **SQL Debug** | true | false |
| **Var. de Env** | Nenhuma | SPRING_DATASOURCE_* |

---

## ⚠️ IMPORTANTE

### Para Desenvolvimento
```bash
export SPRING_PROFILES_ACTIVE=dev
./gradlew bootRun
```
✅ Não precisa de nenhuma variável de ambiente  
✅ Conecta ao MariaDB local

### Para Produção
```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="..."
export SPRING_DATASOURCE_USERNAME="..."
export SPRING_DATASOURCE_PASSWORD="..."
./gradlew bootRun
```
✅ Precisa de todas as variáveis definidas  
✅ Conecta ao Aiven MySQL

---

## 🧪 TESTAR A CONFIGURAÇÃO

### Verificar qual profile está ativo
```bash
curl -s http://localhost:8080/api/auth/health | jq .
```

### Ver logs de inicialização
```bash
./gradlew bootRun 2>&1 | grep -i "profile\|active\|datasource"
```

---

## ✅ AGORA VOCÊ PODE

1. **Desenvolver localmente** com MariaDB
2. **Testar em staging** com Aiven
3. **Deploy em produção** com variáveis de ambiente

Tudo com **zero conflitos de configuração**!

---

**Status:** ✅ Configuração separada por ambiente  
**Data:** 2026-03-04


