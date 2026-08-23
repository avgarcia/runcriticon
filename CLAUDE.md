# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Reglas globales del monorepo

Este archivo recoge las **reglas globales** que aplican a todo el monorepo. Reglas específicas por capa:

- Backend (Kotlin + Spring Boot) → [`backend/CLAUDE.md`](backend/CLAUDE.md)
- Frontend (Angular) → [`frontend/CLAUDE.md`](frontend/CLAUDE.md)

## Estado del proyecto

**Hito H0 en curso** — arranque del *esqueleto andante*. Las 16 ADRs están **Aceptadas** tras la revisión Nivel 1 (mayo 2026) y la documentación operativa está completa (guía de módulo + 5 subdocumentos por tema). Empieza la fase de programación; la mayoría de PRs ahora **producen código**, no documentación.

Ver [`docs/plan-implementacion-mvp.md`](docs/plan-implementacion-mvp.md) para el estado del hito y los 6 bloques.

## Cómo opera Claude en este repo

**Fuente de verdad de cualquier decisión arquitectónica**: los 16 ADRs en [`docs/adr/`](docs/adr/), aceptados a Nivel 1. Cualquier cambio que rompa una sub-decisión de un ADR aceptado requiere PR de cambio del ADR encadenada. Si una guía operativa contradice un ADR, **gana el ADR**.

**Patrón de trabajo de revisión + aceptación**:

- Revisión de un ADR → PR `feature/revision-adr-NNNN` → merge.
- Aceptación → PR `feature/acepta-adr-NNNN` con cambio Propuesto→Aceptado + actualización del README de ADRs.
- Para subdocumentos de arquitectura: `feature/subdoc-{tema}`.
- Para bloques de H0: `feature/h0-bN-{nombre}`.

Cada PR lleva un *"checklist alineado con ADRs"* (ver `.github/PULL_REQUEST_TEMPLATE.md`). El equipo no aprueba ADRs nuevos sin pasar por el patrón Nivel 1 documentado en [`docs/adr/template.md`](docs/adr/template.md).

**Patrón de preguntas multi-tanda**: para decisiones complejas, Claude usa la herramienta `AskUserQuestion` con varias preguntas (max 4) por tanda. Antes de empezar una tanda, Claude resume las decisiones cerradas y lo que falta.

## Comandos disponibles

Hoy operativos (Bloque H0.1 completado):

```bash
# Sitio navegable de ADRs (log4brains) — http://localhost:4004
npm install
npm run adr:preview
npm run adr:build

# Desarrollo local: Postgres 16 + MailHog
docker-compose up -d
docker-compose ps               # ver healthcheck
docker-compose down

# GitHub CLI (gh) está disponible en /c/Program Files/GitHub CLI/gh en Windows
# git operations vía Bash con paths absolutos /c/Users/pw-avidal/projects/runcriticon
```

Operativos sobre el código ya existente (H0 — módulos `identidad`, `clubtaxonomia`, `planificacion` y `auditoria` implementados; `seguimiento` en scaffold; frontend Angular en marcha):

```bash
# Backend
./gradlew build                 # build + tests + ArchUnit + Modulith
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun   # sin el perfil no hay datasource y no arranca
./gradlew test --tests "*AutenticarUsuarioTest"
./gradlew detekt ktlintCheck
./gradlew contractTest          # tests de contrato JSON Schema (CI dedicado)

# Frontend
cd frontend && npm install
npm start                       # ng serve
npm run build
npm test                        # Jest
npm run e2e                     # Playwright + axe-core
npm run lint                    # ESLint + Prettier
```

## Stack técnico decidido (ADRs aceptados)

| Capa | Tecnología | ADR |
|---|---|---|
| Backend | Kotlin + Spring Boot 4.x + Spring Modulith 2.x + **Arrow-kt** (Either + Raise DSL) | 0001, 0007, 0008 |
| Runtime JVM | **GraalVM CE 25** runtime (compila a target Java 21 — límite detekt/Kotlin) modo JIT, no `native-image` | 0016 |
| Frontend | Angular 22 + **spartan.ng** (brain + helm) + **Tailwind CSS v4** + **Signals + servicios** (sin NgRx) + esbuild | 0001, 0012 |
| Cliente HTTP frontend | **Generado desde OpenAPI** (`ng-openapi-gen`) | 0001 D10, 0012 D12 |
| Persistencia | PostgreSQL 16 (RDS) con **un esquema por módulo**, Flyway, **JSONB** para value objects | 0004 |
| Cloud | AWS `eu-west-1` (App Runner + RDS + SSM + AMP + AMG + X-Ray + CloudWatch Logs) | 0006, 0011 |
| CI/CD | GitHub Actions con OIDC contra AWS, imagen Docker en GHCR | 0010 |
| Email | Postmark (no SES, no SMTP propio) | 0005 |
| Configuración | Spring `Environment` lee env vars; **prohibido SDK de AWS en código de módulo** | 0013 |
| Testing | JUnit 5 + Kotest assertions + MockK + Testcontainers + ArchUnit + Playwright + axe-core | 0010 |

La SPA se sirve desde la aplicación Spring Boot bajo el mismo origen (`/api`). Cookie de sesión `httpOnly` first-party. Sin CORS, sin SSR, sin GraphQL.

## Arquitectura — eventos-first, hexagonal, autorización en tres capas

### Cinco módulos (bounded contexts de Spring Modulith)

```
Identidad y acceso  → solo publica eventos
Club y taxonomía    → consume desde Identidad
Planificación       → consume desde Club + Identidad
Seguimiento         → consume desde Planificación + Club + Identidad
Auditoría           → consume eventos AccesoDenegado/AccesoADatosSensibles de TODOS los módulos
```

**Ninguna llamada síncrona cruza módulos**. Toda la comunicación es por **integration events** publicados al outbox de Spring Modulith (`event_publication` en el mismo PostgreSQL). Cada módulo mantiene **proyecciones locales** alimentadas por eventos.

### Convenciones críticas (todas verificables por ArchUnit)

- **Paquete raíz**: `com.runcriticon`. Cada módulo cuelga directo (`com.runcriticon.identidad`, `com.runcriticon.planificacion`, etc.).
- **4 sub-paquetes por módulo**: `domain/`, `application/`, `infrastructure/`, `api/` (este último para integration events públicos).
- **Núcleo compartido**: `com.runcriticon.shared.autorizacion` con `Principal`, `Role`, `AuthorizationMatrix`.
- **`domain` puro**: imports prohibidos de Spring/JPA/Jackson/SDK AWS. Arrow-kt **sí** permitido.
- **Errores como `Either<XxxError, T>` con Raise DSL**, nunca como excepción de dominio. Excepciones reservadas para framework.
- **`XxxError` por módulo** (`PlanificacionError`, `IdentidadError`, …) — sin núcleo de errores compartido. Variantes comunes: `Forbidden`, `NotFound`, `InvalidInput`, `Conflict`, `ProjectionStale`.
- **Typed IDs** como `value class UUID v7` (`PlanId`, `ClubId`, …). Nunca `String` ni `UUID` sueltos.
- **Integration events** implementan `IntegrationEvent` con 6 campos obligatorios + `traceparent: String?` opcional (W3C Trace Context). JSON Schema versionado en `schemas/{modulo}/{evento}-v{N}.json`.
- **Cada caso de uso es `@ApplicationService`** (anotación propia que extiende `@Service`) y **consulta la `AuthorizationMatrix`** del módulo antes de la operación, o se declara exento a nivel de clase con `@NoAuthRequired`/`@AuthenticatedOnly`. ArchUnit lo verifica en CI.
- **Cada `@Repository` declara `@AuthScope(Scope.X, ...)` o `@NoAuthScope`** (con justificación); el filtro (`club_id`, relaciones del principal) va en la firma del método y en su query. Un aspecto (`AuthScopeEnforcementAspect`) **verifica** en runtime que el `clubId` recibido coincide con el del principal, fail-closed.
- **Listeners en `application/listeners/`** con `@ApplicationModuleListener`, idempotentes vía tabla `{modulo}.evento_procesado(listener, event_id) UNIQUE`, restauran el MDC con `MdcRestorerForEvents.restore(...)` / `finally { clear() }`.
- **Proyecciones locales** con columnas `last_processed_event_id` y `last_processed_event_ts` para el cálculo de `projection_lag_seconds` (ADR-0009 D9 fail-closed a 60 s).
- **Cada `@Entity` JPA declara `@RgpdCategory(Category.X)`** (PII_PRIMARIA, AUDITORIA_*, OUTBOX, BACKUPS, LOGS_OPERATIVOS, SIN_PII). ArchUnit lo verifica.
- **Cada módulo con PII tiene `StudentDeletionListener`** obligatorio que aplica borrado mixto: físico para PII primaria, anonimización para auditoría (cruce ADR-0014 D6).
- **Métricas obligatorias** por módulo en bean `{Modulo}Metrics` con `MeterRegistry`, tags controlados (`module`, `endpoint`, `event_type`, `listener`); cardinalidad alta prohibida (`user_id`, path con IDs).

### Comunicación con la UI

- Cookie de sesión `httpOnly`, `SameSite=Lax`, `Secure`. **Frontend nunca lee tokens** de JS, nunca usa `localStorage` para auth.
- **`/me/permissions`** es ayuda de UX para ocultar botones — **nunca barrera**. El backend autoriza cada petición (ADR-0009 regla de oro).
- Errores 4xx del backend traen body estructurado `{ code, field?, message }`; el frontend traduce `code` a mensaje localizado (ADR-0012 D19).

## Lenguaje ubicuo

El **glosario** ([`docs/glosario.md`](docs/glosario.md), autoritativo) es la lengua ubicua del **negocio** en **castellano**: los términos del discovery (`alumno`, `entrenador`, `grupo`, `plan`, `sesion`, `reporte`, `tag`, `ritmo`, `marca`, `personalizacion`) son el vocabulario compartido negocio↔código. **No impone castellano a los identificadores de código.**

**Regla de idioma de los identificadores** — única y sin ambigüedad; la fija [`ADR-0008 D4`](docs/adr/0008-arquitectura-hexagonal-y-ddd.md) y la verifica `NamingConventionArchTest` en CI:

- **Inglés** — todos los identificadores de código Kotlin/TypeScript: clases, interfaces, funciones, propiedades y sub-paquetes técnicos (`domain`, `application`, `infrastructure`, `api`, `persistence`, `security`, `model`, …).
- **Castellano (frontera deliberada)** — paquetes raíz de bounded context (`identidad`, `clubtaxonomia`, `planificacion`, `seguimiento`, `auditoria`, `shared.autorizacion`); identificadores SQL (esquemas, tablas, columnas) y **valores de enum persistidos** (`ENTRENADOR`, `ALUMNO`, `ACTIVO`, …); **textos de UI** (i18n, ADR-0012 D9). El puente lo hacen `@Table(name=…)` / `@Column(name=…)`.

## Mapa de documentación

### Decisiones de arquitectura (fuente de verdad)

- [`docs/adr/`](docs/adr/) — 16 ADRs Aceptados a Nivel 1.
- [`docs/adr/README.md`](docs/adr/README.md) — índice navegable.
- [`docs/adr/template.md`](docs/adr/template.md) — plantilla para nuevos ADRs con patrón Nivel 1.
- [`docs/adr/0015-temas-aplazados-fuera-del-mvp.md`](docs/adr/0015-temas-aplazados-fuera-del-mvp.md) — **índice maestro consolidado de aplazamientos** con disparadores. Para responder *"¿qué queda fuera del MVP y cuándo se reabre?"*.

### Guía operativa (espejo aplicado de los ADRs)

- [`docs/arquitectura/estructura-de-un-modulo.md`](docs/arquitectura/estructura-de-un-modulo.md) — guía principal con ejemplo Kotlin completo de `PlanSemanal`, checklist al crear un módulo.
- [`docs/arquitectura/persistencia.md`](docs/arquitectura/persistencia.md) — esquema por módulo, JSONB, Konvert, migraciones Flyway, snapshots.
- [`docs/arquitectura/testing-de-modulos.md`](docs/arquitectura/testing-de-modulos.md) — pirámide, Testcontainers, ArchUnit, acceso cruzado, contrato JSON Schema.
- [`docs/arquitectura/rgpd-en-modulos.md`](docs/arquitectura/rgpd-en-modulos.md) — `@RgpdCategory`, `StudentDeletionListener`, `@AuditaAcceso`, función SQL `anonimiza_evento_auditoria`.
- [`docs/arquitectura/observabilidad-por-modulo.md`](docs/arquitectura/observabilidad-por-modulo.md) — MDC, métricas obligatorias por capa, traceparent, health checks custom.
- [`docs/arquitectura/configuracion-y-secretos-en-modulos.md`](docs/arquitectura/configuracion-y-secretos-en-modulos.md) — `@ConfigurationProperties`, convención SSM, runbooks de rotación.

### Producto y discovery

- [`docs/vision.md`](docs/vision.md) — visión, alcance mono-club, no-objetivos.
- [`docs/glosario.md`](docs/glosario.md) — lenguaje ubicuo (autoritativo).
- [`docs/backlog.md`](docs/backlog.md) — funcionalidades MoSCoW.
- [`docs/risks.md`](docs/risks.md) — riesgos con cruce a sub-decisiones de ADRs.
- [`docs/plan-implementacion-mvp.md`](docs/plan-implementacion-mvp.md) — fases H0/H1/H2/H3, principios, válvula de escape.
- [`docs/personas/`](docs/personas/), [`docs/journeys/`](docs/journeys/), [`docs/research/`](docs/research/), [`docs/wireframes/`](docs/wireframes/).

### Operación

- [`docs/runbooks/`](docs/runbooks/) — runbooks operativos (catálogo previsto en su README).
- [`infrastructure/terraform/`](infrastructure/terraform/) — IaC de AWS con bootstrap manual del state.
- [`schemas/`](schemas/) — JSON Schemas de integration events versionados.

## Índice de ADRs (los 16 Aceptados)

| # | Título | Por qué importa al escribir código |
|---|---|---|
| 0001 | Stack: Spring Boot + Angular | Cookie de sesión first-party mismo origen, sin GraphQL |
| 0002 | Modelo de datos: tags + `Ritmo` `Absoluto \| Relativo` + marcas privadas | Ningún ritmo como string plano |
| 0003 | Auth invite-only | Magic link 15 min, Argon2id, CSRF activado, sesión httpOnly, **rol único por usuario en MVP** |
| 0004 | PostgreSQL un esquema por módulo | Sin FK cruzado entre esquemas |
| 0005 | Email Postmark + outbox + plantillas en código | Adaptador tras puerto `EmailSender` (`application/ports`) |
| 0006 | Infra AWS `eu-west-1` + App Runner + RDS + tagging + budgets | `club_id` desde día 1; subdominio por club al multi-club |
| 0007 | Monolito modular events-first + outbox Spring Modulith | 5 reintentos + DLQ + alarma + republicación admin |
| 0008 | Hexagonal + DDD + `Either<XxxError, T>` + dominio puro | Arrow-kt permitido en domain; require/check para precondiciones imposibles |
| 0009 | Autorización RBAC + nivel de objeto + `club_id` | Aspecto `@AuthScope`, ArchUnit obligatorio, `/me/permissions`, distinción auditoría identidad vs autorización |
| 0010 | CI/CD GitHub Actions + GHCR + OIDC + quality gates | Trunk-based, merge commits, mutation testing nightly |
| 0011 | Observabilidad AMP + AMG + X-Ray + CloudWatch Logs | OpenTelemetry neutral, MDC con `module` + `trace_id`, IP truncada en logs |
| 0012 | Frontend spartan.ng + Tailwind v4 + Signals + WCAG 2.1 AA + Jest + Playwright | OpenAPI client generado; helm copiados en `src/app/ui/`; sin Material |
| 0013 | Configuración + secretos en SSM `SecureString` | Convención `/runcriticon/{env}/{component}/{name}`, prohibido SDK AWS en código de módulo |
| 0014 | RGPD: 6 categorías + borrado mixto + consentimiento explícito Art. 9.2.a | Cada tabla con `@RgpdCategory`, módulo con PII tiene `StudentDeletionListener` |
| 0015 | Índice maestro de aplazamientos | Mapa único: qué queda fuera del MVP y cuándo se reabre |
| 0016 | Runtime GraalVM CE 25 modo JIT (compila a target 21) | NO `native-image` en MVP (invariante anti-confusión D9) |

## Notas operativas para Claude Code

### Tooling de Claude en este repo (`.claude/`)

Configuración propia de Claude Code en [`.claude/README.md`](.claude/README.md) (índice completo ahí):

- **Hooks que BLOQUEAN** (`exit 2`) — si una edición se rechaza, normalmente es uno de estos, no un error:
  - `bloqueo-adr-aceptado.sh`: no se edita un ADR **Aceptado** salvo en rama `feature/revision-adr-NNNN`.
  - `bloqueo-sensibles.sh`: no se edita `.env`, `*.tfvars` reales ni `secrets.yaml` (sí los `*.example`).
- **Skills**: `/adr-review`, `/module-scaffold`, `/integration-event-creator`, `/runbook-generator`, `/flyway-migration-checker`, `/spring-modulith-debug`.
- **Agents de revisión**: `module-architecture-reviewer`, `idor-hunter`, `event-contract-reviewer`, `adr-coherence-scanner` (tras tocar módulo / eventos / ADRs).

- **Repositorio**: `avgarcia/runcriticon`. Path local: `/c/Users/pw-avidal/projects/runcriticon` (Windows). `gh` CLI en `/c/Program Files/GitHub CLI/gh`. Las herramientas Bash/Edit/Write trabajan con rutas absolutas.
- **Worktrees**: existen worktrees de Claude en `.claude/worktrees/` (no tocar; están en `.gitignore`).
- **Fuente de verdad = `origin/main`**: un worktree de `.claude/worktrees/` puede ir muy por detrás de `main` (sin código ni CLAUDE.md). Comprueba el estado real con `git show origin/main:<archivo>` o `git ls-tree -r origin/main`, no con el HEAD del worktree.
- **Cambios que van a `main` sin tocar el checkout del usuario** (puede tener cambios sin commitear): worktree desechable desde `origin/main` — `git worktree add -b feature/{slug} <path> origin/main` → commit/push/PR → `git worktree remove --force`.
- **PRs**: el patrón es `feature/{tipo}-{slug}` → PR con resumen + cruces a ADRs + `🤖 Generated with [Claude Code]` al final del body + `Co-Authored-By: Claude Opus 4.8` en el commit.
- **PROHIBIDO hacer commit directo a `main`**, sin excepción. Todo cambio va en rama `feature/{tipo}-{slug}` y entra por PR. Esto incluye fixes triviales, bumps de dependencias y cambios de un fichero.
- **No mergear sin confirmación explícita** del usuario, excepto cuando el patrón previo de la sesión ya esté establecido y el usuario diga *"lanzalo"*, *"mergea"* o equivalente.

### Gestión de tareas / flujo Linear

- Al completar cualquier tarea de Linear (`LAL-xxx`), sincroniza el estado **en Linear y en `TASKS.md` en el mismo paso** — nunca solo uno de los dos.

### Flujo Git

- Regla de "prohibido commit directo a `main`" en la sección de arriba. Además: **antes de dar por hecho que la CI va a correr, confirma que los commits están realmente pusheados** (`git status` / `git log origin/<rama>..HEAD`) — no asumas el push solo porque el commit se creó localmente.

### Worktrees — verifica la ruta al editar

- Al editar ficheros dentro de un worktree (`.claude/worktrees/...`), **verifica que la ruta que estás tocando es la del worktree y no la del checkout principal** antes de escribir. El cwd de Bash es la raíz del repo, no el worktree — hay que prefijar con `cd <worktree> &&` o usar rutas absolutas al worktree.

### Notas del entorno Windows

- El backend usa **Gradle** (`backend/gradlew`, `backend/gradlew.bat`) — no hay `mvnw` en el repo, no buscar `mvn` en el PATH.
- Toolchain del backend: **GraalVM CE 21** (`backend/build.gradle.kts`, `languageVersion = 21`, ADR-0016 D7), descargado automáticamente por Gradle en `C:\Users\avidal\.gradle\jdks\graalvm_community-21-amd64-windows.2\`. `keytool` de esa toolchain: `C:\Users\avidal\.gradle\jdks\graalvm_community-21-amd64-windows.2\bin\keytool.exe` (usar ese, no el `keytool` de otro JDK instalado, para que los certificados queden en el truststore que usa el build).
- `gh` CLI confirmado en `C:\Program Files\GitHub CLI\gh.exe` (`gh version 2.96.0`). En Bash, `gh` a secas resuelve a esa misma ruta vía PATH, pero `gh pr create`/`gh pr list` fallan igualmente por el hook de reescritura de comandos — invocar siempre por ruta completa (ver memoria `gh-cli-ruta-completa`).
- Para ejecuciones de tests en segundo plano, usar **PowerShell por defecto**: Bash/MSYS tiene problemas de loopback que pueden colgar o dar falsos negativos.

Antes de dar cualquier recomendación final:
- Muestra tu razonamiento completo paso a paso
- Enumera explícitamente cada suposición
- Indica las incertidumbres y los niveles de confianza (bajo/medio/alto)
- Solo entonces presenta la respuesta definitiva

## graphify

Este proyecto tiene un grafo de conocimiento local en `graphify-out/` (skill personal de cada dev, no versionada — ver `.claude/README.md`) con god nodes, estructura de comunidades y relaciones cruzadas entre ficheros.

Reglas:
- Para preguntas sobre el código, ejecuta primero `graphify query "<pregunta>"` si existe `graphify-out/graph.json`. Usa `graphify path "<A>" "<B>"` para relaciones y `graphify explain "<concepto>"` para conceptos concretos. Devuelven un subgrafo acotado, normalmente mucho más pequeño que `GRAPH_REPORT.md` o un grep en crudo.
- Si existe `graphify-out/wiki/index.md`, úsalo para navegación amplia en vez de explorar el código fuente directamente.
- Lee `graphify-out/GRAPH_REPORT.md` solo para revisión de arquitectura de alto nivel, o cuando `query`/`path`/`explain` no den suficiente contexto.
- Tras modificar código, ejecuta `graphify update .` para mantener el grafo al día (solo AST, sin coste de LLM). Si tienes el hook post-commit instalado (`graphify hook install`, per-máquina — ver abajo), esto ya ocurre automáticamente en cada commit.
- Si `graphify-out/graph.json` no existe todavía en tu máquina, genera el grafo primero con `/graphify .` antes de que estas reglas apliquen.
