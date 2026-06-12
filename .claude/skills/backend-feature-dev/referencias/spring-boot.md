# Spring Boot 3.x — prácticas del proyecto

Fuente: ADR-0007 (Modulith), ADR-0009 (autorización), ADR-0011 (observabilidad), ADR-0013 (secretos), `backend/CLAUDE.md`.

## Inyección de dependencias

- **Solo por constructor.** Sin `@Autowired` por campo, sin `lateinit var` para dependencias (excepción: `@Autowired lateinit var` en tests de integración, donde es la convención de Spring Test).
- Dependencias como `val` privadas — inmutabilidad también en el wiring.
- Las anotaciones de estereotipo del proyecto: `@ApplicationService` (propia, en `shared`, meta-anotada con `@Service`) para casos de uso; `@Service` plano para impls de `AutorizacionService`; `@Component` para listeners y adaptadores.

## Fronteras de transacción

- La transacción envuelve el **caso de uso** (capa application). El patrón del proyecto: la publicación del evento al outbox ocurre **en la misma transacción** que el `repositorio.guardar(...)` — Spring Modulith lo garantiza si ambos pasan por el mismo `@Transactional`.
- Nunca `@Transactional` en controllers (la transacción no debe abarcar serialización HTTP) ni en métodos individuales de repositorio (demasiado granular: pierdes atomicidad caso-de-uso).
- Cuidado con `@Transactional` en métodos `private` o llamadas self-invocation: el proxy no intercepta. Si pasa, extrae el colaborador.
- Lecturas puras: `@Transactional(readOnly = true)` — permite a Hibernate saltarse el dirty checking.

## Listeners de Spring Modulith

Esqueleto canónico (los 4 pasos, en orden):

```kotlin
@Component
class {Evento}Listener(
    private val proyeccion: {X}Projection,
    private val tracker: EventoProcesadoTracker,
) {
    @ApplicationModuleListener
    fun on(evento: {Evento}) {
        evento.traceparent?.let { TraceContextRestorer.restore(it) }          // 1. trace
        if (!tracker.marcarSiNuevo("{Evento}Listener", evento.eventId)) return // 2. idempotencia
        proyeccion.aplicar(evento)                                             // 3. efecto
        // 4. la transacción del listener envuelve 1+2+3; si falla, outbox reintenta (5x)
    }
}
```

- La idempotencia **no es opcional**: el outbox entrega at-least-once.
- Si el listener actualiza una proyección, debe actualizar `last_processed_event_id` y `last_processed_event_ts` (la política stale del AutorizacionService depende de ello).
- Fallos: 5 reintentos con backoff 1/2/4/8/16 s → DLQ implícita en `event_publication` → alarma `outbox_dlq_events > 0` → republicación admin. No añadas tu propio retry encima.

## Configuración y secretos (ADR-0013)

```kotlin
@ConfigurationProperties(prefix = "runcriticon.{modulo}.{tema}")
@Validated
data class {Tema}Properties(
    @field:NotBlank val apiKey: String,
    @field:Min(1) val timeoutSegundos: Int = 10,
)
```

- Secretos en SSM SecureString bajo `/runcriticon/{env}/{modulo}/{name}`; la app los recibe como propiedades — **ningún SDK de AWS en código de módulo**.
- Properties con defaults sensatos y validación Jakarta; falla el arranque, no el request.
- Registrar con `@EnableConfigurationProperties` o `@ConfigurationPropertiesScan` (ya configurado en la app).

## Observabilidad por feature (ADR-0011)

- **Logs**: logback JSON con MDC; `module={modulo}` ya viene puesto. No loguees PII (emails, nombres) — IDs sí.
- **Métricas de negocio nuevas**: Micrometer con tag `module`:

```kotlin
meterRegistry.counter("planes_publicados_total", "module", "planificacion").increment()
```

- Las métricas obligatorias (HTTP, outbox, `listener_failures_total`, `projection_lag_seconds`) ya están instrumentadas a nivel plataforma — solo añade las de negocio de tu feature si el módulo las declara en su `OBSERVABILIDAD.md`.
- `traceparent` W3C se propaga en HTTP automáticamente y en eventos vía el campo del `IntegrationEvent`.

## Controllers

- Delgados: validar shape del request (Bean Validation en el DTO), delegar al caso de uso, traducir el Either. Cero lógica de negocio.
- Cada handler público lleva la anotación propia `@Authorize("RECURSO:ACCION")` (capa 1 RBAC contra `MatrizDeAutorizacion`) o `@NoAuthRequired` con justificación — ArchUnit lo exige. `@PreAuthorize` de Spring Security no se usa.
- DTOs de request/response propios del adaptador (`rest/dto/`), nunca exponer agregados ni entidades JPA.
- La traducción `Either → ResponseEntity` vive en la extension `toResponse(...)` del módulo + `when` exhaustivo sobre la sealed class — al añadir una variante de error nueva, el compilador obliga a decidir su status HTTP.

## Arranque y bean lifecycle

- Nada de lógica pesada en `init {}` de beans ni `@PostConstruct` que haga I/O — retrasa el arranque y rompe con lazy initialization.
- Jobs programados: `@Scheduled` en un `@Component` de infrastructure, con lock si algún día hay más de una instancia (hoy single-instance, ADR-0006).
- Perfiles: `test` para Testcontainers; no crear perfiles nuevos por feature.
