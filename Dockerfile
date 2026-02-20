# ===== Build stage =====
FROM gradle:8.7-jdk21 AS builder
WORKDIR /app

# Copia só arquivos de build primeiro (melhor cache)
COPY build.gradle.kts settings.gradle.kts gradle.properties* ./
COPY gradle ./gradle

# Baixa dependências (cache)
RUN gradle dependencies --no-daemon

# Agora copia o resto do projeto
COPY . .

# Gera o JAR executável
RUN gradle clean bootJar -x test --no-daemon

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar

# Render define PORT em runtime; expomos por padrão
EXPOSE 10000

# Dica: define memória (opcional, pode remover)
# ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]