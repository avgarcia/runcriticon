package com.runcriticon.planificacion.api.events

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Integration event público: el entrenador retiró la personalización de un alumno en una sesión (LAL-26).
 * `aggregateId` es el plan. Igual que [PersonalizacionAplicada], **solo se emite si el plan está `PUBLICADO`**
 * — retirar una personalización que nunca llegó a proyectarse (aplicada y retirada ambas en `BORRADOR`) no
 * tiene nada que deshacer en Seguimiento.
 *
 * Lleva [baseSession] embebida — la sesión base sin override, misma forma que [PersonalizedSession] — para
 * que el listener de Seguimiento pueda **restaurar** `sesion_resuelta` sin preguntarle nada a Planificación
 * (prohibido cruzar módulo con una llamada síncrona, ADR-0007). La alternativa (una columna aparte en
 * `plan_resuelto_por_alumno` con la sesión base) exigía persistirla ya desde `PlanPublicado` aunque nadie la
 * usara nunca si el plan no se personaliza jamás; embeberla aquí es más barato.
 *
 * Schema versionado en `schemas/planificacion/personalizacion-retirada-v1.json`, validado por `contractTest`.
 */
@NamedInterface("events")
data class PersonalizacionRetirada(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    val grupoId: UUID,
    val sesionId: UUID,
    val dia: LocalDate,
    val alumnoId: UUID,
    val baseSession: PersonalizedSession,
) : IntegrationEvent
