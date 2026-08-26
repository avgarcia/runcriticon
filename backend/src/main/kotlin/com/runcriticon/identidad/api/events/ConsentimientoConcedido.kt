package com.runcriticon.identidad.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un alumno ha concedido consentimiento explícito de datos de salud (Art.
 * 9.2.a RGPD, ADR-0014 D16), al activar su cuenta o desde `/me/consentimiento` (LAL-128). Lo publica
 * [com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand] y
 * [com.runcriticon.identidad.application.usecases.consent.GrantConsentCommand]. El módulo `seguimiento`
 * lo consume para su proyección local de qué alumnos pueden reportar sesiones.
 *
 * **Sin `ip` ni `user_agent`**: el payload vive en el outbox mucho después de publicarse; el consumidor
 * solo necesita saber que el alumno consintió, no los metadatos forenses de esa concesión (esos quedan
 * en `identidad.consentimiento`, no se propagan). Mismo criterio que `AlumnoEliminado`.
 *
 * Schema versionado en `schemas/identidad/consentimiento-concedido-v1.json`, validado por `contractTest`.
 */
@NamedInterface("events")
data class ConsentimientoConcedido(
    override val eventId: UUID,
    /** El alumno que concede el consentimiento. */
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Versión del texto de consentimiento vigente en el momento de la concesión. */
    val versionTexto: String,
) : IntegrationEvent
