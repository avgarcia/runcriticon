package com.runcriticon.planificacion.application.ports.outbound.persistence

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Proyección local de pertenencia a grupo (`miembro_grupo`), alimentada por los cuatro eventos de integración que
 * publica `club_taxonomia` (LAL-94): `AlumnoAsignadoAGrupo`, `AlumnoEliminadoDeGrupo`, `EntrenadorAsignadoAGrupo`,
 * `EntrenadorEliminadoDeGrupo`. Es la única vía por la que este módulo sabe qué entrenador lleva qué grupo —
 * `CoachGroupLookup` lee de aquí.
 */
interface GroupMembersProjection {
    /**
     * Registra que [personId] (con [role], `"ALUMNO"` o `"ENTRENADOR"`) pertenece a [groupId], si [occurredAt] es
     * igual o más reciente que el último evento aplicado a esa fila.
     *
     * @return `false` si se descartó por la guarda de orden (la fila ya recogía un evento más reciente) — la
     * entrega del outbox no garantiza orden entre `Asignado` y `Eliminado` de la misma persona.
     */
    fun upsert(
        clubId: ClubId,
        groupId: GroupId,
        personId: PersonId,
        role: String,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean

    /**
     * Quita a [personId] de [groupId], si [occurredAt] es igual o más reciente que el último evento aplicado a esa
     * fila (o si la fila no existe, en cuyo caso no hay nada que quitar — idempotente).
     *
     * @return `false` si se descartó por la guarda de orden.
     */
    fun remove(
        clubId: ClubId,
        groupId: GroupId,
        personId: PersonId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean
}
