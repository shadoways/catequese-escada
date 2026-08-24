# CHECKPOINT — Implementação de login/permissões + toggle de cadastro

> Arquivo de trabalho do assistente. Fica FORA do repositório git
> (`/home/claude/project/`), então nunca aparece em `git status` nem no bundle.
> Ler este arquivo primeiro ao retomar. Só ler o código-fonte do que a etapa atual exige.

## Contexto fixo (não re-investigar)

- Repo local: `/home/claude/project/catequese-git`
- **DUAS branches, não confundir:**
  - `ajuste-pontual-producao` — congelada em `659d433`. É o trabalho **pontual de
    impressão de PDFs** que o usuário está usando agora. **Não mexer mais nela.**
  - `login-e-permissoes` — criada de `659d433` (herda todo o front: menu, consulta,
    ficha, painel, filtros). **É aqui que todo o trabalho de login acontece.**
    Vai para produção e é testada em paralelo.
- **NUNCA** alterar/mergear `main`. Sem acesso a `git push` nesta sessão.
- Entrega = `git bundle create /tmp/<nome>.bundle main..login-e-permissoes`
  + `SendUserFile`. O usuário importa e dá push.
- Stack: Kotlin 1.9.25 / Spring Boot 3.5.5 / Java 21 / JPA+Hibernate / MySQL (Aiven) / GCS.
- Frontend: HTML/CSS/JS puro em `src/main/resources/static/`
  (`index.html`, `script.js`, `style.css`, `ficha.html`, `ficha.js`).
- Usuário roda o app **localmente apontando para o banco de PRODUÇÃO**.
- `spring.jpa.hibernate.ddl-auto=validate` → **qualquer entidade nova/alterada exige
  migração no banco antes de subir o app**, senão a aplicação não inicia.
- `src/main/resources/application.properties` está em **ISO-8859-1 (latin-1)**.
  NÃO reescrever com Write/Edit (corromperia os acentos). Anexar via
  `cat >> ... << 'EOF'` com conteúdo **somente ASCII**.

## Diagnóstico do que já existia (feito, não repetir)

- `tb_login` (entidade `Login`) existe mas **nenhum código a usa**; FK só para `tb_catequista`.
- `tb_permissoes` (entidade `Permissao`) = texto livre, CRUD exposto, **nunca lido** para autorizar.
- `LoginRequest.kt` existe e **não é usado em lugar nenhum**. Não há endpoint de login.
- **Não havia** Spring Security no `build.gradle.kts`. JWT incompleto (só `jjwt-jackson`,
  faltavam `jjwt-api` e `jjwt-impl`). `jwt.secret`/`jwt.expirationMs` já existiam nas properties.
- Nenhum hash de senha em lugar nenhum. API 100% aberta.
- Nada de toggle/período de cadastro em lugar nenhum.

## Decisões tomadas com o usuário

1. **Usuário independente**: criar tabela NOVA `tb_usuario` em vez de alterar `tb_login`.
   `tb_login`/`tb_permissoes` ficam intocados (legado). Isso evita `ALTER TABLE` em produção.
2. **Toggle de cadastro só manual** (liga/desliga), sem agendamento por data.
3. Três tipos: `CATEQUISTA` (só leitura), `COORDENADOR` (leitura+escrita),
   `COORDENADOR_PAROQUIAL` (admin: tudo + gestão de usuários + toggle).
4. **Branch separada** para o login (`login-e-permissoes`), partindo do trabalho de
   front atual (`659d433`) para não gerar conflito grande depois.
5. **Recuperação de senha: e-mail (link com token) + reset pelo administrador.**
   SMS ficou de fora (custo/gateway pago) — deixar a estrutura extensível, sem implementar.
6. **SMTP genérico configurável por variável de ambiente** — usuário decide o provedor depois.

## Plano por etapas

- [x] **Etapa 1 — Backend: base de autenticação** (`a28c555`)
      `tb_usuario` + entidade + repo + JwtService + JwtAuthFilter + SecurityConfig
      + `POST /api/auth/login` + `GET /api/auth/me`.
      Protegido por flag `app.security.enabled` (**default false**).
- [x] **Etapa 2 — Backend: endurecimento de segurança + primeiro acesso** (`d6ec923`)
      Colunas novas em `tb_usuario` (email, senha_provisoria, tentativas_falhas,
      bloqueado_ate, ultimo_login, data_troca_senha, telefone).
      Bloqueio por tentativas, política de senha forte, `POST /api/auth/trocar-senha`,
      bloqueio de tudo enquanto a senha for provisória, invalidação de token após troca,
      **tirar `jwt.secret` do repositório** (hoje está versionado — falha de segurança).
- [x] **Etapa 3 — Backend: recuperação de senha por e-mail** (`12d6f9e`)
      `tb_token_recuperacao` (token **hasheado**, uso único, expiração curta),
      `POST /api/auth/esqueci-senha`, `POST /api/auth/redefinir-senha`,
      envio via `spring-boot-starter-mail` com SMTP por env. Resposta sempre genérica.
- [x] **Etapa 4 — Backend: gestão de usuários (admin)** (`63e7a2b`)
      `UsuarioController` + `UsuarioAdminService` + `UsuarioDTO`/`CriarUsuarioDTO`/
      `AtualizarUsuarioDTO`/`SenhaProvisoriaDTO`.
      Endpoints: `GET /api/usuarios`, `GET /{id}`, `POST /api/usuarios`,
      `PUT /{id}`, `POST /{id}/resetar-senha`, `POST /{id}/desbloquear`.
      **Sem DELETE** — usuário é desativado, nunca apagado.
      Travas: não rebaixar/desativar o último admin ativo; ninguém se auto-desativa
      nem se auto-rebaixa. `/api/usuarios/**` exige admin **mesmo com a flag off**.
- [x] **Etapa 5 — Frontend: login e ciclo de senha** (`876cadb`)
      `auth.js` (sessão + **interceptação do `window.fetch`**), `login.html/js`
      (login + esqueci senha), `trocar-senha.html/js` (obrigatória e voluntária),
      `redefinir-senha.html/js` (link do e-mail).
      `index.html`/`ficha.html` carregam `auth.js` ANTES do script da página.
      Cadastro segue público; `consulta`/`dashboard` exigem login e voltam via
      `index.html?tab=<destino>`. Barra `#barra-usuario` no topo.
- [x] **Etapa 6 — Frontend: restrições por papel + tela de usuários** (`13185e6`)
      Aba `usuarios` no index (classe `.somente-admin`, `hidden` por padrão,
      revelada por `aplicarPermissoesNaTela()` em script.js).
      `TABS_PROTEGIDAS = consulta/dashboard/usuarios`; `TABS_SO_ADMIN = usuarios`
      (quem forçar `?tab=usuarios` cai no menu).
      `usuarios.js` (prefixo `usr` em TUDO para não colidir com script.js):
      listar/buscar/filtrar, criar, editar, resetar senha, desbloquear.
      `switchTab` chama `window.carregarUsuarios()` — o acoplamento é por essa
      função global, registrada por usuarios.js.
      `ficha.js` agora exige sessão (`Auth.irParaLogin()` no topo).
- [x] **Etapa 7 — Ligar `app.security.enabled=true`** (`ecbdee4`)
      Padrão agora é `true`; `APP_SECURITY_ENABLED=false` fica como válvula de escape.
      Tratado: rollback do cadastro público usa DELETE (restrito) → a tela agora
      confere `res.ok` e avisa se não conseguiu desfazer tudo.
- [x] **Etapa 8 — Toggle de cadastro público** (`c2073fa`)
      `tb_configuracao` (chave/valor; chave `cadastro.aberto`; **linha ausente = ABERTO**).
      `ConfiguracaoService/Controller`: `GET /api/config/cadastro` público, `PUT` só admin.
      **`CadastroPublicoFilter`** barra POST em `/api/catequisandos|fichas|documentos|files`
      quando fechado — esconder o formulário sozinho não bloquearia um POST direto.
      Quem tem `ROLE_COORDENADOR`/`ROLE_COORDENADOR_PAROQUIAL` cadastra mesmo fechado.
      Front: aba `configuracoes` (admin) + `configuracoes.js` (prefixo `cfg`);
      `script.js` ganhou `cadastroAberto`/`aplicarEstadoCadastro()`/
      `window.definirCadastroAberto()` (usado por configuracoes.js para refletir sem reload).

- [x] **Etapa 9 — Chave de inscrição + envio transacional** (`15cde0c`)
      `tb_chave_inscricao` + `POST /api/inscricoes` (uma transação).
      **Substituiu o rollback pelo navegador** — não existe mais DELETE público.
      `/api/chaves/**` admin, exceto `GET /api/chaves/validar` (público, a tela
      confere o código antes de mostrar o formulário).
      Header `X-Chave-Inscricao`, injetado por `auth.js` (lido de `?chave=` → sessionStorage).
      Uso da chave é consumido DENTRO da transação (falha não gasta vaga).
      Front: aviso `#aviso-cadastro-bloqueado` com campo para digitar o código;
      admin gere chaves na aba Configurações (`configuracoes.js`).

## Boas práticas de segurança exigidas pelo usuário (checklist)

- [x] Senha só como hash BCrypt, nunca texto puro.
- [x] Mensagem genérica no login (não revela se o usuário existe).
- [x] 401 vs 403 distintos.
- [x] Bloqueio temporário após 5 tentativas erradas → 15 min (`AuthController`, HTTP 423).
- [x] Política de senha (`PoliticaSenha`): mín. 8, letra+dígito, ≠ username/e-mail, lista de óbvias.
- [x] Senha provisória obrigatoriamente trocada: `JwtAuthFilter` barra tudo menos
      `/api/auth/trocar-senha` e `/api/auth/me`, devolvendo `codigo: SENHA_PROVISORIA`.
- [x] Token JWT deixa de valer após troca de senha (claim `senhaEm` × `data_troca_senha` no banco).
- [x] Token também deixa de valer se a conta for desativada (filtro relê o usuário do banco).
- [x] `jwt.secret` fora do repositório → `${JWT_SECRET:}`; `JwtService` falha na subida se < 32 chars.
- [x] Token de recuperação: 32 bytes de `SecureRandom` (base64url), gravado como **SHA-256**,
      uso único (`usado_em`), validade 30 min, cada novo pedido invalida os anteriores,
      e intervalo mínimo de 2 min entre pedidos (anti-spam de e-mail).
- [x] "Esqueci a senha" responde sempre a mesma mensagem, exista o e-mail ou não.
- [x] Redefinir senha por e-mail **destrava** conta bloqueada por tentativas
      (quem provou ter o e-mail não pode ficar refém do bloqueio).
- [x] Nunca logar senha nem token (exceção consciente e única: a senha do admin inicial
      gerada pelo sistema aparece uma vez no log da primeira subida — mesmo padrão do Spring Boot).

## Bugs reais encontrados depois, em produção (referência)

- **`LocalDateTime.now()` + coluna `DATETIME`**: MySQL ARREDONDA a fração de segundo.
  O objeto em memória ficava 1s diferente do banco, o claim `senhaEm` do token não
  batia e TODA requisição dava 401. Corrigido com `.withNano(0)` em todo write de
  `dataTrocaSenha` (`bac2c9b`). **Regra: truncar segundos ao gravar data que vira claim.**
- **Preenchimento automático do Chrome** em campo de senha (localhost:8080 é
  reaproveitado entre projetos). Limpar uma vez não basta — o Chrome repõe depois.
  Solução: reaplicar em 100/350/800/1500ms e parar no primeiro `keydown`/`paste`
  (autofill não dispara esses eventos) (`fa7b6db`).
- **`password_hash` menor que 60** trunca o BCrypt em silêncio → login falha sempre.
  A migração agora amplia colunas curtas.
- **`display: grid/flex` vence `[hidden]`** — já mordeu 3 vezes (`.tabs`, `.auth-layout form`,
  `.grid`). Ao esconder algo via `hidden`, SEMPRE adicionar o par `[hidden] { display: none }`.

## Armadilhas já encontradas (não repetir)

- `Duration.between(agora, usuario.bloqueadoAte)` **não compila**: `bloqueadoAte` é
  `LocalDateTime?` e não há smart cast por causa de `estaBloqueado()`. Extrair para
  um `val` local antes de usar.
- Regra de escrita `POST /api/**` → coordenadores **bloquearia o catequista de trocar
  a própria senha**. Por isso `/api/auth/trocar-senha` tem regra `.authenticated()`
  declarada ANTES das regras por método.
- `application.properties` é latin-1: usar `sed -i` em linha específica ou `cat >>`
  com ASCII. Nunca Write/Edit no arquivo inteiro.
- **Kotlin tem comentários de bloco ANINHADOS** (Java não tem). Escrever
  `/api/usuarios/` + `**` dentro de um KDoc abre um comentário interno; o `*/`
  final fecha só o interno e o arquivo inteiro quebra com "Unclosed comment"
  na última linha. **Nunca escrever a sequência barra-asterisco-asterisco dentro
  de comentário.** Já aconteceu em `UsuarioController.kt` (`cb8466f`).
  → Rodar SEMPRE `python3 /home/claude/project/kt_comment_check.py` antes de entregar .kt.
- Referência a função de companion object pelo nome da classe (`UsuarioDTO::de`)
  é arriscada entre versões do Kotlin. Usar lambda: `.map { UsuarioDTO.de(it) }`.
- **Entidades JPA são `open`** (plugin allOpen) → **Kotlin NÃO faz smart cast em
  propriedade delas**. `x != null && usa(x)` não compila quando `x` é propriedade
  de entidade. Extrair para `val` local: `val v = x ?: return false`.
  Já mordeu 2× (`bloqueadoAte` em `bac2c9b`, `limiteUsos` em `d8fe2c3`).
  Vale para propriedade de classe; `val` local e parâmetro de função são seguros.
- `spring.mail.host=${SPRING_MAIL_HOST:}` **não** impede o Spring de criar o
  `JavaMailSender`: a propriedade existe (vazia), então `@ConditionalOnProperty` casa.
  Detectar SMTP ausente exige checar `spring.mail.host` em branco, não só o bean.
- `.auth-layout form { display: grid }` vence o `[hidden]` nativo → formulário
  continuava visível com link inválido. **Toda regra de `display` em elemento que
  será escondido via `hidden` precisa do par `[hidden] { display: none }`.**
  (Já aconteceu com `.tabs` e agora com `form`.)
- No teste headless, os botões da barra superior (`.tab-btn`) **não são clicáveis
  na tela inicial** — a barra fica oculta por design. Usar `.menu-card[data-tab=...]`.
- Endpoints de `/api/auth` que o front público usa (`esqueci-senha`, `redefinir-senha`)
  precisam de `permitAll` explícito; `trocar-senha` precisa de `.authenticated()`.
  Todos ANTES das regras genéricas por método HTTP.

## Regras de acesso (aplicar na Etapa 3, já escritas na SecurityConfig da Etapa 1)

Público (sem token), porque a tela de cadastro é aberta:
- `POST /api/auth/login`
- `GET /api/config/cadastro` (Etapa 4)
- `POST /api/catequisandos`, `POST /api/documentos`, `POST /api/fichas`, `/api/upload/**`
- `GET /api/turmas`, `GET /api/comunidades`  ← **necessários para preencher os selects do cadastro**
- estáticos: `/`, `*.html`, `*.css`, `*.js`, `*.png`

Autenticado:
- `GET` nos demais `/api/**` → qualquer tipo logado (CATEQUISTA inclusive).
- `PUT`/`DELETE` e demais `POST` → `COORDENADOR` ou `COORDENADOR_PAROQUIAL`.
- `/api/usuarios/**` e `PUT /api/config/**` → só `COORDENADOR_PAROQUIAL`.

## Estado atual

- Último commit: `15cde0c` (Etapa 9). Pendente: primeira compilação real e validação em produção.
  Pendente só do lado do usuário: rodar a migração e validar em produção.
  (ligar `app.security.enabled=true`), após o usuário validar o login rodando.
  que depende do usuário validar o login rodando contra o banco real.
- **Próximo passo: Etapa 2** (tela de login no frontend). Nada bloqueando.
- Aguardando o usuário rodar `MIGRACAO_USUARIOS.sql` + compilar do lado dele
  (não consigo compilar aqui — ver seção Verificação).

## Verificação

- **NÃO É POSSÍVEL COMPILAR NESTA SESSÃO.** Testado e confirmado:
  - `./gradlew` falha ao baixar a distro (`services.gradle.org` → proxy 403).
  - `repo1.maven.org` também dá 403 no proxy → nenhuma dependência JVM resolve.
  - Existe `gradle` em `/opt/gradle` e JDK 21, mas sem Maven Central não adianta.
  - Consequência: **todo Kotlin vai sem compilar**. Revisar à mão com atenção
    (assinaturas de API, imports, smart casts, invariância de genéricos do Kotlin)
    e avisar o usuário que a compilação é do lado dele.
  - **Rodar SEMPRE os dois verificadores antes de entregar .kt:**
    `python3 /home/claude/project/kt_comment_check.py` e
    `python3 /home/claude/project/smartcast_check.py`
  - Checagem possível aqui: `python3 /home/claude/project/kt_comment_check.py`
    (emula o aninhamento de comentários do Kotlin) + balanceamento de chaves/
    parênteses + imports não usados + caracteres não-ASCII.
    Pega erro grosseiro e de comentário; **não** pega erro de tipo.
- Sem `bcrypt` em Python nem `bcryptjs` em Node (pypi/npm também bloqueados para estes).
  Por isso o admin inicial é criado pelo próprio app (`AdminBootstrap`), não por SQL.
- Front: servir `static/` com `python3 -m http.server` + Playwright/Chromium em
  `/opt/pw-browsers/chromium`, mockando `**/api/**` via `page.route`. **Isso funciona bem** —
  usar sempre para validar as telas.

## Etapa 1 — o que foi entregue (commit `a28c555`)

Arquivos novos:
- `model/TipoUsuario.kt` — enum CATEQUISTA/COORDENADOR/COORDENADOR_PAROQUIAL,
  com `podeEditar`, `isAdmin`, `role` (= "ROLE_" + name).
- `model/Usuario.kt` — entidade `tb_usuario` (id_usuario, nome, username,
  password_hash @JsonIgnore, tipo @Enumerated(STRING), id_catequista?, id_coordenador?,
  ativo, data_criacao).
- `repository/UsuarioRepository.kt` — `findByUsername`, `existsByUsername`.
- `dto/AuthDTO.kt` — `LoginRequestDTO`, `UsuarioLogadoDTO` (inclui `podeEditar`, `admin`, `token`).
- `security/JwtService.kt` — jjwt 0.11.5 (`Jwts.builder`/`parserBuilder`), claims: idUsuario, nome, tipo.
- `security/JwtAuthFilter.kt` — **de propósito NÃO é `@Component`** (bean Filter seria
  auto-registrado no chain de servlet do Boot além do chain do Security). A SecurityConfig
  instancia com `JwtAuthFilter(jwtService)`.
- `config/SecurityConfig.kt` — csrf off, stateless, entryPoint 401 / accessDenied 403
  (o front precisa distinguir os dois), + as regras da seção acima.
- `config/AdminBootstrap.kt` — `CommandLineRunner`; se `tb_usuario` estiver vazia e
  `ADMIN_INICIAL_USERNAME`/`ADMIN_INICIAL_PASSWORD` existirem, cria o admin com BCrypt.
- `controller/AuthController.kt` — `POST /api/auth/login`, `GET /api/auth/me`.
  Resposta genérica em falha (não revela se o usuário existe).
- `MIGRACAO_USUARIOS.sql` — só `CREATE TABLE tb_usuario` (+ FKs opcionais). Nenhum ALTER.

Alterados:
- `build.gradle.kts` — `spring-boot-starter-security`, `jjwt-api` (impl+jackson como runtimeOnly).
- `application.properties` — **anexado via `cat >>` em ASCII** (arquivo é latin-1):
  `app.security.enabled=${APP_SECURITY_ENABLED:false}` e `admin.inicial.*`.

Pendências conhecidas para as próximas etapas:
- Não existe endpoint de troca de senha nem CRUD de usuários → **Etapa 3**.
- `tb_login`/`tb_permissoes` continuam órfãs (legado, intocadas) — decidir depois se remove.

## API de autenticação pronta (referência rápida para o front — Etapa 5)

| Método | Rota | Acesso | Retorno relevante |
|---|---|---|---|
| POST | `/api/auth/login` | público | `UsuarioLogadoDTO` + `token`; 401 genérico; **423** = conta bloqueada (`codigo: CONTA_BLOQUEADA`) |
| GET | `/api/auth/me` | logado | `UsuarioLogadoDTO` (sem token) |
| POST | `/api/auth/trocar-senha` | logado (qualquer tipo) | `{senhaAtual, novaSenha}` → DTO + **token novo** |
| POST | `/api/auth/esqueci-senha` | público | `{email}` → sempre a mesma mensagem |
| POST | `/api/auth/redefinir-senha` | público | `{token, novaSenha}`; 400 com `codigo: TOKEN_INVALIDO` |
| GET/POST/PUT | `/api/usuarios/**` | só admin | inclui `/{id}/resetar-senha` e `/{id}/desbloquear` |

Sinal que o front DEVE tratar: qualquer resposta **403 com `codigo: SENHA_PROVISORIA`**
significa "redirecione para a tela de troca de senha obrigatória".

`UsuarioLogadoDTO` = `idUsuario, nome, username, email, tipo, podeEditar, admin,
senhaProvisoria, token?`.

Página do link de e-mail (a criar na Etapa 5): **`redefinir-senha.html?token=...`**
(o nome já está fixado no `RecuperacaoSenhaService`).
