package com.runcriticon.planificacion.application.ports.outbound.persistence

import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant
import java.util.UUID

/**
 * Proyección local de pertenencia a grupo (`miembro_grupo`), alimentada por eventos de integración de
 * `club_taxonomia`. Es la única vía por la que este módulo sabe qué entrenador lleva qué grupo — `CoachGroupLookup`
 * lee de aquí — y de qué alumnos es el snapshot al publicar (LAL-25).
 *
 * **Alumnos y entrenadores se alimentan de forma distinta a propósito** (LAL-25): los alumnos llegan por
 * `MembresiaDeGrupoCambiada`, un snapshot completo (`replaceStudents`), porque la pertenencia por tags no admite
 * eventos delta de verdad — un evento perdido corrompería la proyección para siempre. Los entrenadores siguen
 * llegando por `EntrenadorAsignadoAGrupo`/`EntrenadorEliminadoDeGrupo`, delta (`upsert`/`remove`), porque
 * `grupo_entrenador` es una tabla real y esos eventos sí son completos.
 */
interface GroupMembersProjection {
    /**
     * Reemplaza el snapshot completo de alumnos (`rol = 'ALUMNO'`) de [groupId] por [students], si [occurredAt] es
     * igual o más reciente que el último snapshot aplicado a ese grupo (`miembro_grupo_version`, no por fila: un
     * snapshot que deja el grupo vacío no puede perder la referencia de orden).
     *
     * @return `false` si se descartó por la guarda de orden.
     */
    fun replaceStudents(
        clubId: ClubId,
        groupId: GroupId,
        students: Set<PersonId>,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean

    /**
     * Registra que [personId] (con [role], hoy siempre `"ENTRENADOR"`) pertenece a [groupId], si [occurredAt] es
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

    /**
     * Los alumnos (`rol = 'ALUMNO'`) que pertenecen hoy a [groupId] — de aquí sale el snapshot al publicar
     * (LAL-25).
     */
    fun findStudents(
        clubId: ClubId,
        groupId: GroupId,
    ): Set<PersonId>
}
