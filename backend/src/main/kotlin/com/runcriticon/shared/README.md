# Núcleo compartido (`shared`)

No es un módulo de negocio: es el *shared kernel* que usan los cinco módulos, y un módulo `OPEN` de Spring Modulith (`SharedModule.kt`), exento de la detección de ciclos. Subpaquetes: `autorizacion` (principal, matriz, aspectos y filtros de ADR-0009), `events` (outbox, idempotencia, retraso de proyecciones), `rgpd`, `observability`, `tenancy`, `config`, `api` (errores REST y eventos) y `application`.

## Eventos publicados

Viven en `shared.api.events`, y no en el módulo que los origina, porque tienen **varios productores y un solo consumidor** (o un productor cuyo consumidor está aguas arriba). Si cada uno viviera en uno de los extremos, le impondría al otro una dependencia que forma un ciclo, y `ModulithFronterasTest` lo rechaza. Detalle en el KDoc de cada evento.

| Evento | Cuándo | Schema | Quién lo publica | Consumido por |
|---|---|---|---|---|
| `AccesoDenegado` v1 | Cualquier `Either.Left(XxxError.Forbidden)` o `ProjectionStale`, y toda denegación del motor de `@Authorize` (ADR-0009 D15-D16) | `schemas/shared/acceso-denegado-v1.json` | `identidad`, `club_taxonomia`, `planificacion` y `seguimiento` (cada uno con su `XxxAccessAuditor` o su propio `denegado(...)`), y `AuthorizeInterceptor` | `auditoria` (`AuditEventListener`) |
| `AccesoADatosSensibles` v1 | Lectura o modificación con éxito de datos de salud de un tercero, sobre un método con `@AuditAccess` (D15) | `schemas/shared/acceso-datos-sensibles-v1.json` | `shared.rgpd.AuditAccessAspect` | `auditoria` (`AuditEventListener`) |
| `LesionDeclarada` v1 | El alumno confirma el cambio de su tag `estado` al avisar de una lesión | `schemas/shared/lesion-declarada-v1.json` | `seguimiento` (`RescheduleDayCommand`) | `club_taxonomia` (`LesionDeclaradaListener`) |

Convención de schemas y de versionado en [`schemas/README.md`](../../../../../../../schemas/README.md).

## Eventos consumidos

Ninguno: `shared` no tiene listeners propios.
