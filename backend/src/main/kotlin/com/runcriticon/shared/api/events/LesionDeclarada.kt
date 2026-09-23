package com.runcriticon.shared.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * El alumno confirmó en el modal de reajuste de día que su tag `estado` pase a "lesión" (wireframe
 * 07 §Flujo B opción 4). `aggregateId` es el alumno.
 *
 * **Vive en `shared.api.events` y no en `seguimiento` (quien lo publica) ni en `club_taxonomia` (quien lo
 * consume)** — mismo motivo que [AccesoDenegado]: el productor es un módulo aguas abajo del consumidor en el
 * orden de dependencia habitual (`identidad → club_taxonomia → planificacion → seguimiento`), así que
 * publicarlo desde `seguimiento.api.events` habría obligado a `club_taxonomia` a depender de `seguimiento`,
 * una dependencia inversa que `ApplicationModules.verify()` rechaza. `shared` es módulo `OPEN` y ya cuenta
 * con la named interface `events` en el allowlist de ambos módulos (`club_taxonomia` la tenía por
 * `AccesoDenegado`; `seguimiento` la suma aquí).
 *
 * **Se publica solo si el alumno confirmó** (`confirmaCambioEstado`, `RescheduleDayCommand`) — a diferencia
 * de la marca de dolor y la alerta al entrenador, que se activan igual con cualquier `AdjustmentReason.LESION`
 * sin necesidad de este evento. El propio `DiaReajustado` de la misma operación ya lleva `motivo=LESION` y
 * `marcaDolor=true`; este evento existe solo para que `club_taxonomia` sepa que debe mutar su propio
 * agregado.
 *
 * **Sin `mensaje`**: mismo criterio que `DiaReajustado` — es texto libre de salud que no necesita cruzar a
 * `club_taxonomia`, que solo necesita "este alumno declaró lesión".
 */
@NamedInterface("events")
data class LesionDeclarada(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
) : IntegrationEvent
