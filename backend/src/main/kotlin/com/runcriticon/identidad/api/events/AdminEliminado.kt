package com.runcriticon.identidad.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: se ha eliminado un ADMIN y sus datos personales del club, en ejercicio del derecho de
 * supresión. Simétrico a [AlumnoEliminado]/[EntrenadorEliminado] (LAL-126): a diferencia de esos dos, un ADMIN nunca
 * llega a proyectarse como persona en otros módulos (no hay `AdminInvitado`, se siembra directo en `identidad`), así
 * que este evento no dispara borrado físico en ningún consumidor — existe para que los módulos con auditoría local o
 * de autorización (`club_taxonomia`, `auditoria`) anonimicen el `actor_id` de las acciones que el ADMIN realizó antes
 * de suprimirse, algo que la baja de un alumno/entrenador no necesita porque nunca actúan como actor sobre sí mismos.
 *
 * **Sin `name` ni `email`**, igual que sus dos hermanos: el payload de un evento vive en el outbox mucho después de
 * publicarse, así que propagar aquí la PII la dejaría escrita justo cuando se acaba de borrar el original.
 *
 * Schema versionado en `schemas/identidad/admin-eliminado-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class AdminEliminado(
    override val eventId: UUID,
    /** Identificador del ADMIN eliminado; es su antiguo id de usuario. */
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
) : IntegrationEvent
