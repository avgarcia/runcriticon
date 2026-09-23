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
# Major de Node alineado con frontend/.nvmrc (el que usa CI); Dependabot solo refresca el digest,
# los majors se ignoran en .github/dependabot.yml.
FROM node:22-alpine@sha256:b6f26b36c8ff49624cfdac716b8ea1138d606df02586a77d364bb5536a634f85 AS frontend-build
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json* ./
RUN npm ci
COPY frontend/ ./
# Contrato OpenAPI (ADR-0001 D10): ng-openapi-gen.json lo lee de ../api/openapi.yaml y genera el
# cliente en src/app/api/generated, que está en .gitignore — sin este paso el build no compila.
COPY api/ /api/
RUN npm run gen:api
RUN npm run build
# El build de Angular deja los estáticos en /frontend/dist/runcriticon/browser

# ---------------------------------------------------------------------------
# Stage 2 — Build del backend (Gradle bootJar) con los estáticos embebidos.
# Compila con JDK 21: detekt 1.23.7 (tope jvm-target 22) aún no soporta jvm-target 25. El jar
# (bytecode 21) corre sin problema en el runtime GraalVM CE 25 del stage 3 (forward-compatible).
# ---------------------------------------------------------------------------
FROM ghcr.io/graalvm/jdk-community:21@sha256:57e5c4cf8536dac4346611691c31ea4828f3ff7bbb0d3695902b234f69ed99b5 AS backend-build
WORKDIR /backend
# gradlew necesita xargs, que la imagen (Oracle Linux 9 slim) no trae.
RUN microdnf install -y findutils && microdnf clean all
# Caché de dependencias: copiar primero la config de build (ADR-0010 D15)
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
# Lockfiles de dependencyLocking (ADR-0010 D19): sin ellos Gradle resuelve sin fijar versiones.
COPY backend/gradle.lockfile backend/settings-gradle.lockfile ./
COPY backend/gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true
# Código fuente del backend
COPY backend/src ./src
COPY backend/config ./config
# openApiGenerate lee $rootDir/../api/openapi.yaml (ADR-0001 D10) → /api/openapi.yaml
COPY api/ /api/
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
