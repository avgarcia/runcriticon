# Backend — Runcriticon

Backend Kotlin + Spring Boot 4 + Spring Modulith del proyecto. Reglas de capa en [`CLAUDE.md`](CLAUDE.md); arquitectura en [`../docs/arquitectura/estructura-de-un-modulo.md`](../docs/arquitectura/estructura-de-un-modulo.md) + 5 subdocumentos.

## Estado (H0 Bloque 2A)

Esqueleto de build. Compila, arranca y produce un JAR ejecutable. **Sin módulos de negocio todavía** (llegan en el Bloque 3) ni login (Bloque 5). Lo que hay:

- `RuncriticonApplication.kt` — punto de entrada con `@Modulithic`.
- `/actuator/health`, `/actuator/prometheus`, `/actuator/loggers` (ADR-0011).
- Migración Flyway del outbox de Spring Modulith (`event_publication`).
- 1 test ArchUnit (`CapasArchTest`) + 1 test de stack (`StackSmokeTest`).

## Bootstrap único: generar el Gradle Wrapper

El `gradle-wrapper.jar` es un binario y **no está commiteado**. Genera el wrapper una sola vez con un Gradle local (o el de tu IDE):

```bash
cd backend
gradle wrapper --gradle-version 9.5.1
# genera: gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.jar
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar
```

Tras esto, usa siempre `./gradlew` (no `gradle`). El `Dockerfile` y el pipeline de CI (Bloque 3/4) asumen el wrapper presente.

> Si no tienes Gradle instalado: `sdk install gradle 9.5.1` (SDKMAN) o `brew install gradle`. La toolchain de GraalVM CE 21 la descarga Foojay automáticamente al primer build (ADR-0016 D7); no hace falta instalar el JDK a mano.

## Comandos

```bash
./gradlew build                 # compila + tests + ktlint + detekt
./gradlew test                  # unit + integración (Testcontainers cuando haya)
./gradlew test --tests "*StackSmokeTest"
./gradlew contractTest          # tests de contrato JSON Schema (@Tag("contract"))
./gradlew detekt ktlintCheck    # estilo estático
./gradlew ktlintFormat          # autoformato
./gradlew bootJar               # JAR ejecutable en build/libs/
./gradlew bootRun               # arranca local en :8080 (requiere docker-compose up para la BD)
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
| Lenguaje / framework | Kotlin 2.3 + Spring Boot 4.0 | 0001 |
| Runtime | GraalVM CE 25 (JIT; build/toolchain CE 21, Foojay) | 0016 |
| Modularidad | Spring Modulith 2.0 | 0007 |
| Errores | Arrow-kt (Either + Raise DSL) | 0008 |
| Mapping | Konvert (compile-time, kapt) | 0008 |
| Persistencia | Spring Data JPA + Flyway + PostgreSQL | 0004 |
| Observabilidad | Actuator + Micrometer Prometheus + OTel + Logback JSON | 0011 |
| Testing | JUnit 5 + Kotest + MockK + Testcontainers + ArchUnit | 0010 |
| Estilo | detekt + ktlint | 0010 |

## Estructura de paquetes (a partir del Bloque 3)

```
com.runcriticon
├── shared/autorizacion        ← Principal, Rol, MatrizDeAutorizacion (ADR-0009 D6)
├── identidad/{domain,application,infrastructure,api}
├── club/...
├── planificacion/...
├── salud/...
└── auditoria/...
```

Usa la skill `/module-scaffold` para crear un módulo nuevo con todos los ítems del checklist cubiertos.

## Versiones del catálogo

Las versiones en `gradle/libs.versions.toml` están fijadas a las últimas estables de inicio de 2026. Al primer build, revisa y actualiza a la última estable de cada una (Dependabot vigila después — `.github/dependabot.yml`).
