# Runcriticon

Aplicación para que un **club de running amateur** gestione los entrenos de sus grupos: el admin del club organiza entrenadores y alumnos por grupos; los entrenadores publican planes semanales al grupo; los alumnos siguen el plan y reportan.

> **Alcance del MVP**: **un único club**. No es multi-tenant. Los usuarios se dan de alta por el admin del club (no hay signup público). Los planes se asignan a **grupos**, no a alumnos individuales.

> **Estado actual**: **Hito H0** (esqueleto andante) en curso, con código de aplicación ya en `main` (ver [Estado del Hito H0](#estado-del-hito-h0)). Las decisiones de arquitectura están **cerradas**: 17 ADRs aceptados a Nivel 1.

> **¿Primer día?** Sigue la guía de arranque [`docs/onboarding.md`](docs/onboarding.md): backend + frontend en local y primer login en menos de 30 minutos.

## Estructura del monorepo

```
├── api/
│   └── openapi.yaml          ← contrato de la API (contract-first, ADR-0001 D10)
├── backend/                  ← Kotlin + Spring Boot 4 + Spring Modulith
├── frontend/                 ← Angular 22 + spartan.ng + Tailwind CSS v4
├── infrastructure/
│   └── terraform/            ← IaC: AWS eu-west-1 (mono-tenant)
├── schemas/                  ← JSON Schemas de integration events
├── docs/
│   ├── onboarding.md         ← guía de arranque en local para devs nuevos
│   ├── adr/                  ← 17 Architecture Decision Records aceptados
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
| **Nuevo en el proyecto** | [`docs/onboarding.md`](docs/onboarding.md) (arranque en local), [`docs/vision.md`](docs/vision.md), [`docs/glosario.md`](docs/glosario.md), [`docs/adr/README.md`](docs/adr/README.md) |
| **Programador backend** | [`docs/arquitectura/estructura-de-un-modulo.md`](docs/arquitectura/estructura-de-un-modulo.md) + 5 subdocumentos |
| **Programador frontend** | ADR-0012 (spartan.ng + Tailwind v4 + Signals), ADR-0001 (cookie first-party), ADR-0009 D18 (`/me/permissions`) |
| **Infra / DevOps** | [`infrastructure/terraform/README.md`](infrastructure/terraform/README.md), ADR-0006, ADR-0010, ADR-0013 |
| **Producto / negocio** | [`docs/vision.md`](docs/vision.md), [`docs/backlog.md`](docs/backlog.md), [`docs/risks.md`](docs/risks.md), [`docs/plan-implementacion-mvp.md`](docs/plan-implementacion-mvp.md) |
| **Curiosidad sobre qué queda fuera del MVP** | ADR-0015 (índice maestro de aplazamientos con disparadores) |

## Comandos del repositorio

```bash
# Sitio navegable de ADRs en local
npm install
npm run adr:preview          # http://localhost:4004

# Desarrollo local: Postgres (:5432) + MailHog (:1025 SMTP, :8025 UI)
docker-compose up -d
docker-compose ps            # esperar al healthcheck de postgres
docker-compose down

# Backend (desde backend/)
./gradlew bootRun --args='--spring.profiles.active=local'   # :8080; sin el perfil local no hay datasource y no arranca
./gradlew build              # build + tests + ArchUnit + Modulith (Testcontainers: requiere Docker)
./gradlew test --tests "*ArchTest" -x checkDockerAvailable   # compila + ArchUnit sin Docker
./gradlew detekt ktlintCheck # estilo estático
./gradlew contractTest       # tests de contrato JSON Schema

# Frontend (desde frontend/; Node según frontend/.nvmrc)
npm ci
npm run gen:api              # genera el cliente HTTP desde api/openapi.yaml (no versionado)
npm start                    # ng serve en :4200, proxya /api y /actuator a :8080
npm run build
npm test                     # Jest
npm run e2e                  # Playwright + axe-core
npm run lint                 # ng lint (ESLint)
```

Detalle, primer login y problemas conocidos en Windows: [`docs/onboarding.md`](docs/onboarding.md).

## Stack técnico (ADRs aceptados)

- **Backend**: Kotlin + Spring Boot 4 + Spring Modulith + Arrow-kt; compila con GraalVM CE 21 y corre en GraalVM CE 25 (JIT, ADR-0016).
- **Frontend**: Angular 22 + spartan.ng + Tailwind CSS v4 + Signals + esbuild; cliente HTTP generado desde OpenAPI.
- **Persistencia**: PostgreSQL 16 (RDS) con esquema por módulo.
- **Cloud**: AWS `eu-west-1` (App Runner + RDS + SSM + AMP + AMG + X-Ray + CloudWatch Logs).
- **CI/CD**: GitHub Actions con OIDC contra AWS, imagen Docker en GHCR.
- **Observabilidad**: OpenTelemetry + Micrometer + Logback JSON.
- **Testing**: JUnit 5 + Kotest + MockK + Testcontainers + Playwright + axe-core.

## Documentación

Toda la documentación arquitectónica y de producto vive en [`docs/`](docs/). Los **17 ADRs aceptados** son la fuente de verdad de cualquier decisión arquitectónica; la guía de módulo y sus 5 subdocumentos son **espejo aplicado** de los ADRs.

## Estado del Hito H0

El hito H0 se define en [`docs/plan-implementacion-mvp.md`](docs/plan-implementacion-mvp.md) (Fase 0): *un commit llega solo a `staging`, se puede iniciar sesión y se ve una pantalla*. El plan fija orden e hitos, no un tablero de estado: el seguimiento fino vive en Linear (ver la «Corrección de rumbo — 2026-08-12» del plan). Lo que hay hoy en `main`:

| Pieza | Estado en el repo |
|---|---|
| Builds | Gradle (backend) + Angular (frontend) + `backend/Dockerfile` multi-stage |
| Módulos backend | `identidad`, `clubtaxonomia`, `planificacion`, `seguimiento`, `auditoria` + núcleo `shared`, verificados con ArchUnit y Spring Modulith |
| Login | Contraseña, magic link, activación de invitación y reseteo (ADR-0003); semilla del admin en `local`/`staging` |
| CI | `.github/workflows/ci.yml`: backend, frontend, secret-scan, SAST y validación de Terraform |
| Infraestructura | Terraform con módulos `network`, `database`, `secrets`, `runtime`, `observability`, `cicd`; entornos `staging` y `localstack` |
| Despliegue continuo a `staging` | **Pendiente**: no hay workflow de CD en `.github/workflows/` — el criterio del hito H0 aún no se cumple desde el repo |
