# CLAUDE.md
Este archivo proporciona orientación a Claude Code (claude.ai/code) al trabajar con código en este repositorio. Recoge las **reglas globales** que aplican a todo el monorepo.

**Reglas específicas por capa**:
- Backend (Kotlin + Spring Boot) → [`backend/CLAUDE.md`](backend/CLAUDE.md)
- Frontend (Angular) → [`frontend/CLAUDE.md`](frontend/CLAUDE.md)

## Estado del proyecto
**Greenfield — todavía no hay código de aplicación.**
El repositorio contiene solo documentación: 15 ADR, documentos de descubrimiento, wireframes, personas y una guía de estructura de módulos. La pila tecnológica y las decisiones de arquitectura son definitivas (ADR en estado *Propuesto*, aprobadas antes del primer commit).

**Fase de trabajo actual**: discovery / diseño visual. La mayoría de peticiones producen o modifican **documentación, wireframes o prototipos HTML** en `docs/`, no código Kotlin/Angular. No generes scaffolding de aplicación a menos que se pida explícitamente.

## Comandos disponibles
Hoy el repo solo contiene documentación. Los únicos comandos activos son los del sitio de ADRs (log4brains):

```bash
npm install          # primera vez
npm run adr:preview  # servidor local del sitio de ADRs
npm run adr:build    # build estático del sitio de ADRs
```

Los comandos de backend y frontend están en sus respectivos `CLAUDE.md` (pendientes hasta que se cree el código).

## Pila tecnológica
| Capa                 | Tecnología                                                                                  |
|----------------------|---------------------------------------------------------------------------------------------|
| Backend              | Kotlin + Spring Boot 3.x, JVM ≥ 21                                                          |
| Frontend             | Angular (componentes standalone, TypeScript, carga diferida por ruta)                       |
| Persistencia         | PostgreSQL (Amazon RDS), Spring Data JPA/Hibernate, migraciones con Flyway                  |
| Cumplimiento modular | Spring Modulith                                                                             |
| Validación de build  | ArchUnit (reglas de dependencias), Testcontainers (PostgreSQL), pruebas de contrato OpenAPI |
| CI/CD                | GitHub Actions, Docker/GHCR, Terraform (AWS: App Runner + RDS)                              |

La SPA se sirve desde la aplicación Spring Boot bajo el mismo origen (`/api`). Sin SSR, sin GraphQL.

## Arquitectura

### Cuatro módulos (contextos delimitados de Spring Modulith)
```
Identidad y acceso   → solo publica eventos
Club y taxonomía     → consume desde Identidad
Planificación        → consume desde Club y taxonomía + Identidad
Seguimiento          → consume desde Planificación + Club y taxonomía + Identidad
```

**No hay llamadas síncronas entre módulos.**
Toda la comunicación entre módulos se realiza mediante eventos de dominio publicados a través del registro de eventos de Spring Modulith (outbox). Cada módulo mantiene proyecciones locales (modelos de lectura) alimentadas por esos eventos; la consistencia entre módulos es eventual.

### Topología de base de datos
Una instancia de PostgreSQL, un esquema por módulo (`identidad`, `club_taxonomia`, `planificacion`, `seguimiento`). **Ninguna FK cruza el límite de un módulo** — las referencias entre contextos se almacenan como IDs simples. Flyway gestiona las migraciones de cada módulo de forma independiente.

`club_id` está presente en todas las tablas de dominio desde la migración 1, aunque el MVP tenga un solo club (mitiga una futura reescritura multi-tenant). **Toda consulta debe filtrar por `club_id`** — el detalle de implementación está en `backend/CLAUDE.md`.

### Reglas clave del dominio
- **Los tags son entidades de primera clase.** Los grupos son consultas nombradas sobre tags, no listas libres. Nunca modeles la agrupación con columnas hardcodeadas.
- **Cada ritmo de sesión se almacena como `{tipo, valor}`** (`absoluto`, `pct_umbral`, `pct_marca_10k`), nunca como un string plano, aunque el MVP siempre use `absoluto`.
- **Snapshot al publicar**: cuando un plan se publica para un grupo, la membresía queda congelada en ese momento. Los cambios posteriores de tags no alteran los planes ya publicados.

### Autenticación y autorización (ADR-0003, ADR-0009)
- **Autenticación**: solo por invitación (sin registro público). Sesión por cookie. Detalles (cookie, expiración, tokens, hashing) en `backend/CLAUDE.md`.
- **Autorización**: dos capas por request — RBAC (`admin`, `entrenador`, `alumno`) + comprobación a nivel de objeto contra una proyección local (previene IDOR). Detalle backend e implicaciones para la UI en los respectivos ficheros.

## Lenguaje ubicuo
Los términos de dominio en el código están en **español**, alineados con el vocabulario de descubrimiento. El glosario autoritativo es `docs/glosario.md`. Términos clave: `alumno`, `entrenador`, `grupo`, `plan`, `sesión`, `reporte`, `tag`. Aplica tanto al backend (clases, columnas, eventos) como al frontend (componentes, rutas, traducciones).

## Contrato de API
La especificación OpenAPI es la fuente de verdad (contract-first). Los stubs del servidor (Kotlin) y el cliente tipado de Angular se generan a partir de la especificación. Una prueba de contrato en CI verifica que el backend en ejecución coincida con la especificación.

## Estrategia de pruebas (ADR-0010)
Cada PR debe pasar los quality gates definidos en `backend/CLAUDE.md` (dominio puro, Testcontainers, contrato, ArchUnit, Modulith) y `frontend/CLAUDE.md` (lint, unit, E2E selectivo). Las pruebas de mutación (PITest) se ejecutan cada noche; las de carga (k6/Gatling) antes de la beta.

## Git Workflow
- Crear rama 'feature branches for all changes
- Commit frecuentemente con mensajes descriptivos
- Nunca hacer push directamente a la rama principal
- Agregar y hacer commit automáticamente cuando las tareas se completen

## CI/CD
Desarrollo basado en trunk. Cada PR a `main` debe pasar todos los quality gates. Al hacer merge en `main`, se dispara el despliegue automático a `staging`. El despliegue a producción requiere aprobación manual en el entorno de GitHub.

Quality gates transversales: revisión de dependencias con Dependabot, escaneo de secretos con `gitleaks`. Los gates específicos de cada capa están en los respectivos `CLAUDE.md`.

## Documentation
- Actualizar README.md cuando se agregan nuevas características
- Crear comentarios en línea para lógica compleja
- Generar documentación de API para nuevos endpoints
- Mantener registros de cambios actualizados

## Mapa de documentación
- `docs/vision.md` — visión, alcance mono-club, objetivos del MVP.
- `docs/glosario.md` — vocabulario ubicuo (autoritativo).
- `docs/personas/` — admin del club, entrenador, alumno.
- `docs/journeys/` — recorridos clave (admin-setup, coach-runner).
- `docs/backlog.md` — funcionalidades priorizadas (MoSCoW).
- `docs/risks.md` — riesgos identificados.
- `docs/wireframes/` y `docs/diseno/` — bocetos y prototipos visuales.
- `docs/arquitectura/estructura-de-un-modulo.md` — ejemplo Kotlin anotado de un módulo hexagonal.
- `docs/plan-implementacion-mvp.md` — secuencia de entrega del MVP.
- `docs/adr/` — 15 ADRs en estado *Propuesto* (ver índice abajo).

## Índice de ADR
Todas las decisiones de arquitectura están en `docs/adr/`. Las más relevantes al escribir código:

- **ADR-0001** — Pila tecnológica (Kotlin, Spring Boot, Angular, monorepo, API contract-first)
- **ADR-0002** — Modelo de datos: tags, objeto de valor `Ritmo`, consultas de grupos
- **ADR-0003** — Autenticación invite-only (sesión por cookie, tokens hasheados con Argon2id)
- **ADR-0004** — PostgreSQL, un esquema por módulo, Flyway
- **ADR-0007** — Monolito modular, comunicación events-first
- **ADR-0008** — Arquitectura hexagonal + DDD táctico; estructura domain/application/infrastructure
- **ADR-0009** — Autorización: RBAC + nivel de objeto
- **ADR-0010** — Pipeline CI/CD y estrategia de pruebas
- **ADR-0012** — Frontend: librería de componentes (relevante al empezar UI)
