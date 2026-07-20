package com.runcriticon.identidad.api.events
import com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: se ha dado de alta un entrenador por invitación (queda en estado `INVITADO`) en el club.
 * Lo publica el caso de uso [com.runcriticon.identidad.application.usecases.invitation.InviteCoachCommand] dentro de su
 * transacción; otros bounded contexts (Club y taxonomía, Seguimiento) lo consumirán para sembrar su proyección local
 * del entrenador.
 * Evento simétrico a [AlumnoInvitado] — asimetría detectada y anotada como deuda al cerrar esa tarea.
 *
 * Payload con `name` + `email` (PII), coherente con [AlumnoInvitado] y [EntrenadorActivado]. Schema versionado en
 * `schemas/identidad/entrenador-invitado-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class EntrenadorInvitado(
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
