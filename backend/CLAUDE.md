# CLAUDE.md — Backend (Kotlin + Spring Boot)
Reglas específicas del backend. Las reglas globales (arquitectura de módulos, lenguaje ubicuo, contrato OpenAPI, reglas de dominio) están en [`../CLAUDE.md`](../CLAUDE.md).

## Estado
**Hito H0 en curso** — el esqueleto andante ya tiene código. El módulo `identidad` está implementado (domain / application / infrastructure / api) junto al núcleo `shared/` (`autorizacion`, `eventos`, `rgpd`, `observabilidad`), con tests de dominio, Testcontainers y ArchUnit. La guía de referencia al crear un módulo sigue siendo `../docs/arquitectura/estructura-de-un-modulo.md`.

## Stack
- **Kotlin** con **runtime GraalVM CE 25** (Java 25 LTS) modo JIT (ADR-0016), pero **compila a target Java 21**: detekt 1.23.7 (tope `jvm-target 22`) no soporta `jvm-target 25`. Build stage Docker + toolchain Gradle + CI van en 21; el runtime stage en 25. En local `.sdkmanrc` usa Temurin 25.
- **Spring Boot 4.x** + **Spring Modulith 2.x** (ADR-0007).
- **Spring Data JPA / Hibernate** + **Flyway** (ADR-0004).
- **Testcontainers** (PostgreSQL real), **ArchUnit**, contract tests OpenAPI (ADR-0010).
- Build: **Gradle** (Kotlin DSL). Quality: **detekt** + **ktlint**.

## Comandos
El proyecto Gradle ya existe (Kotlin DSL + version catalog en `gradle/libs.versions.toml`):

```bash
./gradlew build                     # build + tests
./gradlew test                      # unit + integración (Testcontainers)
./gradlew detekt ktlintCheck        # estilo
./gradlew bootRun                   # arranca la app local
```

## Estructura hexagonal dentro de cada módulo
Cada módulo (un *bounded context* de ADR-0007) tiene tres capas con la dependencia apuntando siempre al dominio:

```
infrastructure   →   application   →   domain
(adaptadores)        (casos de uso)     (modelo + puertos + eventos)
```

- **`domain`**: clases Kotlin **puras**. Cero anotaciones de Spring/JPA/Jackson. Contiene agregados, value objects y eventos de dominio. No contiene puertos — el dominio no sabe nada de sus propias dependencias de infraestructura. Totalmente testeable sin framework.
- **`application`**: casos de uso que orquestan el dominio, **más los puertos** en `application/ports/` (interfaces de repositorio, adaptadores de salida y `AutorizacionService` del módulo). Publica y escucha eventos mediante `@ApplicationModuleListener`. **Los consumidores deben ser idempotentes** (los eventos pueden reprocesarse desde el outbox).
- **`infrastructure`**: controladores REST (adaptadores de entrada), modelo de persistencia JPA con mappers hacia/desde agregados de dominio, adaptador publicador de eventos.

**El agregado de dominio está separado de la entidad JPA** — un mapper convierte entre ambos. Este boilerplate es deliberado; nunca anotes una clase de dominio con `@Entity`, `@Component`, `@Service` ni ninguna otra anotación de framework.

ArchUnit verifica la regla de dependencias y la ausencia de imports de framework en `domain` en cada build. Consulta `../docs/arquitectura/estructura-de-un-modulo.md` para ejemplos Kotlin anotados.

## Comunicación entre módulos
Solo eventos. Una llamada síncrona cruzando un módulo es **error de arquitectura** — Spring Modulith lo detecta en los tests de límites. Cada módulo mantiene su propia proyección local del estado que necesita de otros módulos.

## Persistencia
- Un esquema PostgreSQL por módulo (`identidad`, `club_taxonomia`, `planificacion`, `seguimiento`, `auditoria`).
- **Ninguna FK cruza el límite de un módulo** — referencias entre contextos como IDs simples.
- Flyway por módulo, migraciones independientes.
- **Toda consulta filtra por `club_id`** (presente en cada tabla de dominio desde la migración 1). El filtro es responsabilidad de la capa de aplicación / repositorio.
- PostgreSQL en tests vía **Testcontainers** (no H2): se usan `JSONB`, `unaccent`, índices de expresión.

## Autorización (ADR-0009)
Cada caso de uso es `@ApplicationService` y **consulta explícitamente la matriz de autorización** (`MatrizDeAutorizacion` / `AutorizacionService`) antes de tocar el dominio — ArchUnit lo exige (`AutorizacionArchTest`). **No se usa `@PreAuthorize`.** Capas por request:

1. **RBAC** vía la matriz (`Accion` × `Rol`). Roles: `admin`, `entrenador`, `alumno`.
2. **A nivel de objeto** en el caso de uso, contra la proyección local del módulo — comprueba que el llamador tenga una relación real con el objeto (entrenador↔grupo, alumno↔plan, etc.). Previene IDOR.
3. **`@AuthScope`** en cada `@Repository` inyecta el filtro `club_id` / relaciones del principal en las queries.

La comprobación a nivel de objeto **siempre vive en `application`**, nunca en `infrastructure`.

## Autenticación (ADR-0003)
- Sin registro público — solo por invitación.
- Sesión por **cookie**: `httpOnly`, `SameSite=Lax`, `Secure`. Expiración deslizante 30 días, máximo absoluto 90 días.
- Tokens (invitación, magic link, reset de contraseña): de un solo uso, tiempo limitado, almacenados **hasheados con Argon2id**.
- **No emitir JWT al frontend.** No exponer tokens en JS.

## Reglas de dominio (implementación)
Las reglas globales están en `../CLAUDE.md`. Implementación concreta:

- **`Ritmo` es un value object** con `{tipo, valor, distancia?}` — `tipo ∈ {absoluto, pct_umbral, pct_marca_10k}`. Persistido como columnas separadas o JSONB, **nunca como string plano**.
- **Tags**: `TagKey` y `TagValue` como entidades. Los "grupos" son consultas nombradas sobre tags (`GrupoConsulta`), no listas materializadas de alumnos.
- **Publicación de plan**: emite el evento `PlanPublicado` y materializa el snapshot de membresía del grupo en ese momento. Los cambios de tags posteriores no afectan al plan publicado.

## Testing
- **Unitario de dominio**: sin DB, sin contexto Spring. Es el grueso de la pirámide.
- **Integración**: Testcontainers con PostgreSQL real. Cubre repositorios, listeners de eventos, proyecciones.
- **Contrato OpenAPI**: el backend arrancado debe cumplir la spec.
- **ArchUnit**: regla de dependencias hexagonales + ausencia de imports de framework en `domain`.
- **Spring Modulith**: tests de límites entre módulos.
- **Mutación (PITest)**: nightly, no por PR. Cobertura más estricta en `domain`.

## Code style
- **detekt** + **ktlint** en cada build.
- Nombres en **español** para los conceptos de dominio (clases, propiedades, eventos, columnas). Los nombres técnicos de paquetes (`infrastructure`, `application`, `domain`) van en inglés.
- Sin `@Autowired` por campo — inyección por constructor.
