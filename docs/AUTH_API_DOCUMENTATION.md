# 🔐 Sistema de Autenticação - API Documentation

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Estrutura de Roles](#estrutura-de-roles)
3. [Endpoints Disponíveis](#endpoints-disponíveis)
4. [Exemplos de Uso](#exemplos-de-uso)
5. [Segurança](#segurança)
6. [Scripts SQL](#scripts-sql)

---

## 🎯 Visão Geral

Sistema completo de autenticação com JWT, recuperação de senha e controle de acesso baseado em roles (RBAC).

**Funcionalidades:**
- ✅ Login com email/senha
- ✅ JWT Token (válido por 48h)
- ✅ Recuperação de senha por email
- ✅ Reset de senha com token
- ✅ Controle de acesso por roles
- ✅ Gestão de usuários
- ✅ Vinculação com comunidades/catequistas

---

## 👥 Estrutura de Roles

### 1️⃣ COORDENADOR_PAROQUIAL
**Nível:** Administrador  
**Acesso:**
- ✅ Acesso total ao sistema
- ✅ Gerencia todas as comunidades
- ✅ Gerencia todos os catequistas
- ✅ Gerencia usuários e permissões
- ✅ Visualiza relatórios gerais

**Exemplo de uso:**
```json
{
  "nome": "João Silva",
  "email": "joao@catequese.com",
  "password": "senha123",
  "roles": ["COORDENADOR_PAROQUIAL"]
}
```

### 2️⃣ COORDENADOR_COMUNIDADE
**Nível:** Gerente de Comunidade  
**Acesso:**
- ✅ Acesso à sua comunidade específica
- ✅ Visualiza catequistas da comunidade
- ✅ Gerencia catequisandos da comunidade
- ✅ Visualiza relatórios da comunidade

**Exemplo de uso:**
```json
{
  "nome": "Maria Santos",
  "email": "maria@catequese.com",
  "password": "senha123",
  "roles": ["COORDENADOR_COMUNIDADE"],
  "idComunidade": 1
}
```

### 3️⃣ CATEQUISTA
**Nível:** Professor  
**Acesso:**
- ✅ Acesso às suas turmas
- ✅ Gerencia presença dos alunos
- ✅ Visualiza dados dos catequisandos
- ✅ Registra atividades

**Exemplo de uso:**
```json
{
  "nome": "Pedro Oliveira",
  "email": "pedro@catequese.com",
  "password": "senha123",
  "roles": ["CATEQUISTA"],
  "idCatequista": 5
}
```

---

## 🔌 Endpoints Disponíveis

### Autenticação

#### 1. Login
```http
POST /api/auth/login
Content-Type: application/json

{
  "email": "joao@catequese.com",
  "password": "senha123"
}
```

**Resposta (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "email": "joao@catequese.com",
  "nome": "João Silva",
  "roles": ["COORDENADOR_PAROQUIAL"],
  "expiresIn": 172800000
}
```

**Erros:**
- `401 Unauthorized` - Credenciais inválidas
- `400 Bad Request` - Usuário inativo

---

#### 2. Solicitar Reset de Senha
```http
POST /api/auth/password-reset/request
Content-Type: application/json

{
  "email": "joao@catequese.com"
}
```

**Resposta (200 OK):**
```json
{
  "message": "Se o email estiver cadastrado, você receberá instruções para redefinir sua senha."
}
```

**Observação:** Por segurança, sempre retorna sucesso mesmo se o email não existir.

---

#### 3. Confirmar Reset de Senha
```http
POST /api/auth/password-reset/confirm
Content-Type: application/json

{
  "token": "550e8400-e29b-41d4-a716-446655440000",
  "newPassword": "novaSenha123"
}
```

**Resposta (200 OK):**
```json
{
  "message": "Senha alterada com sucesso"
}
```

**Erros:**
- `400 Bad Request` - Token inválido/expirado
- `400 Bad Request` - Senha muito curta (mínimo 6 caracteres)

---

#### 4. Validar Token
```http
GET /api/auth/validate
Authorization: Bearer {token}
```

**Resposta (200 OK):**
```json
{
  "valid": true
}
```

---

#### 5. Health Check
```http
GET /api/auth/health
```

**Resposta (200 OK):**
```json
{
  "status": "UP",
  "module": "authentication"
}
```

---

### Gestão de Usuários

#### 1. Listar Todos os Usuários
```http
GET /api/usuarios
Authorization: Bearer {token}
```

**Resposta (200 OK):**
```json
[
  {
    "idUsuario": 1,
    "nome": "João Silva",
    "email": "joao@catequese.com",
    "ativo": true,
    "roles": ["COORDENADOR_PAROQUIAL"],
    "idComunidade": null,
    "idCatequista": null
  }
]
```

---

#### 2. Buscar Usuário por ID
```http
GET /api/usuarios/{id}
Authorization: Bearer {token}
```

---

#### 3. Buscar Usuário por Email
```http
GET /api/usuarios/email/{email}
Authorization: Bearer {token}
```

---

#### 4. Criar Novo Usuário
```http
POST /api/usuarios
Authorization: Bearer {token}
Content-Type: application/json

{
  "nome": "Ana Costa",
  "email": "ana@catequese.com",
  "password": "senha123",
  "roles": ["COORDENADOR_COMUNIDADE", "CATEQUISTA"],
  "idComunidade": 1,
  "idCatequista": 5
}
```

**Resposta (201 Created):**
```json
{
  "idUsuario": 10,
  "nome": "Ana Costa",
  "email": "ana@catequese.com",
  "ativo": true,
  "roles": ["COORDENADOR_COMUNIDADE", "CATEQUISTA"],
  "idComunidade": 1,
  "idCatequista": 5
}
```

**Erros:**
- `400 Bad Request` - Email já cadastrado
- `400 Bad Request` - Senha muito curta
- `400 Bad Request` - Role inválida
- `404 Not Found` - Comunidade/Catequista não encontrado

---

#### 5. Atualizar Usuário
```http
PUT /api/usuarios/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "nome": "João Silva Junior",
  "email": "joao.junior@catequese.com",
  "ativo": true,
  "roles": ["COORDENADOR_PAROQUIAL"],
  "idComunidade": null,
  "idCatequista": null
}
```

---

#### 6. Ativar/Desativar Usuário
```http
PATCH /api/usuarios/{id}/toggle-ativo
Authorization: Bearer {token}
```

**Resposta (200 OK):**
```json
{
  "idUsuario": 1,
  "nome": "João Silva",
  "email": "joao@catequese.com",
  "ativo": false,
  "roles": ["COORDENADOR_PAROQUIAL"],
  "idComunidade": null,
  "idCatequista": null
}
```

---

#### 7. Deletar Usuário
```http
DELETE /api/usuarios/{id}
Authorization: Bearer {token}
```

**Resposta (204 No Content)**

---

## 📚 Exemplos de Uso

### Fluxo Completo de Login

```javascript
// 1. Login
const loginResponse = await fetch('http://localhost:8080/api/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'joao@catequese.com',
    password: 'senha123'
  })
});

const { token, nome, roles } = await loginResponse.json();

// 2. Salvar token
localStorage.setItem('auth_token', token);

// 3. Usar token em requisições
const response = await fetch('http://localhost:8080/api/usuarios', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});

const usuarios = await response.json();
```

---

### Fluxo de Recuperação de Senha

```javascript
// 1. Solicitar reset
await fetch('http://localhost:8080/api/auth/password-reset/request', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email: 'joao@catequese.com'
  })
});

// 2. Usuário recebe email com token

// 3. Confirmar reset
await fetch('http://localhost:8080/api/auth/password-reset/confirm', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    token: '550e8400-e29b-41d4-a716-446655440000',
    newPassword: 'novaSenha123'
  })
});
```

---

## 🔒 Segurança

### Senhas
- ✅ Hash BCrypt (força 10)
- ✅ Mínimo 6 caracteres
- ✅ Nunca retornadas na API

### Tokens JWT
- ✅ Assinados com HS512
- ✅ Válidos por 48 horas (configurável)
- ✅ Contém: id, email, nome, roles

### Reset de Senha
- ✅ Token UUID único
- ✅ Válido por 24 horas
- ✅ Uso único (marcado após utilização)
- ✅ Limpeza automática de tokens expirados

### CORS
- ✅ Configurado para domínios específicos
- ✅ Suporta credenciais
- ✅ Headers personalizados permitidos

---

## 📊 Scripts SQL

### 1. Criar Tabelas
```bash
mysql -u usuario -p catequese < CREATE_AUTH_TABLES.sql
```

**Cria:**
- `tb_usuario`
- `tb_usuario_role`
- `tb_password_reset_token`

**Insere:**
- Usuário admin padrão (admin@catequese.com / admin123)

---

### 2. Popular Dados de Teste
```bash
mysql -u usuario -p catequese < POPULATE_AUTH_TEST_DATA.sql
```

**Insere:**
- 5 usuários de teste
- Diferentes roles
- Vinculações com comunidades/catequistas

---

## 🧪 Credenciais de Teste

Após rodar `POPULATE_AUTH_TEST_DATA.sql`:

| Email | Senha | Roles |
|-------|-------|-------|
| admin@catequese.com | admin123 | COORDENADOR_PAROQUIAL |
| joao.silva@catequese.com | admin123 | COORDENADOR_PAROQUIAL |
| maria.santos@catequese.com | admin123 | COORDENADOR_COMUNIDADE |
| pedro.oliveira@catequese.com | admin123 | CATEQUISTA |
| ana.costa@catequese.com | admin123 | COORDENADOR_COMUNIDADE + CATEQUISTA |

⚠️ **IMPORTANTE:** Altere essas senhas em produção!

---

## 🚀 Próximos Passos

1. ✅ Implementar envio real de emails (SendGrid, AWS SES)
2. ✅ Adicionar autenticação em endpoints sensíveis
3. ✅ Implementar refresh tokens
4. ✅ Adicionar auditoria de ações
5. ✅ Implementar 2FA (autenticação em dois fatores)

---

## 📞 Suporte

Para dúvidas ou problemas, consulte:
- **Logs do backend**: `logs/app.log`
- **Health check**: `GET /api/auth/health`
- **Validar token**: `GET /api/auth/validate`

---

**Última atualização:** 2026-03-03  
**Versão:** 1.0.0


