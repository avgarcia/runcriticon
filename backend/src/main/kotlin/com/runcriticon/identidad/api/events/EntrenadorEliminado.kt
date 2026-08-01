package com.runcriticon.identidad.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: se han eliminado un entrenador y sus datos personales del club, en ejercicio del derecho
 * de supresión. Simétrico a [AlumnoEliminado] — los módulos que proyectan personas guardan a los dos roles, así que
 * ambos necesitan su evento de baja para no dejar PII huérfana.
 *
 * **Sin `name` ni `email`**, por el mismo motivo que [AlumnoEliminado]: el payload sobrevive en el outbox al dato que
 * se acaba de borrar.
 *
 * Schema versionado en `schemas/identidad/entrenador-eliminado-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class EntrenadorEliminado(
    override val eventId: UUID,
    /** Identificador del entrenador eliminado; es su antiguo id de usuario. */
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
) : IntegrationEvent
