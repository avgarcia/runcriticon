package com.runcriticon.identidad.api.events
import com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un alumno ha activado su cuenta (pasa a `ACTIVO`) consumiendo su invitación. Lo publica el
 * caso de uso [com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand]; otros bounded contexts (Club y
 * taxonomía, Seguimiento) lo consumirán para activar su proyección local del alumno.
 *
 * Payload con `name` + `email` (PII), coherente con `AlumnoInvitado`. Schema versionado en
 * `schemas/identidad/alumno-activado-v1.json`, validado por el job `contractTest`.
 */
@NamedInterface("events")
data class AlumnoActivado(
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
