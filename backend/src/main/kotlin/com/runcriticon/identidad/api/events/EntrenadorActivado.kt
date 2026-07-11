package com.runcriticon.identidad.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un entrenador ha activado su cuenta (pasa a `ACTIVO`) consumiendo su
 * invitación (LAL-9, ADR-0007 D11). Lo publica el caso de uso
 * [com.runcriticon.identidad.application.usecases.ActivateAccount]; otros bounded contexts (Club y
 * taxonomía, Seguimiento) lo consumirán para activar su proyección local del entrenador.
 *
 * Payload con `name` + `email` (PII), coherente con el resto de eventos de identidad. Schema
 * versionado en `schemas/identidad/entrenador-activado-v1.json`, validado por `contractTest`.
 */
@NamedInterface("events")
data class EntrenadorActivado(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Nombre completo del entrenador. */
    val name: String,
    /** Email del entrenador (identificador único en el club). */
    val email: String,
) : IntegrationEvent
