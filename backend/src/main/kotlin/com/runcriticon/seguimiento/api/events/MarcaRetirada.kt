package com.runcriticon.seguimiento.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: el alumno borró su marca de una distancia (LAL-31). `aggregateId` es el
 * alumno. Por analogía con `PersonalizacionAplicada`/`PersonalizacionRetirada` (ADR-0002 D9): el ADR-0002 D8
 * solo nombra `MarcaActualizada` explícitamente, este evento gemelo lo exige el AC3 de la historia.
 *
 * Sin `tiempoSegundos`: a diferencia de `PersonalizacionRetirada`, el consumidor futuro (LAL-32) no necesita
 * "restaurar" ningún valor — solo sabe que el ritmo relativo que dependía de `(alumnoId, distancia)` debe
 * volver a `ritmo_falta_marca`.
 */
@NamedInterface("events")
data class MarcaRetirada(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    val distancia: String,
) : IntegrationEvent
