package com.runcriticon.seguimiento.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: el alumno registró (o editó) su marca de una distancia (LAL-31, ADR-0002 D8).
 * `aggregateId` es el alumno.
 *
 * Consumidor previsto: LAL-32 recalculará las filas de `plan_resuelto_por_alumno` donde
 * `ritmo_referencia_distancia = distancia AND alumno_id = ese alumno` (ADR-0002 D8, citado literalmente).
 */
@NamedInterface("events")
data class MarcaActualizada(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    val distancia: String,
    val tiempoSegundos: Int,
) : IntegrationEvent
