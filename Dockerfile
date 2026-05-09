# ===== Build stage (Go API) =====
FROM golang:1.25 AS builder
WORKDIR /app

# Copia apenas módulo Go para melhor cache de dependências.
COPY go-api/go.mod go-api/go.sum ./
RUN go mod download

# Copia código e gera binário estático.
COPY go-api/ ./
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o /bin/go-api ./cmd/api

# ===== Runtime stage =====
FROM gcr.io/distroless/base-debian12
WORKDIR /
COPY --from=builder /bin/go-api /go-api

# PORT é injetada em runtime; padrão da app é 8080.
EXPOSE 8080

ENTRYPOINT ["/go-api"]