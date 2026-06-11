# Estructura de un módulo — guía de referencia

Esta guía baja a tierra las decisiones de los ADRs de arquitectura — **ADR-0007** (monolito modular, *events-first*), **ADR-0008** (hexagonal + DDD táctico) y **ADR-0009** (autorización) — mostrando **cómo se estructura un módulo por dentro**. Es el documento que el equipo lee el día 1.

Es **espejo aplicado** de los ADRs: cada decisión que aquí aparece está respaldada por una sub-decisión concreta (cruce `(ADR-XXXX DN)` inline). Si hay conflicto, **gana el ADR**.

> Los fragmentos de código son **ilustrativos** (Kotlin, el lenguaje de ADR-0001). El proyecto está en fase de diseño y aún no tiene código; esto es un patrón de referencia, no un módulo real. Los nombres están en **castellano**: el lenguaje ubicuo del discovery es el del código (ADR-0008).

Como ejemplo recurrente se usa el módulo **Planificación** y su agregado `PlanSemanal`.

## 1. Estructura de paquetes y carpetas

Paquete raíz del backend: **`com.runcriticon`**. Cada módulo cuelga directamente del raíz como sub-paquete.

```
backend/src/main/kotlin/com/runcriticon/
├── shared/                              ← núcleo compartido (sin nada de modulo)
│   └── autorizacion/
│       ├── Principal.kt                 ← (userId, clubId, rol)
│       ├── Rol.kt                       ← sealed class
│       ├── MatrizDeAutorizacion.kt      ← matriz fija de ADR-0009 D6
│       └── primitivas.kt                ← puedeRol(...), etc.
│
└── planificacion/                       ← un módulo (bounded context)
    ├── api/                             ← contratos PÚBLICOS (consumidos por otros módulos)
    │   └── events/
    │       ├── IntegrationEvent.kt      ← interface marker con 6 campos obligatorios
    │       └── PlanPublicado.kt         ← integration event público
    │
    ├── domain/                          ← núcleo de negocio (puro Kotlin + Arrow)
    │   ├── PlanSemanal.kt               ← agregado
    │   ├── Sesion.kt                    ← entidad
    │   ├── Ritmo.kt                     ← value object
    │   ├── ids.kt                       ← PlanId, SesionId (value class UUID v7)
    │   ├── PlanificacionError.kt        ← sealed class de errores del módulo
    │   ├── events/                      ← domain events INTERNOS al módulo
    │   │   └── SesionAnadida.kt
    │   └── ports/
    │       ├── PlanSemanalRepository.kt
    │       ├── PublicadorDeEventos.kt
    │       ├── EnviadorDeEmail.kt
    │       └── PlanificacionAutorizacionService.kt
    │
    ├── application/                     ← casos de uso + listeners
    │   ├── PublicarPlanService.kt       ← @ApplicationService
    │   ├── autorizacion/
    │   │   └── PlanificacionAutorizacionServiceImpl.kt
    │   ├── listeners/
    │   │   └── AlumnoAsignadoAGrupoListener.kt
    │   └── projections/
    │       └── MiembrosGrupoProjection.kt
    │
    └── infrastructure/                  ← adaptadores (Spring, JPA, libs)
        ├── rest/
        │   ├── PlanController.kt
        │   ├── dto/                     ← DTOs propios separados del dominio
        │   │   ├── PublicarPlanRequest.kt
        │   │   └── PlanResponse.kt
        │   └── ResultadoControllerAdvice.kt ← traduce DomainError → HTTP
        ├── persistencia/
        │   ├── PlanSemanalEntity.kt
        │   ├── PlanSemanalEntityRepository.kt   ← Spring Data JPA
        │   ├── PlanSemanalRepositoryImpl.kt
        │   └── PlanSemanalMapper.kt     ← @Konverter (Konvert)
        ├── email/
        │   └── PostmarkEnviadorDeEmail.kt
        └── eventos/
            └── ModulithPublicadorDeEventos.kt
```

**JSON Schemas** de los integration events versionados viven en la **raíz del repo** (ADR-0007 D11):

```
schemas/
└── planificacion/
    ├── plan-publicado-v1.json
    └── sesion-personalizada-v1.json
```

## 2. Las tres capas + `api`

Cada módulo (un *bounded context* de ADR-0007 D2) se organiza en:

```
infrastructure   →   application   →   domain   ←   api
(adaptadores)        (casos de uso)     (modelo + puertos + eventos internos)   (contratos públicos)
```

- **Regla de dependencias**: `infrastructure → application → domain`. El `domain` **no depende de nadie** (salvo `shared` y Arrow-kt — ver más abajo).
- **`api`** es **paquete público de contratos**: lo importa cualquier módulo que consume eventos de éste. **No depende de `domain`**: los integration events no exponen tipos internos.
- **`domain` puede importar**: Kotlin stdlib, `shared.autorizacion` (Principal, Rol), **Arrow-kt** (Either, Raise DSL). Nada más.
  - Arrow-kt **sí está permitido** en `domain` porque es librería pura sin frameworks. `ADR-0008 D6` prohíbe Spring, JPA, Jackson, SDKs de nube — no menciona Arrow.
- **Imports prohibidos en `domain`** (verificados por ArchUnit — ADR-0008 D14):
  - `org.springframework.*` (cualquier Spring)
  - `jakarta.persistence.*` / `javax.persistence.*` (JPA)
  - `com.fasterxml.jackson.*` (Jackson)
  - `software.amazon.awssdk.*` y similares (SDKs de nube)
  - El propio paquete `infrastructure.*` de cualquier módulo.

La regla la verifica **ArchUnit** en los tests (ADR-0010 D8, ADR-0008 D14).

## 3. La capa `domain`

Clases **puras**: sin Spring, sin JPA, sin frameworks. Solo Kotlin + Arrow + `shared`. Es el corazón testable.

### Typed IDs

Identificadores tipados como `value class` envolviendo `UUID v7` (ADR-0008):

```kotlin
// domain/ids.kt
@JvmInline
value class PlanId(val value: UUID) {
    init {
        require(value.version() == 7) { "PlanId debe ser UUID v7" }
    }
    companion object {
        fun nuevo(): PlanId = PlanId(UuidCreator.getTimeOrderedEpoch())  // uuid-creator o equiv.
    }
}

@JvmInline value class ClubId(val value: UUID)
@JvmInline value class AlumnoId(val value: UUID)
@JvmInline value class EntrenadorId(val value: UUID)
```

Evita pasar un `String` o un `UUID` "suelto" como ID equivocado.

### Errores del módulo: `PlanificacionError`

Cada módulo define **su propia** `sealed class` de errores en `domain` (ADR-0008 D11, ADR-0009 D12). **No hay un `DomainError` compartido**: cada módulo es responsable de sus errores.

```kotlin
// domain/PlanificacionError.kt
sealed class PlanificacionError {
    // Variantes comunes con shape estandarizado
    data class Forbidden(val razon: String) : PlanificacionError()
    data class NotFound(val recurso: String, val id: String) : PlanificacionError()
    data class InvalidInput(val campo: String, val motivo: String) : PlanificacionError()
    data object Conflict : PlanificacionError()
    data class ProjectionStale(val modulo: String, val lagSeconds: Long) : PlanificacionError()

    // Variantes específicas del módulo Planificación
    data class PlanYaPublicado(val planId: PlanId) : PlanificacionError()
    data object SinSesiones : PlanificacionError()
    data class AlumnoFueraDelSnapshot(val alumnoId: AlumnoId) : PlanificacionError()
}
```

El adaptador REST traduce cada variante a HTTP en el `@RestControllerAdvice` (sección 5).

### Agregado: `PlanSemanal`

Raíz que protege sus invariantes (ADR-0008):

```kotlin
// domain/PlanSemanal.kt
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

class PlanSemanal private constructor(
    val id: PlanId,
    val clubId: ClubId,
    val entrenadorId: EntrenadorId,
    private val sesiones: MutableList<Sesion>,
    private var estado: EstadoPlan,
) {
    /**
     * Publica el plan. Validación esperable: el plan no puede estar ya publicado.
     * → devuelve Either (ADR-0008 D11).
     */
    fun publicar(): Either<PlanificacionError, PlanPublicado> = either {
        ensure(estado == EstadoPlan.BORRADOR) { PlanificacionError.PlanYaPublicado(id) }
        ensure(sesiones.isNotEmpty()) { PlanificacionError.SinSesiones }
        estado = EstadoPlan.PUBLICADO
        PlanPublicado.from(id, clubId, sesiones.toList())  // integration event
    }

    /**
     * Precondición imposible: si llega un ID nulo aquí, es bug del caller.
     * → require (ADR-0008 D11).
     */
    fun marcarSesionEjecutada(sesionId: SesionId) {
        require(sesiones.any { it.id == sesionId }) { "Sesión $sesionId no pertenece al plan $id" }
        // ...
    }
}
```

**Regla**: `require`/`check` para precondiciones que el caller **debería haber validado antes** (bug del caller si fallan). `Either<PlanificacionError, T>` para validaciones esperables (estado del agregado, datos de negocio).

### Value object: `Ritmo`

Concepto sin identidad propia, inmutable (ADR-0002):

```kotlin
// domain/Ritmo.kt
sealed class Ritmo {
    data class Absoluto(val segPorKm: Int) : Ritmo()
    data class Relativo(val referencia: Distancia, val deltaSegPorKm: Int) : Ritmo()
}
```

### Domain event interno

Hechos relevantes **dentro** del módulo, nombrados en pasado. Viven en `domain/events`:

```kotlin
// domain/events/SesionAnadida.kt
data class SesionAnadida(val planId: PlanId, val sesion: Sesion)
```

No salen del módulo. Sirven para orquestación interna entre agregados.

### Integration event público

Vive en `api/events`, implementa la interface `IntegrationEvent` con los **6 campos obligatorios + traceparent opcional** (ADR-0007 D11):

```kotlin
// api/events/IntegrationEvent.kt
interface IntegrationEvent {
    val eventId: UUID
    val aggregateId: UUID
    val occurredAt: Instant
    val version: Int
    val clubId: UUID
    val actorId: UUID?
    /** W3C Trace Context propagado, opcional. ADR-0011 D4. */
    val traceparent: String?
}

// api/events/PlanPublicado.kt
data class PlanPublicado(
    override val eventId: UUID,
    override val aggregateId: UUID,      // = planId
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    // payload específico
    val sesiones: List<SesionView>,
) : IntegrationEvent {
    companion object {
        fun from(planId: PlanId, clubId: ClubId, sesiones: List<Sesion>): PlanPublicado = PlanPublicado(
            eventId = UUID.randomUUID(),
            aggregateId = planId.value,
            occurredAt = Instant.now(),
            clubId = clubId.value,
            actorId = PrincipalContext.actual()?.userId,
            traceparent = OpenTelemetry.actualTraceparent(),
            sesiones = sesiones.map(SesionView::from),
        )
    }
}
```

El **JSON Schema** correspondiente vive en `schemas/planificacion/plan-publicado-v1.json` — versionado en el repo (ADR-0007 D11).

### Puerto: repositorio y servicios externos

Interfaces de lo que cruza a infraestructura. Viven en `domain/ports`:

```kotlin
// domain/ports/PlanSemanalRepository.kt
interface PlanSemanalRepository {
    fun guardar(plan: PlanSemanal)
    fun buscar(id: PlanId): PlanSemanal?
}

// domain/ports/PlanificacionAutorizacionService.kt
interface PlanificacionAutorizacionService {
    fun puedePublicarPlan(principal: Principal, planId: PlanId): Either<PlanificacionError, Unit>
    fun puedeVerPlan(principal: Principal, planId: PlanId): Either<PlanificacionError, Unit>
    fun puedePersonalizarSesion(principal: Principal, planId: PlanId, alumnoId: AlumnoId): Either<PlanificacionError, Unit>
}
```

## 4. La capa `application`

Casos de uso que **orquestan el dominio**. Dependen de `domain`. Publican los eventos de dominio y consumen los entrantes.

### Caso de uso: `@ApplicationService` con autorización explícita y `Either`

```kotlin
// application/PublicarPlanService.kt
import arrow.core.Either
import arrow.core.raise.either

@ApplicationService
class PublicarPlanService(
    private val repositorio: PlanSemanalRepository,
    private val autorizacionService: PlanificacionAutorizacionService,
    private val publicador: PublicadorDeEventos,
    private val principalProvider: PrincipalProvider,
) {
    fun ejecutar(planId: PlanId): Either<PlanificacionError, PlanPublicado> = either {
        val principal = principalProvider.actual()

        // Autorización EXPLÍCITA al inicio (ADR-0009 D7, D13)
        autorizacionService.puedePublicarPlan(principal, planId).bind()

        val plan = repositorio.buscar(planId)
            ?: raise(PlanificacionError.NotFound("PlanSemanal", planId.value.toString()))

        val evento = plan.publicar().bind()

        repositorio.guardar(plan)
        publicador.publicar(evento)  // se entrega vía outbox de Spring Modulith (ADR-0007 D6)

        evento
    }
}
```

- **`@ApplicationService`** es anotación propia (`com.runcriticon.shared.ApplicationService`) meta-anotada con `@Service` de Spring. ArchUnit la usa para verificar las reglas (ADR-0009 D13).
- **El caso de uso devuelve `Either<PlanificacionError, T>`**, nunca lanza excepción de dominio (ADR-0008 D11).
- **Llamada explícita al `AutorizacionService` del módulo** como patrón canónico (ADR-0009 D7). La variante declarativa `@Authorize(...)` queda para reglas RBAC puras; la variante `@NoAuthRequired` requiere comentario justificativo (ADR-0009 D13).
- **Excepciones permitidas**: sólo las que lanza el framework (Spring, Hibernate). Cualquier error de negocio va por `Either`.

### Servicio de autorización del módulo

Implementación del puerto `PlanificacionAutorizacionService` en `application/autorizacion`:

```kotlin
// application/autorizacion/PlanificacionAutorizacionServiceImpl.kt
@Service
class PlanificacionAutorizacionServiceImpl(
    private val proyeccionMiembros: MiembrosGrupoProjection,
    private val proyeccionGruposDeEntrenador: GruposDeEntrenadorProjection,
    private val matriz: MatrizDeAutorizacion,
) : PlanificacionAutorizacionService {

    override fun puedePublicarPlan(principal: Principal, planId: PlanId): Either<PlanificacionError, Unit> = either {
        // 1. RBAC: ¿este rol puede publicar?
        ensure(matriz.puedeRol(principal.rol, Recurso.PLAN, Accion.PUBLICAR)) {
            PlanificacionError.Forbidden("rol no autorizado")
        }

        // 2. Política frente a proyección stale (ADR-0009 D9)
        val lag = proyeccionGruposDeEntrenador.lagSegundos()
        ensure(lag < 60) {
            PlanificacionError.ProjectionStale("planificacion.grupos_entrenador", lag)
        }

        // 3. Nivel de objeto: ¿este entrenador es responsable del grupo del plan?
        val esResponsable = proyeccionGruposDeEntrenador
            .esResponsableDelPlan(principal.userId, planId)
        ensure(esResponsable) { PlanificacionError.Forbidden("entrenador no responsable") }
    }
    // ...
}
```

- **Núcleo compartido** (`shared/autorizacion`) provee `Principal`, `Rol`, `MatrizDeAutorizacion`, primitivas (ADR-0009 D6).
- **Proyecciones locales** alimentan las reglas de relación (ADR-0009 D8).
- **`lagSegundos()`** calcula `now() - last_processed_event_ts` de la tabla de proyección (sección 6). Si > 60 s, `ProjectionStale` (fail-closed, ADR-0009 D9).

### Listener: idempotente con `evento_procesado` y `traceparent`

```kotlin
// application/listeners/AlumnoAsignadoAGrupoListener.kt
@Component
class AlumnoAsignadoAGrupoListener(
    private val proyeccion: MiembrosGrupoProjection,
    private val tracker: EventoProcesadoTracker,
) {
    @ApplicationModuleListener
    fun on(evento: AlumnoAsignadoAGrupo) {
        // 1. Restaurar W3C Trace Context si está presente (ADR-0011 D4)
        evento.traceparent?.let { TraceContextRestorer.restore(it) }

        // 2. Idempotencia: insert if not exists en planificacion.evento_procesado
        if (!tracker.marcarSiNuevo(listener = "AlumnoAsignadoAGrupoListener", eventId = evento.eventId)) {
            return  // ya procesado, no repetir efectos
        }

        // 3. Lógica del listener
        proyeccion.añadir(evento.grupoId, evento.alumnoId, evento.occurredAt)

        // 4. La transacción del listener envuelve 1+2+3
        //    Si algo falla, el outbox de Spring Modulith reintenta (5 reintentos, ADR-0007 D13)
    }
}
```

La idempotencia se garantiza via tabla `planificacion.evento_procesado` con `(listener, event_id)` UNIQUE:

```sql
CREATE TABLE planificacion.evento_procesado (
    listener     VARCHAR(120)  NOT NULL,
    event_id     UUID          NOT NULL,
    processed_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (listener, event_id)
);
```

Política de fallos del outbox (ADR-0007 D13): si el listener falla, Spring Modulith reintenta hasta 5 veces. Tras agotarlos, el evento queda en `event_publication` como DLQ implícita + alarma + republicación admin.

## 5. La capa `infrastructure`

Los **adaptadores**. Implementan los puertos de `domain`.

### Adaptador de entrada — controller REST

```kotlin
// infrastructure/rest/PlanController.kt
@RestController
@RequestMapping("/api/planes")
class PlanController(
    private val publicarPlan: PublicarPlanService,
    private val planMapper: PlanRestMapper,  // @Konverter
) {
    /**
     * Capa 1 RBAC con la anotación propia @Authorize del núcleo compartido (ADR-0009 D6, D13).
     * ArchUnit exige @Authorize o @NoAuthRequired (con justificación) en todo handler público.
     * No se usa @PreAuthorize de Spring Security (ver backend/CLAUDE.md): la regla se evalúa
     * contra la MatrizDeAutorizacion, sin SpEL en strings.
     */
    @PostMapping("/{id}/publicar")
    @Authorize("PLAN:PUBLICAR")
    fun publicar(@PathVariable id: UUID): ResponseEntity<PlanResponse> =
        publicarPlan.ejecutar(PlanId(id)).toResponse(planMapper::aResponse)
}
```

### Traducción `Either<DomainError, T>` → HTTP

Extension function común para casos estándar + `@RestControllerAdvice` como fallback:

```kotlin
// infrastructure/rest/EitherExtensions.kt
fun <T, R> Either<PlanificacionError, T>.toResponse(mapper: (T) -> R): ResponseEntity<R> = fold(
    ifLeft = { error -> error.toHttpResponse() },
    ifRight = { ok -> ResponseEntity.ok(mapper(ok)) },
)

private fun PlanificacionError.toHttpResponse(): ResponseEntity<Nothing> = when (this) {
    is PlanificacionError.Forbidden        -> ResponseEntity.status(403).build()
    is PlanificacionError.NotFound         -> ResponseEntity.status(404).build()
    is PlanificacionError.InvalidInput     -> ResponseEntity.badRequest().build()
    is PlanificacionError.Conflict         -> ResponseEntity.status(409).build()
    is PlanificacionError.ProjectionStale  -> ResponseEntity.status(503)
                                                .header("Retry-After", "1")
                                                .build()
    is PlanificacionError.PlanYaPublicado  -> ResponseEntity.status(409).build()
    is PlanificacionError.SinSesiones      -> ResponseEntity.badRequest().build()
    is PlanificacionError.AlumnoFueraDelSnapshot -> ResponseEntity.status(404).build()
}
```

- **Cuerpo neutro al cliente** (sin mensaje detallado del error): ADR-0009 D12.
- La razón del error queda en el log de auditoría (ADR-0009 D15).
- El `@RestControllerAdvice` captura **excepciones de framework** y devuelve 500 con cuerpo neutro.

### DTOs propios con Konvert

DTOs separados del dominio (la API no se ata al agregado interno):

```kotlin
// infrastructure/rest/dto/PlanResponse.kt
data class PlanResponse(
    val id: UUID,
    val estado: String,
    val sesiones: List<SesionResponse>,
)

// infrastructure/rest/dto/PlanRestMapper.kt
@Konverter
interface PlanRestMapper {
    fun aResponse(planPublicado: PlanPublicado): PlanResponse
}
```

Konvert genera el mapeo en **tiempo de compilación** (sin reflection). Detalles, configuración y mappers custom en [`persistencia.md`](persistencia.md) (subdocumento — pendiente).

### Modelo de persistencia + mapeador

Por ADR-0008 D6 (dominio puro), la entidad JPA está **separada** del agregado:

```kotlin
// infrastructure/persistencia/PlanSemanalEntity.kt
@Entity
@Table(name = "plan_semanal", schema = "planificacion")
class PlanSemanalEntity { /* anotaciones JPA */ }

// infrastructure/persistencia/PlanSemanalMapper.kt
@Konverter
interface PlanSemanalMapper {
    fun aDominio(e: PlanSemanalEntity): PlanSemanal
    fun aEntidad(p: PlanSemanal): PlanSemanalEntity
}
```

### Repositorio con `@AuthScope`

```kotlin
// infrastructure/persistencia/PlanSemanalRepositoryImpl.kt
@Repository
class PlanSemanalRepositoryImpl(
    private val entityRepo: PlanSemanalEntityRepository,
    private val mapper: PlanSemanalMapper,
) : PlanSemanalRepository {

    @AuthScope(Scope.CLUB)
    override fun buscar(id: PlanId): PlanSemanal? =
        entityRepo.findById(id.value)?.let(mapper::aDominio)

    @AuthScope(Scope.CLUB, Scope.GRUPOS_DEL_ENTRENADOR)
    fun listarDelEntrenador(entrenadorId: EntrenadorId): List<PlanSemanal> =
        entityRepo.findByEntrenadorId(entrenadorId.value).map(mapper::aDominio)
}
```

- **`@AuthScope`** con enum de scopes declarativos (ADR-0009 D10, D11). El aspecto inyecta los predicates correspondientes en la query: `WHERE club_id = :principalClubId AND ...`.
- **`@NoAuthScope`** para excepciones administrativas (auditado siempre — ADR-0009 D11). Su uso requiere comentario justificativo y revisión PR.

### Adaptador de salida — `EnviadorDeEmail` con Postmark

Ejemplo de adaptador **no-repositorio**. Cumple el patrón "aislar tras un puerto" del ADR-0005 D3:

```kotlin
// domain/ports/EnviadorDeEmail.kt
interface EnviadorDeEmail {
    fun enviarInvitacion(destinatario: Email, magicLink: String): Either<EmailError, Unit>
}

// infrastructure/email/PostmarkEnviadorDeEmail.kt
@Component
class PostmarkEnviadorDeEmail(
    private val postmarkClient: PostmarkClient,
    private val plantillas: PlantillasInvitacion,  // plantillas en código (ADR-0005 D7)
) : EnviadorDeEmail {

    override fun enviarInvitacion(destinatario: Email, magicLink: String): Either<EmailError, Unit> = either {
        val html = plantillas.invitacion(magicLink)
        runCatching { postmarkClient.send(destinatario, "Invitación al club", html) }
            .onFailure { raise(EmailError.EnvioFallido(it.message ?: "desconocido")) }
    }
}
```

El día que se cumpla el disparador de ADR-0005 D15 (migrar a SES), el cambio es **solo este archivo** — el resto del módulo sigue igual.

## 6. Comunicación entre módulos — *events-first*

Un módulo **nunca llama de forma síncrona** a otro (ADR-0007 D7). Cuando necesita datos de otro contexto, mantiene una **proyección local** alimentada por **integration events**.

### Flujo completo

```
[Módulo A]                          [outbox de Spring Modulith]                          [Módulo B]
                                            (event_publication
                                             en mismo Postgres,
                                             ADR-0007 D6)

casoDeUso() ── plan.publicar()                                                            @ApplicationModuleListener
                    │                                                                              │
                    └─→ PlanPublicado ────────────► persiste en MISMA transacción ──────────► restaurar traceparent
                        (api/events,                                                          → idempotencia (evento_procesado)
                         interface IntegrationEvent,                                          → actualizar proyección
                         6 campos + traceparent)                                              → actualizar last_processed_event_ts
                                                    │
                                              outbox entrega
                                              at-least-once
                                              (con 5 reintentos
                                               ADR-0007 D13)
```

### Política de fallos del outbox

- **5 reintentos** con backoff exponencial 1/2/4/8/16 s (ADR-0007 D13).
- Tras agotar los reintentos, el evento queda en `event_publication` como **DLQ implícita** + alarma operativa.
- **Republicación manual** vía endpoint admin `POST /admin/events/republish` tras corregir la causa raíz.
- Métrica `outbox_dlq_events` con alarma a **> 0** (ADR-0011 D10).

### Lag de proyección

Cada proyección local lleva el momento del último evento procesado:

```sql
CREATE TABLE planificacion.miembros_grupo (
    grupo_id                 UUID PRIMARY KEY,
    alumnos                  UUID[] NOT NULL DEFAULT ARRAY[]::UUID[],
    last_processed_event_ts  TIMESTAMPTZ NOT NULL,
    last_processed_event_id  UUID NOT NULL
);
```

El listener actualiza estas dos columnas al consumir cada evento. El `AutorizacionService` y la métrica `projection_lag_seconds{module, projection}` la leen para decidir fail-closed > 60 s (ADR-0009 D9, ADR-0011 D10).

### Versionado de eventos: dual-publishing v1+v2

Cuando un integration event cambia de forma **rompiente** (ADR-0007 D11):

1. Se añade `PlanPublicadoV2` en `api/events` con el nuevo shape. El JSON Schema en `schemas/planificacion/plan-publicado-v2.json`.
2. Durante una **ventana de migración (4 semanas por defecto)**, el emisor publica `v1` Y `v2` simultáneamente.
3. Cada consumidor migra a `v2` cuando le toque, sin presión.
4. Pasada la ventana, el emisor retira `v1`. Consumidores que no migraron empiezan a fallar — el contrato de migración era explícito.

Para cambios additivos (campos nuevos opcionales), basta con incrementar `version` en el mismo evento sin dual-publishing.

### Reproyección tras compactación

El outbox se compacta a los **30 días** (ADR-0007 D15). Para no perder histórico de las proyecciones largas:

- Cada proyección guarda **un snapshot semanal** en `planificacion.snapshot_miembros_grupo(snapshot_at, contenido_jsonb)`.
- Si hay que reproyectar (cambio de lógica, recuperación tras corrupción), el endpoint admin `POST /admin/proyecciones/{modulo}/{proyeccion}/reproyectar` restaura el snapshot más reciente y replaya desde el evento posterior.

## 7. Autorización

Tres capas concéntricas (ADR-0009 D1):

| Capa | Responsabilidad | Dónde | Cómo |
|---|---|---|---|
| **1 — RBAC por rol** | *"¿este rol puede ejecutar esta operación?"* | Controller | `@Authorize("PLAN:PUBLICAR")` o `@NoAuthRequired(justificacion)`, contra `MatrizDeAutorizacion` (ADR-0009 D6, D13) |
| **2 — Nivel de objeto** | *"¿este usuario puede tocar este objeto?"* | `@ApplicationService` | `autorizacionService.puedeXxx(principal, ...)` (ADR-0009 D3, D7) |
| **3 — `club_id`** | Defensa en profundidad | `@Repository` | Aspecto `@AuthScope(Scope.CLUB)` inyecta filtro (ADR-0009 D4, D11) |

> **No se usa `@PreAuthorize` de Spring Security** (ver [`backend/CLAUDE.md`](../../backend/CLAUDE.md)): la capa 1 se declara con la anotación propia `@Authorize` y la evalúa el núcleo compartido contra la `MatrizDeAutorizacion`. Mantiene la semántica de ADR-0009 D2 (RBAC declarativo en el adaptador de entrada) eliminando el SpEL en strings que el propio D2 señalaba como riesgo; ADR-0009 D2 tiene pendiente la nota de revisión que recoja este cambio de mecanismo (rama `feature/revision-adr-0009`).

### Núcleo compartido

Vive en `com.runcriticon.shared.autorizacion` (ADR-0009 D6):

```kotlin
// shared/autorizacion/Principal.kt
data class Principal(val userId: UUID, val clubId: UUID, val rol: Rol)

// shared/autorizacion/Rol.kt
sealed class Rol {
    data object Admin : Rol()
    data object Entrenador : Rol()
    data object Alumno : Rol()
}

// shared/autorizacion/MatrizDeAutorizacion.kt
object MatrizDeAutorizacion {
    fun puede(rol: Rol, recurso: Recurso, accion: Accion): Boolean = /* matriz fija */
}
```

### Servicio de autorización por módulo

Cada módulo define **su propio** servicio de autorización:

- **Interface** en `domain/ports/PlanificacionAutorizacionService` — el caso de uso lo conoce.
- **Implementación** en `application/autorizacion/PlanificacionAutorizacionServiceImpl` — usa proyecciones locales del módulo.

Ver ejemplo completo en sección 4.

### Tests obligatorios

#### ArchUnit: todo `@ApplicationService` autoriza

```kotlin
// test/architecture/AutorizacionArchTest.kt
@AnalyzeClasses(packages = ["com.runcriticon"])
class AutorizacionArchTest {

    @ArchTest
    val `metodos publicos de @ApplicationService autorizan` =
        methods()
            .that().areDeclaredInClassesThat().areAnnotatedWith(ApplicationService::class.java)
            .and().arePublic()
            .should(invokeAutorizacionServiceOrBeAnnotatedAuthorize())
}
```

Falla la build si un caso de uso no autoriza. ADR-0009 D13.

#### Acceso cruzado por caso de uso

```kotlin
// test/integration/PublicarPlanAccesoTest.kt
@SpringBootTest
@AutoConfigureTestContainers
class PublicarPlanAccesoTest {

    @Test
    fun `entrenador A no puede publicar plan del entrenador B`() {
        val planDeB = givenPlanDelEntrenador(entrenadorIdB)
        loginComo(entrenadorIdA)

        val resultado = publicarPlanService.ejecutar(planDeB.id)

        resultado.shouldBeLeft<PlanificacionError.Forbidden>()
    }
}
```

Tests obligatorios por caso de uso que carga o modifica objetos sujetos a nivel de objeto. ADR-0009 D14.

Detalle completo de la pirámide de tests del módulo, fixtures, dataset sintético: [`testing-de-modulos.md`](testing-de-modulos.md) (subdocumento — pendiente).

### `/me/permissions` (cruce a módulo Identidad)

El módulo Identidad expone `GET /me/permissions` que devuelve `{ recurso: [acciones] }` desde la matriz fija (ADR-0009 D18). El frontend lo usa para **ocultar botones** — no como barrera. La regla de oro: **cada petición se autoriza en el servidor**.

### Distinción explícita: observabilidad ≠ auditoría

| Registro | Propósito | Almacén | Retención |
|---|---|---|---|
| **Observabilidad** | Detectar y diagnosticar incidentes | CloudWatch Logs / AMP / X-Ray | 90 d / 13 m / 7 d |
| **Auditoría de identidad** | Investigar incidentes de cuenta | `identidad.evento_auditoria` | 12 m (ADR-0003 D15) |
| **Auditoría de autorización** | Investigar accesos a datos sensibles y denegaciones | `auditoria.evento` (módulo `auditoria` dedicado) | 24 m (ADR-0009 D17) |

**No se mezclan**. Los logs operativos no van a `auditoria.evento`; los accesos a datos sensibles no van a CloudWatch Logs (ADR-0009 D21).

## 8. Checklist al crear un módulo nuevo

Items planos con cruces inline. Ningún item es opcional sin comentario justificativo en PR.

### Estructura de paquetes

- [ ] Paquete del módulo bajo `com.runcriticon.{modulo}` con sub-paquetes `domain`, `application`, `infrastructure`, `api` `(ADR-0007 D2, ADR-0008)`
- [ ] Spring Modulith reconoce el módulo y ArchUnit verifica las fronteras `(ADR-0007 D2, ADR-0010 D8)`
- [ ] Integration events en `api/events`, domain events internos en `domain/events` `(ADR-0007 D11/D12)`
- [ ] JSON Schemas de cada integration event en `schemas/{modulo}/{evento}-v{N}.json` raíz del repo `(ADR-0007 D11)`

### Capa `domain`

- [ ] `domain` sin imports prohibidos (Spring, JPA, Jackson, SDKs de nube). Arrow-kt sí permitido `(ADR-0008 D6, D14)`
- [ ] Typed IDs como `value class` UUID v7 `(ADR-0008)`
- [ ] Agregados con invariantes protegidas por la raíz: `require`/`check` para precondiciones imposibles, `Either<XxxError, T>` para validaciones esperables `(ADR-0008 D11)`
- [ ] `XxxError` sealed class por módulo con variantes comunes (`Forbidden`, `NotFound`, `InvalidInput`, `Conflict`, `ProjectionStale`) + específicas del dominio `(ADR-0008 D11, ADR-0009 D12)`
- [ ] Integration events implementan `IntegrationEvent` con los 6 campos obligatorios + `traceparent` opcional `(ADR-0007 D11, ADR-0011 D4)`
- [ ] Puertos en `domain/ports`: repositorio, `AutorizacionService` del módulo, adaptadores de salida (`EnviadorDeEmail`, etc.) `(ADR-0008 D9)`

### Capa `application`

- [ ] Cada caso de uso es `@ApplicationService` (anotación propia que extiende `@Service`) `(ADR-0008 D7, ADR-0009 D13)`
- [ ] Cada caso de uso devuelve `Either<XxxError, T>` (Arrow-kt + Raise DSL) `(ADR-0008 D11)`
- [ ] Cada caso de uso llama a `autorizacionService` del módulo antes de la operación (o `@Authorize` para RBAC simple, o `@NoAuthRequired` con comentario justificativo) `(ADR-0009 D7, D13)`
- [ ] `AutorizacionService` con interface en `domain/ports` + impl en `application/autorizacion` `(ADR-0009 D7)`
- [ ] Listeners en `application/listeners` con `@ApplicationModuleListener` `(ADR-0007 D6)`
- [ ] Listeners idempotentes vía tabla `{modulo}.evento_procesado(listener, event_id)` UNIQUE `(ADR-0007 D9)`
- [ ] Listeners restauran `trace_id` desde `traceparent` del evento `(ADR-0011 D4)`
- [ ] Proyecciones locales con columnas `last_processed_event_ts` + `last_processed_event_id` para política stale `(ADR-0009 D9)`

### Capa `infrastructure`

- [ ] Todo handler público con `@Authorize(...)` o `@NoAuthRequired(justificacion)`; **sin** `@PreAuthorize` de Spring Security `(ADR-0009 D6, D13)`
- [ ] DTOs propios en `infrastructure/rest/dto`; Konvert para dominio↔DTO `(ADR-0008 D6)`
- [ ] Extension function común + `@RestControllerAdvice` traducen `XxxError` → HTTP con cuerpo neutro `(ADR-0008 D11, ADR-0009 D12)`
- [ ] Cada método de `@Repository` con `@AuthScope(Scope.X, ...)` o `@NoAuthScope` (con comentario justificativo + auditoría) `(ADR-0009 D10, D11)`
- [ ] Modelo de persistencia separado del agregado; Konvert para dominio↔entidad `(ADR-0008 D6)`
- [ ] Adaptadores de salida (puerto + impl): `EnviadorDeEmail`, `PublicadorDeEventos`, etc. `(ADR-0008 D9, ADR-0005 D3)`

### Eventos

- [ ] Política de fallos del outbox aceptada: 5 reintentos + DLQ + alarma + republicación admin `(ADR-0007 D13)`
- [ ] Versionado de eventos breaking: dual-publishing v1+v2 durante ventana de 4 semanas `(ADR-0007 D11)`
- [ ] Snapshot semanal de cada proyección + endpoint admin de reproyección documentado `(ADR-0007 D15)`

### Persistencia (cruce a [`persistencia.md`](persistencia.md))

- [ ] Esquema propio en PostgreSQL `({modulo}.*)`; ninguna FK ni consulta cruzando otros esquemas `(ADR-0004 D4)`
- [ ] Migraciones Flyway compatibles hacia atrás `(ADR-0010 D11)`

### Observabilidad (cruce a [`observabilidad-por-modulo.md`](observabilidad-por-modulo.md))

- [ ] MDC con `module={modulo}` en cada línea de log `(ADR-0011 D5)`
- [ ] Métricas obligatorias emitidas via Micrometer: HTTP por endpoint, eventos pendientes/DLQ, `listener_failures_total`, `projection_lag_seconds`, métricas de negocio del módulo `(ADR-0011 D10, D11)`

### RGPD (cruce a [`rgpd-en-modulos.md`](rgpd-en-modulos.md))

- [ ] Si el módulo tiene PII: consume `AlumnoEliminado` aplicando **borrado mixto** (físico para PII primaria, anonimización para auditoría/derivados) `(ADR-0014 D5/D6/D7)`
- [ ] Cada tabla declarada con su categoría de dato (1-6) `(ADR-0014 D5)`
- [ ] Accesos a datos sensibles emiten evento `AccesoADatosSensibles` consumido por módulo `auditoria` `(ADR-0009 D15/D16)`

### Configuración y secretos (cruce a [`configuracion-y-secretos-en-modulos.md`](configuracion-y-secretos-en-modulos.md))

- [ ] Secretos del módulo declarados en `/runcriticon/{env}/{modulo}/{name}` SSM SecureString `(ADR-0013 D5)`
- [ ] App lee via `@ConfigurationProperties`, sin SDK de AWS en código de módulo `(ADR-0013 D8)`

### Testing (cruce a [`testing-de-modulos.md`](testing-de-modulos.md))

- [ ] ArchUnit: dependencias entre capas, ausencia de imports prohibidos, autorización en `@ApplicationService`, `@AuthScope` en `@Repository`, fronteras de Modulith `(ADR-0008 D14, ADR-0009 D13, ADR-0010 D8)`
- [ ] Tests de acceso cruzado por caso de uso `(ADR-0009 D14)`
- [ ] Tests de integración con Testcontainers PostgreSQL `(ADR-0010 D8)`
- [ ] Tests de contrato del JSON Schema de cada integration event publicado `(ADR-0007 D11)`
- [ ] Tests unitarios del dominio sin Spring `(ADR-0008 D6)`

## Referencias

- **ADR-0002** — modelo de datos (tags, `Ritmo` como *value object*).
- **ADR-0003** — autenticación e identidad.
- **ADR-0004** — base de datos: un esquema por módulo.
- **ADR-0005** — email transaccional (puerto `EnviadorDeEmail`).
- **ADR-0007** — monolito modular, comunicación *events-first*, política de fallos del outbox.
- **ADR-0008** — arquitectura hexagonal y DDD; dominio puro con modelo de persistencia aparte; `Either` para errores.
- **ADR-0009** — modelo de autorización en tres capas; aspecto `@AuthScope`; ArchUnit guards.
- **ADR-0010** — estrategia de tests (ArchUnit, Testcontainers, fronteras de Modulith).
- **ADR-0011** — observabilidad (MDC, métricas obligatorias, `traceparent` propagado).
- **ADR-0013** — configuración y secretos en runtime.
- **ADR-0014** — RGPD: borrado mixto y consumo de `AlumnoEliminado`.
- Subdocumentos por tema (pendientes de redacción):
  - [`persistencia.md`](persistencia.md)
  - [`observabilidad-por-modulo.md`](observabilidad-por-modulo.md)
  - [`rgpd-en-modulos.md`](rgpd-en-modulos.md)
  - [`configuracion-y-secretos-en-modulos.md`](configuracion-y-secretos-en-modulos.md)
  - [`testing-de-modulos.md`](testing-de-modulos.md)
- Plan de formación: [`docs/formacion/arquitectura-dirigida-por-eventos.md`](../formacion/arquitectura-dirigida-por-eventos.md).
- [`docs/glosario.md`](../glosario.md) — lenguaje ubicuo del proyecto.
