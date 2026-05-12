package router

import (
	"net/http"

	"catequese-escada/go-api/internal/auth"
	"catequese-escada/go-api/internal/catequisando"
	"catequese-escada/go-api/internal/catequista"
	"catequese-escada/go-api/internal/comunidade"
	"catequese-escada/go-api/internal/conhecimento"
	"catequese-escada/go-api/internal/coordenador"
	"catequese-escada/go-api/internal/documento"
	"catequese-escada/go-api/internal/evento"
	"catequese-escada/go-api/internal/ficha"
	"catequese-escada/go-api/internal/http/handlers"
	"catequese-escada/go-api/internal/http/middleware"
	"catequese-escada/go-api/internal/http/response"
	"catequese-escada/go-api/internal/permissao"
	"catequese-escada/go-api/internal/presenca"
	"catequese-escada/go-api/internal/turma"
	"catequese-escada/go-api/internal/upload"
	"catequese-escada/go-api/internal/usuario"

	"github.com/go-chi/chi/v5"
)

func New(
	jwtService *auth.JWTService,
	authService *auth.Service,
	cateqRepo *catequisando.Repository,
	fichaService *ficha.Service,
	comunidadeService *comunidade.Service,
	turmaService *turma.Service,
	eventoService *evento.Service,
	presencaService *presenca.Service,
	documentoService *documento.Service,
	catequistaService *catequista.Service,
	coordenadorService *coordenador.Service,
	conhecimentoService *conhecimento.Service,
	permissaoService *permissao.Service,
	uploadService *upload.Service,
	uploadMaxMB int64,
	usuarioService *usuario.Service,
) http.Handler {
	r := chi.NewRouter()
	r.Use(middleware.RequestLogger())
	authHandler := handlers.NewAuthHandler(jwtService, authService)
	cateqHandler := handlers.NewCatequisandoHandler(cateqRepo)
	fichaHandler := handlers.NewFichaHandler(fichaService)
	comunidadeHandler := handlers.NewComunidadeHandler(comunidadeService)
	turmaHandler := handlers.NewTurmaHandler(turmaService)
	eventoHandler := handlers.NewEventoHandler(eventoService)
	presencaHandler := handlers.NewPresencaHandler(presencaService)
	documentoHandler := handlers.NewDocumentoHandler(documentoService)
	catequistaHandler := handlers.NewCatequistaHandler(catequistaService)
	coordenadorHandler := handlers.NewCoordenadorHandler(coordenadorService)
	conhecimentoHandler := handlers.NewConhecimentoHandler(conhecimentoService)
	permissaoHandler := handlers.NewPermissaoHandler(permissaoService)
	uploadHandler := handlers.NewUploadHandler(uploadService, uploadMaxMB)
	usuarioHandler := handlers.NewUsuarioHandler(usuarioService)
	authMW := middleware.JWTAuth(jwtService)

	// Public auth endpoints (same path contract, incremental implementation).
	r.Post("/api/auth/login", authHandler.Login)
	r.Post("/api/auth/refresh", authHandler.Refresh)
	r.Post("/api/auth/logout", authHandler.Logout)
	r.Post("/api/auth/password-reset/request", authHandler.RequestPasswordReset)
	r.Post("/api/auth/password-reset/confirm", authHandler.ConfirmPasswordReset)
	r.Get("/api/auth/health", authHandler.Health)

	// Hardened area: every non-public API route requires JWT.
	r.Group(func(api chi.Router) {
		api.Use(authMW)
		api.Get("/api/auth/validate", authHandler.Validate)

		api.Route("/api/catequisandos", func(c chi.Router) {
			c.Get("/", cateqHandler.GetAll)
			c.Get("/{id}", cateqHandler.GetByID)
			c.Post("/", cateqHandler.Create)
			c.Put("/{id}", cateqHandler.Update)
			c.Delete("/{id}", cateqHandler.Delete)
		})

		api.Route("/api/fichas", func(f chi.Router) {
			f.Get("/", fichaHandler.GetAll)
			f.Get("/{id}", fichaHandler.GetByID)
			f.Post("/", fichaHandler.Create)
			f.Put("/{id}", fichaHandler.Update)
			f.Delete("/{id}", fichaHandler.DeleteByID)
			f.Delete("/catequisando/{catequisandoId}", fichaHandler.DeleteByCatequisandoID)
		})

		api.Route("/api/comunidades", func(c chi.Router) {
			c.Get("/", comunidadeHandler.GetAll)
			c.Get("/{id}", comunidadeHandler.GetByID)
			c.Post("/", comunidadeHandler.Create)
			c.Put("/{id}", comunidadeHandler.Update)
			c.Delete("/{id}", comunidadeHandler.Delete)
		})

		api.Route("/api/turmas", func(t chi.Router) {
			t.Get("/", turmaHandler.GetAll)
			t.Get("/{id}", turmaHandler.GetByID)
			t.Post("/", turmaHandler.Create)
			t.Put("/{id}", turmaHandler.Update)
			t.Delete("/{id}", turmaHandler.Delete)
		})

		api.Route("/api/eventos", func(e chi.Router) {
			e.Get("/", eventoHandler.GetAll)
			e.Get("/{id}", eventoHandler.GetByID)
			e.Post("/", eventoHandler.Create)
			e.Put("/{id}", eventoHandler.Update)
			e.Delete("/{id}", eventoHandler.Delete)
		})

		api.Route("/api/presencas", func(p chi.Router) {
			p.Get("/", presencaHandler.GetAll)
			p.Get("/{id}", presencaHandler.GetByID)
			p.Post("/", presencaHandler.Create)
			p.Put("/{id}", presencaHandler.Update)
			p.Delete("/{id}", presencaHandler.Delete)
		})

		api.Route("/api/documentos", func(d chi.Router) {
			d.Get("/", documentoHandler.GetAll)
			d.Get("/{id}", documentoHandler.GetByID)
			d.Post("/", documentoHandler.Create)
			d.Put("/{id}", documentoHandler.Update)
			d.Put("/{id}/status", documentoHandler.UpdateStatus)
			d.Delete("/{id}", documentoHandler.Delete)
		})

		api.Route("/api/catequistas", func(c chi.Router) {
			c.Get("/", catequistaHandler.GetAll)
			c.Get("/{id}", catequistaHandler.GetByID)
			c.Post("/", catequistaHandler.Create)
			c.Put("/{id}", catequistaHandler.Update)
			c.Delete("/{id}", catequistaHandler.Delete)
		})

		api.Route("/api/coordenadores", func(c chi.Router) {
			c.Get("/", coordenadorHandler.GetAll)
			c.Get("/{id}", coordenadorHandler.GetByID)
			c.Post("/", coordenadorHandler.Create)
			c.Put("/{id}", coordenadorHandler.Update)
			c.Delete("/{id}", coordenadorHandler.Delete)
		})

		api.Route("/api/conhecimentos", func(c chi.Router) {
			c.Get("/", conhecimentoHandler.GetAll)
			c.Get("/{id}", conhecimentoHandler.GetByID)
			c.Post("/", conhecimentoHandler.Create)
			c.Put("/{id}", conhecimentoHandler.Update)
			c.Delete("/{id}", conhecimentoHandler.Delete)
		})

		api.Route("/api/permissoes", func(p chi.Router) {
			p.Get("/", permissaoHandler.GetAll)
			p.Get("/{id}", permissaoHandler.GetByID)
			p.Post("/", permissaoHandler.Create)
			p.Put("/{id}", permissaoHandler.Update)
			p.Delete("/{id}", permissaoHandler.Delete)
		})

		api.Post("/api/files", uploadHandler.Upload)
		api.Post("/api/files/batch", uploadHandler.UploadBatch)

		api.Route("/api/usuarios", func(u chi.Router) {
			u.Use(middleware.RequireRole("COORDENADOR_PAROQUIAL"))
			u.Get("/", usuarioHandler.GetAll)
			u.Get("/{id}", usuarioHandler.GetByID)
			u.Get("/email/{email}", usuarioHandler.GetByEmail)
			u.Post("/", usuarioHandler.Create)
			u.Put("/{id}", usuarioHandler.Update)
			u.Patch("/{id}/toggle-ativo", usuarioHandler.ToggleAtivo)
			u.Delete("/{id}", usuarioHandler.Delete)
		})

	})

	r.NotFound(func(w http.ResponseWriter, _ *http.Request) {
		response.Error(w, http.StatusNotFound, "Recurso não encontrado")
	})

	return r
}
