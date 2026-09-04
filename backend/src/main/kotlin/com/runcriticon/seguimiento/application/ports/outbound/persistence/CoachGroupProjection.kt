package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Proyección local de qué entrenador lleva qué grupo (`grupo_entrenador`), alimentada por
 * `EntrenadorAsignadoAGrupo`/`EntrenadorEliminadoDeGrupo` de `club_taxonomia`. Es la copia propia de este
 * módulo del mismo hecho que `planificacion.miembro_grupo` proyecta para sus propios fines (ADR-0007: sin
 * esquema ni FK compartidos entre módulos) — [CoachAlertReader] la usa para acotar "solo mis grupos".
 *
 * **Solo el lado ENTRENADOR**, a diferencia de `planificacion.GroupMembersProjection`: la pertenencia
 * alumno↔grupo ya llega gratis vía `plan_resuelto_por_alumno.grupo_id` (LAL-116, migración
 * `V202609040001`), así que no hace falta duplicar aquí el snapshot completo de alumnos
 * (`MembresiaDeGrupoCambiada`) que sí necesita `planificacion` para el propio flujo de publicación.
 */
interface CoachGroupProjection {
    /**
     * Registra que [coachId] lleva [groupId], si [occurredAt] es igual o más reciente que el último evento
     * aplicado a esa fila — mismo criterio de guarda de orden que
     * `planificacion.GroupMembersProjection.upsert`.
     *
     * @return `false` si se descartó por la guarda de orden.
     */
    fun upsert(
        clubId: ClubId,
        groupId: GroupId,
        coachId: CoachId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean

    /**
     * Quita a [coachId] de [groupId], si [occurredAt] es igual o más reciente que el último evento aplicado a
     * esa fila (o si la fila no existe, idempotente).
     *
     * @return `false` si se descartó por la guarda de orden.
     */
    fun remove(
        clubId: ClubId,
        groupId: GroupId,
        coachId: CoachId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean
}
