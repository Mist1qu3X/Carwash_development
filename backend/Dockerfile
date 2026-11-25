# syntax=docker/dockerfile:1
# Образ собирается из корня проекта (context: .. в docker-compose.yml),
# потому что в него входят и React-клиент (frontend/), и Spring Boot (backend/).

# ---------- 1. сборка React-клиента ----------
FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build -- --outDir /build/static

# ---------- 2. сборка Spring Boot ----------
FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY backend/src ./src
# собранный React кладём в static — его отдаёт сам Spring Boot
COPY --from=frontend /build/static ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- 3. образ для запуска ----------
FROM eclipse-temurin:21-jre
RUN groupadd --system carwash && useradd --system --gid carwash --no-create-home carwash
WORKDIR /app
COPY --from=backend /build/target/carwash-backend.jar app.jar
USER carwash
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
