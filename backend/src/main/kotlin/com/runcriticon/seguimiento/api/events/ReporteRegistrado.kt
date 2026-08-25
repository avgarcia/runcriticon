package com.runcriticon.seguimiento.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Integration event público: el alumno reportó (o editó) una sesión (LAL-30). `aggregateId` es el alumno.
 * Primer evento que publica este módulo — hasta ahora `seguimiento` solo consumía.
 *
 * Nombre sin la palabra "sesión" a propósito: `NamingConventionArchTest` prohíbe el token `Sesion` en nombres
 * de clase (ya mordió con `MiSesionResueltaResponse` en LAL-29) — reservado para "Session", su equivalente en
 * inglés, que ya usa otro tipo del módulo (`SessionType`). "Reporte" es el término del glosario, así que el
 * evento se nombra por lo que ocurre (se registra un reporte), no por sobre qué es.
 *
 * **Sin `notas` ni el detalle del dolor**: el payload vive hasta 30 días en el outbox (ADR-0007 D15) y el
 * consumidor previsto (panel de alertas, LAL-116) solo necesita el estado y las banderas para decidir si
 * alertar — no el texto libre. Mismo criterio que `AlumnoEliminado` para no propagar más PII de la
 * imprescindible al outbox.
 */
@NamedInterface("events")
data class ReporteRegistrado(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    val planId: UUID,
    val dia: LocalDate,
    val estado: String,
    val valoracion: Int?,
    val motivo: String?,
    val marcaDolor: Boolean,
) : IntegrationEvent
