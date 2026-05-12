package main

import (
	"context"
	"log"
	"net/http"
	"time"

	"catequese-escada/go-api/internal/auth"
	"catequese-escada/go-api/internal/catequisando"
	"catequese-escada/go-api/internal/catequista"
	"catequese-escada/go-api/internal/comunidade"
	"catequese-escada/go-api/internal/config"
	"catequese-escada/go-api/internal/conhecimento"
	"catequese-escada/go-api/internal/coordenador"
	"catequese-escada/go-api/internal/database"
	"catequese-escada/go-api/internal/documento"
	"catequese-escada/go-api/internal/evento"
	"catequese-escada/go-api/internal/ficha"
	"catequese-escada/go-api/internal/http/router"
	"catequese-escada/go-api/internal/permissao"
	"catequese-escada/go-api/internal/presenca"
	"catequese-escada/go-api/internal/turma"
	"catequese-escada/go-api/internal/upload"
	"catequese-escada/go-api/internal/usuario"
)

func main() {
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("config error: %v", err)
	}

	jwtService, err := auth.NewJWTService(cfg.JWTSecret, cfg.JWTExpiration)
	if err != nil {
		log.Fatalf("jwt config error: %v", err)
	}
	authRepo := auth.NewRepository()

	ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
	defer cancel()
	db, err := database.Connect(ctx, cfg.DBDSN)
	if err != nil {
		log.Fatalf("database error: %v", err)
	}
	defer db.Close()
	authService := auth.NewService(db, authRepo, jwtService, cfg.JWTExpiration, cfg.JWTRefreshExpiration)
	cateqRepo := catequisando.NewRepository(db)
	fichaService := ficha.NewService(ficha.NewRepository(db))
	comunidadeService := comunidade.NewService(comunidade.NewRepository(db))
	turmaService := turma.NewService(turma.NewRepository(db))
	eventoService := evento.NewService(evento.NewRepository(db))
	presencaService := presenca.NewService(presenca.NewRepository(db))
	documentoService := documento.NewService(documento.NewRepository(db))
	catequistaService := catequista.NewService(catequista.NewRepository(db))
	coordenadorService := coordenador.NewService(coordenador.NewRepository(db))
	conhecimentoService := conhecimento.NewService(conhecimento.NewRepository(db))
	permissaoService := permissao.NewService(permissao.NewRepository(db))
	var uploadService *upload.Service
	switch cfg.UploadStorage {
	case "local":
		uploadService, err = upload.NewLocalService(cfg.UploadLocalDir, cfg.UploadPublicBaseURL)
	case "gcs":
		uploadService, err = upload.NewService(cfg.GCSBucket, cfg.UploadPublicBaseURL)
	default:
		err = nil
		log.Fatalf("upload config error: storage mode unsupported: %s", cfg.UploadStorage)
	}
	if err != nil {
		log.Fatalf("upload config error: %v", err)
	}
	usuarioRepo := usuario.NewRepository(db)
	usuarioService := usuario.NewService(db, usuarioRepo)

	srv := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           router.New(jwtService, authService, cateqRepo, fichaService, comunidadeService, turmaService, eventoService, presencaService, documentoService, catequistaService, coordenadorService, conhecimentoService, permissaoService, uploadService, cfg.UploadMaxMB, usuarioService),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       15 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	log.Printf("go-api running on :%s env=%s storage=%s", cfg.Port, cfg.AppEnv, cfg.UploadStorage)
	if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatalf("server error: %v", err)
	}
}
