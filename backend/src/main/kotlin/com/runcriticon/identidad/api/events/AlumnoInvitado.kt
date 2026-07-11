package com.runcriticon.identidad.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: se ha dado de alta un alumno por invitación (queda en estado
 * `INVITADO`) en el club (LAL-8, ADR-0007 D11). Lo publica el caso de uso
 * [com.runcriticon.identidad.application.usecases.InviteStudent] dentro de su transacción; otros
 * bounded contexts (Club y taxonomía, Seguimiento) lo consumirán para sembrar su proyección local
 * del alumno.
 *
 * Payload con `name` + `email` (PII) por decisión de producto: el consumidor puede pintar el alumno
 * sin un evento adicional. Schema versionado en `schemas/identidad/alumno-invitado-v1.json`,
 * validado por el job `contractTest` (ADR-0007 D11).
 */
@NamedInterface("events")
data class AlumnoInvitado(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Nombre completo del alumno. */
    val name: String,
    /** Email del alumno (identificador único en el club). */
    val email: String,
) : IntegrationEvent
