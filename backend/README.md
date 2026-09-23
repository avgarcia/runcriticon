# Backend — Runcriticon

Backend Kotlin + Spring Boot 4 + Spring Modulith del proyecto. Reglas de capa en [`CLAUDE.md`](CLAUDE.md); arquitectura en [`../docs/arquitectura/estructura-de-un-modulo.md`](../docs/arquitectura/estructura-de-un-modulo.md) + 5 subdocumentos.

## Estado (Hito H0 en curso)

Los cinco módulos de negocio tienen código (domain / application / infrastructure / api), sobre el núcleo `shared/`:

- `identidad` — login con contraseña y magic link, activación por invitación, reseteo de contraseña, sesión por cookie, gestión y supresión de usuarios.
- `clubtaxonomia` — ajustes del club, taxonomía de tags, alumnos, entrenadores y grupos.
- `planificacion` — planes semanales, publicación y personalizaciones.
- `seguimiento` — vista semanal del alumno, reporte de sesión, marcas, reajustes de día, alertas del entrenador y actividad de grupo.
- `auditoria` — registro de accesos denegados y a datos sensibles, con anonimización y retención.
- `/actuator/health`, `/actuator/prometheus` y `/actuator/loggers` (ADR-0011).
- Migraciones Flyway por módulo en `src/main/resources/db/migration/{_shared,identidad,club_taxonomia,planificacion,seguimiento,auditoria}`.
- Tests de dominio, integración con Testcontainers, ArchUnit, límites de Spring Modulith y contrato (JSON Schema de eventos + OpenAPI).

## Bootstrap

El Gradle Wrapper está commiteado (`gradlew`, `gradlew.bat`, `gradle/wrapper/`, Gradle 9.6.1): usa siempre `./gradlew`, no hace falta un Gradle local. La toolchain de GraalVM CE 21 la descarga Foojay automáticamente al primer build (ADR-0016 D7); no hace falta instalar el JDK a mano.

## Comandos

```bash
./gradlew build                 # compila + tests + ktlint + detekt
./gradlew test                  # unit + integración (Testcontainers: requiere Docker)
./gradlew test --tests "*StackSmokeTest"
./gradlew contractTest          # tests de contrato JSON Schema (@Tag("contract"))
./gradlew detekt ktlintCheck    # estilo estático
./gradlew ktlintFormat          # autoformato
./gradlew bootJar               # JAR ejecutable en build/libs/
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun   # arranca local en :8080 (requiere docker-compose up para la BD)
```

## Desarrollo local

```bash
# Desde la raíz del repo: levantar Postgres + MailHog
docker-compose up -d

# Arrancar el backend con perfil local (apunta a localhost:5432 + MailHog)
cd backend
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## Stack (versiones en `gradle/libs.versions.toml`)

| Pieza | Tecnología | ADR |
|---|---|---|
| Lenguaje / framework | Kotlin 2.4 + Spring Boot 4.1 | 0001 |
| Runtime | GraalVM CE 25 (JIT; build/toolchain CE 21, Foojay) | 0016 |
| Modularidad | Spring Modulith 2.1 | 0007 |
| Errores | Arrow-kt (Either + Raise DSL) | 0008 |
| Mapping | Konvert (compile-time, KSP) | 0008 |
| Persistencia | Spring Data JPA + Flyway + PostgreSQL | 0004 |
| Observabilidad | Actuator + Micrometer Prometheus + OTel + Logback JSON | 0011 |
| Testing | JUnit 5 + Kotest + MockK + Testcontainers + ArchUnit | 0010 |
| Estilo | detekt + ktlint | 0010 |

## Estructura de paquetes

```
com.runcriticon
├── shared/                    ← autorizacion (Principal, Role, AuthorizationMatrix — ADR-0009 D6),
│                                events, rgpd, observability, tenancy, config, api, application
├── identidad/{domain,application,infrastructure,api}
├── clubtaxonomia/...
├── planificacion/...
├── seguimiento/...
└── auditoria/...
```

Usa la skill `/module-scaffold` para crear un módulo nuevo con todos los ítems del checklist cubiertos.

## Versiones del catálogo

Las versiones viven en `gradle/libs.versions.toml` y están bloqueadas en `gradle.lockfile` (dependency locking). Dependabot propone las actualizaciones (`.github/dependabot.yml`); al subir una versión, regenera el lock con `--write-locks`.
