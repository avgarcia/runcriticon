package com.runcriticon.planificacion.api.events

import com.runcriticon.planificacion.api.PublishedPersonalization
import com.runcriticon.planificacion.api.PublishedSession
import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: un plan semanal se publicó a su grupo (LAL-25). `aggregateId` es el plan.
 *
 * Auto-contenido por exigencia expresa de ADR-0007 D15, que cita este evento por su nombre: lleva el
 * snapshot completo de alumnos ([snapshotAlumnos]) y las sesiones de la semana ([sesiones]) embebidas, para
 * que un consumidor (Seguimiento) pueda construir su read model sin preguntarle nada a Planificación ni
 * asumir que algún otro evento previo esté disponible.
 */
@NamedInterface("events")
data class PlanPublicado(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    val grupoId: UUID,
    /** Snapshot completo: todos los alumnos que pertenecían al grupo justo antes de publicar (ADR-0002 D5). */
    val snapshotAlumnos: List<UUID>,
    val sesiones: List<PublishedSession>,
    /**
     * Personalizaciones creadas antes de publicar (LAL-26, AC2): no emitieron `PersonalizacionAplicada`
     * propio porque todavía no había snapshot al que proyectar. Campo aditivo — ver KDoc de
     * [PublishedPersonalization]. Vacía en un plan sin personalizaciones previas a la publicación.
     */
    val personalizaciones: List<PublishedPersonalization> = emptyList(),
) : IntegrationEvent
