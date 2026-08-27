package com.runcriticon.planificacion.api.events

import com.runcriticon.planificacion.api.PersonalizedSession
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Integration event público: el entrenador aplicó (creó o sustituyó) una personalización de sesión para un
 * alumno concreto (LAL-26, ADR-0002 D9). `aggregateId` es el plan.
 *
 * **Solo se emite si el plan ya está `PUBLICADO`** (AC2): antes de publicar no hay snapshot al que proyectar,
 * así que Seguimiento no tiene nada que actualizar todavía. Una personalización aplicada antes de publicar
 * llega igual, pero dentro de `PlanPublicado.personalizaciones` en el momento de publicar — no aquí.
 *
 * Auto-contenido (ADR-0007 D15, mismo criterio que `PlanPublicado`): lleva [override] embebido, no un id que
 * el consumidor tendría que resolver contra Planificación (prohibido cruzar módulo con una llamada síncrona).
 * Lleva [dia], no solo [sesionId]: la PK de `seguimiento.plan_resuelto_por_alumno` es
 * `(alumno_id, plan_id, dia)`, y el listener no puede localizar la fila solo con el id de la sesión.
 *
 * **[mensajeAlAlumno] es texto libre del entrenador — PII primaria** que queda en el outbox hasta 30 días
 * (ADR-0007 D15, D11 de este mismo ADR). A diferencia de `ConsentimientoConcedido` (que omite `ip`/
 * `user_agent`) o `ReporteRegistrado` (que omite `notas`), aquí sí se propaga: el consumidor lo necesita para
 * pintarlo en la vista "hoy" del alumno y no hay forma de proyectarlo sin él.
 *
 * Nombrado `PersonalizacionAplicada`, no `SesionPersonalizada` (el nombre que usa ADR-0002 D9):
 * `NamingConventionArchTest` prohíbe el token `Sesion` en nombres de clase (ADR-0008 D4). Revisión de
 * ADR-0002 D9 pendiente aparte, no bloquea esta historia.
 *
 * Schema versionado en `schemas/planificacion/personalizacion-aplicada-v1.json`, validado por `contractTest`.
 */
@NamedInterface("events")
data class PersonalizacionAplicada(
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
    val override: PersonalizedSession,
    val mensajeAlAlumno: String?,
) : IntegrationEvent
