package com.runcriticon.identidad.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un alumno ha revocado su consentimiento de datos de salud (ADR-0014 D18,
 * LAL-128), desde `/me/consentimiento`. Lo publica
 * [com.runcriticon.identidad.application.usecases.consent.RevokeConsentCommand]. El módulo `seguimiento`
 * lo consume para rechazar nuevos reportes de sesión de este alumno hasta que vuelva a conceder.
 *
 * Schema versionado en `schemas/identidad/consentimiento-revocado-v1.json`, validado por `contractTest`.
 */
@NamedInterface("events")
data class ConsentimientoRevocado(
    override val eventId: UUID,
    /** El alumno que revoca el consentimiento. */
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
) : IntegrationEvent
