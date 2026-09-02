package com.runcriticon.seguimiento.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Integration event público: el alumno reajustó el día de una sesión (LAL-33) — la movió a otro día o la
 * marcó como saltada. `aggregateId` es el alumno.
 *
 * Nombre sin el token `Sesion`: `NamingConventionArchTest` lo prohíbe en nombres de clase (ya mordió con
 * `MiSesionResueltaResponse` en LAL-29).
 *
 * **`accion` y `motivo` viajan los dos**, no solo `marcaDolor`: `docs/wireframes/08-coach-alerts.md` define la
 * regla *"Saltó N consecutivas — 2+ sesiones marcadas como saltada en los últimos 5 días"* para el futuro
 * panel de alertas (LAL-116); sin `accion` ese consumidor no podría aplicarla.
 *
 * **Sin `mensaje`**: es texto libre y el payload vive hasta 30 días en el outbox (ADR-0007 D15) — mismo
 * criterio que `ReporteRegistrado`, que tampoco propaga `notas`.
 *
 * Un `REEMPLAZAR`/`INTERCAMBIAR` publica **un evento por fila escrita** (dos eventos), correlados por
 * [operacionId] — igual que las dos filas de `reajuste_dia` comparten `operacion_id`.
 */
@NamedInterface("events")
data class DiaReajustado(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    val operacionId: UUID,
    val planId: UUID,
    val diaPlanificado: LocalDate,
    val accion: String,
    val diaDestino: LocalDate?,
    val motivo: String,
    val marcaDolor: Boolean,
) : IntegrationEvent
