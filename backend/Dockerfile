# Dockerfile multi-stage de Runcriticon (ADR-0006 D15, ADR-0016 D4).
# Un único desplegable: la app Spring Boot sirve los estáticos de Angular (mismo origen,
# cookie first-party — ADR-0001 D11). Runtime GraalVM CE 25 modo JIT (ADR-0016, NO native-image).
#
# Imágenes base pineadas por digest SHA256 (ADR-0010 D19): un tag como :21-alpine es mutable y
# puede reapuntarse; el comentario indica la versión legible. Dependabot (ecosistema docker,
# directory /backend) mantiene los digests actualizados.

# ---------------------------------------------------------------------------
# Stage 1 — Build del frontend Angular → estáticos
# ---------------------------------------------------------------------------
FROM node:26-alpine@sha256:e88a35be04478413b7c71c455cd9865de9b9360e1f43456be5951032d7ac1a66 AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
# El build de Angular deja los estáticos en /frontend/dist/runcriticon/browser

# ---------------------------------------------------------------------------
# Stage 2 — Build del backend (Gradle bootJar) con los estáticos embebidos.
# Compila con JDK 21: detekt 1.23.7 (tope jvm-target 22) aún no soporta jvm-target 25. El jar
# (bytecode 21) corre sin problema en el runtime GraalVM CE 25 del stage 3 (forward-compatible).
# ---------------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21@sha256:57e5c4cf8536dac4346611691c31ea4828f3ff7bbb0d3695902b234f69ed99b5 AS backend-build
WORKDIR /backend
# Caché de dependencias: copiar primero la config de build (ADR-0010 D15)
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
# Código fuente del backend
COPY backend/src ./src
COPY backend/config ./config
# Estáticos de Angular dentro del classpath de Spring Boot (servidos como SPA)
COPY --from=frontend-build /frontend/dist/runcriticon/browser ./src/main/resources/static
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------------------------------------------------------------------------
# Stage 3 — Runtime mínimo (GraalVM CE 25, usuario no-root)
# ---------------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:25@sha256:a3bdba6a313cddc89f7e1b2649140e898c68ba356994a9c317b96b01bcb9f982 AS runtime
WORKDIR /app
# Usuario no-root
RUN groupadd --system runcriticon && useradd --system --gid runcriticon runcriticon
COPY --from=backend-build /backend/build/libs/*.jar app.jar
USER runcriticon
EXPOSE 8080
# Healthcheck que App Runner puede usar (ADR-0006 D3)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD ["sh", "-c", "curl -fsS http://localhost:8080/actuator/health || exit 1"]
ENTRYPOINT ["java", "-jar", "app.jar"]
