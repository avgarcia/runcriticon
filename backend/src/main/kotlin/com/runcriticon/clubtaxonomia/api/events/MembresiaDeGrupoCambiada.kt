package com.runcriticon.clubtaxonomia.api.events

import com.runcriticon.shared.events.IntegrationEvent
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

/**
 * Integration event público: la membresía de alumnos de un grupo cambió y `alumnos` es la lista **completa**
 * resultante, no un delta — snapshot auto-contenido (ADR-0007 D15), no evento incremental.
 *
 * Sustituye a `AlumnoAsignadoAGrupo`/`AlumnoEliminadoDeGrupo` (LAL-94), retirados en este mismo cambio: aquellos
 * solo cubrían la excepción manual, nunca la pertenencia por tags, así que no podían ser nunca una fuente
 * completa de membresía. Un consumidor que reciba este evento **reemplaza** su proyección de alumnos del grupo
 * con `alumnos` entero — no aplica un delta sobre lo que ya tenía — así que un evento perdido o reordenado no
 * corrompe la proyección: el siguiente evento que llegue ya trae el estado completo.
 *
 * Se publica desde seis puntos (crear grupo, poner/quitar una excepción manual, asignar/quitar/reemplazar tags
 * de un alumno) — ver `README.md` del módulo, sección "Eventos publicados".
 */
@NamedInterface("events")
data class MembresiaDeGrupoCambiada(
    override val eventId: UUID,
    override val aggregateId: UUID,
    override val occurredAt: Instant,
    override val version: Int = 1,
    override val clubId: UUID,
    override val actorId: UUID?,
    override val traceparent: String?,
    /** Snapshot completo: todos los alumnos que pertenecen al grupo justo después del cambio. */
    val alumnos: List<UUID>,
) : IntegrationEvent
