# Runcriticon

Aplicación para que un **club de running amateur** gestione los entrenos de sus grupos: el admin del club organiza entrenadores y alumnos por grupos; los entrenadores publican planes semanales al grupo; los alumnos siguen el plan y reportan.

> **Alcance del MVP**: **un único club**. No es multi-tenant. Los usuarios se dan de alta por el admin del club (no hay signup público). Los planes se asignan a **grupos**, no a alumnos individuales.

> **Estado actual**: arranque del **Hito H0** (esqueleto andante). Las decisiones de arquitectura están **cerradas** (16 ADRs aceptados a Nivel 1, mayo 2026) y la documentación operativa está completa. Empieza la fase de programación.

## Estructura del monorepo

```
├── backend/                  ← Kotlin + Spring Boot 3 + Spring Modulith
├── frontend/                 ← Angular 17+ con Material
├── infrastructure/
│   └── terraform/            ← IaC: AWS eu-west-1 (mono-tenant)
├── schemas/                  ← JSON Schemas de integration events
├── docs/
│   ├── adr/                  ← 16 Architecture Decision Records aceptados
│   ├── arquitectura/         ← guía de módulo + 5 subdocumentos por tema
│   ├── runbooks/             ← procedimientos operativos
│   ├── formacion/            ← planes de formación del equipo
│   ├── research/             ← discovery, entrevistas, card-sort
│   ├── wireframes/           ← validación de pantallas
│   ├── personas/             ← admin, entrenador, alumno
│   ├── journeys/             ← admin-setup, coach-runner
│   ├── glosario.md           ← lenguaje ubicuo del proyecto
│   ├── vision.md             ← visión y alcance
│   ├── backlog.md            ← funcionalidades MoSCoW
│   ├── risks.md              ← riesgos con cruce a ADRs
│   └── plan-implementacion-mvp.md
├── docker-compose.yml        ← dev local: Postgres + MailHog
├── package.json              ← log4brains (sitio navegable de ADRs)
└── .log4brains.yml
```

## Por dónde empezar

| Si eres… | Lee primero |
|---|---|
| **Nuevo en el proyecto** | [`docs/vision.md`](docs/vision.md), [`docs/glosario.md`](docs/glosario.md), [`docs/adr/README.md`](docs/adr/README.md) |
| **Programador backend** | [`docs/arquitectura/estructura-de-un-modulo.md`](docs/arquitectura/estructura-de-un-modulo.md) + 5 subdocumentos |
| **Programador frontend** | ADR-0012 (Angular Material), ADR-0001 (cookie first-party), ADR-0009 D18 (`/me/permissions`) |
| **Infra / DevOps** | [`infrastructure/terraform/README.md`](infrastructure/terraform/README.md), ADR-0006, ADR-0010, ADR-0013 |
| **Producto / negocio** | [`docs/vision.md`](docs/vision.md), [`docs/backlog.md`](docs/backlog.md), [`docs/risks.md`](docs/risks.md), [`docs/plan-implementacion-mvp.md`](docs/plan-implementacion-mvp.md) |
| **Curiosidad sobre qué queda fuera del MVP** | ADR-0015 (índice maestro de aplazamientos con disparadores) |

## Comandos del repositorio

```bash
# Sitio navegable de ADRs en local
npm install
npm run adr:preview          # http://localhost:4004

# Desarrollo local: Postgres + MailHog
docker-compose up -d
docker-compose down

# Backend (cuando exista el proyecto Gradle — Bloque 2A)
cd backend
./gradlew build              # build + tests + ArchUnit + Modulith
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun   # arranca la app local en :8080 (requiere el docker-compose de arriba)

# Frontend (cuando exista el proyecto Angular — Bloque 2A)
cd frontend
npm install
npm start                    # arranca dev server en :4200
npm test                     # tests con Jest + Playwright
```

## Stack técnico (ADRs aceptados)

- **Backend**: Kotlin + Spring Boot 3 + Spring Modulith + Arrow-kt + GraalVM CE 21 (JIT).
- **Frontend**: Angular 17+ + Material 3 + Signals + esbuild.
- **Persistencia**: PostgreSQL 16 (RDS) con esquema por módulo.
- **Cloud**: AWS `eu-west-1` (App Runner + RDS + SSM + AMP + AMG + X-Ray + CloudWatch Logs).
- **CI/CD**: GitHub Actions con OIDC contra AWS, imagen Docker en GHCR.
- **Observabilidad**: OpenTelemetry + Micrometer + Logback JSON.
- **Testing**: JUnit 5 + Kotest + MockK + Testcontainers + Playwright + axe-core.

## Documentación

Toda la documentación arquitectónica y de producto vive en [`docs/`](docs/). Los **16 ADRs aceptados** son la fuente de verdad de cualquier decisión arquitectónica; la guía de módulo y sus 5 subdocumentos son **espejo aplicado** de los ADRs.

## Estado del Hito H0

Ver [`docs/plan-implementacion-mvp.md`](docs/plan-implementacion-mvp.md). H0 se construye en 6 bloques con dependencias:

| Bloque | Contenido | Estado |
|---|---|---|
| **1** | Cimientos (monorepo + Terraform state backend) | ✅ hecho |
| **2A** | Builds (Gradle backend + Angular frontend + Dockerfile) | 🟡 en curso |
| **2B** | Infra core Terraform (VPC + RDS + SSM + observabilidad) | ⏳ |
| **3** | Backend esqueleto (4 módulos vacíos + ArchUnit + Modulith) + CI agnostic | ⏳ |
| **4** | Despliegue (App Runner + OIDC + CD workflow) | ⏳ |
| **5** | Login mínimo + pantalla post-login | ⏳ |
| **6** | Smoke test verificación H0 | ⏳ |
