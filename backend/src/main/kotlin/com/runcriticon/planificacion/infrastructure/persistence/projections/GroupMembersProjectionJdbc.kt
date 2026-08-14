package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.planificacion.application.ports.outbound.persistence.GroupMembersProjection
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Adaptador de [GroupMembersProjection] sobre `JdbcTemplate`, contra `planificacion.miembro_grupo`.
 *
 * Se invoca desde `GroupMembersProjectionListener`, que corre sin principal (fuera de una petición HTTP) — mismo
 * motivo que `@NoAuthScope` en `PersonProjectionJdbc` de `club_taxonomia`: el `club_id` de la fila lo trae el
 * propio evento, no hay principal contra el que verificarlo.
 */
@Repository
class GroupMembersProjectionJdbc(
    private val jdbc: JdbcTemplate,
) : GroupMembersProjection {
    @NoAuthScope(
        justificacion =
            "Escrito por un listener de eventos sin principal (fuera de una petición HTTP); el club_id de la fila " +
                "lo trae el propio evento, no hay contra qué verificarlo.",
    )
    override fun replaceStudents(
        clubId: ClubId,
        groupId: GroupId,
        students: Set<PersonId>,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean {
        val timestamp = Timestamp.from(occurredAt)
        val applied =
            jdbc.update(UPSERT_VERSION_SQL, groupId.value, clubId.value, eventId, timestamp, timestamp) == 1
        if (!applied) return false

        jdbc.update(DELETE_STUDENTS_SQL, groupId.value, clubId.value)
        if (students.isNotEmpty()) {
            jdbc.batchUpdate(
                INSERT_STUDENT_SQL,
                students.map { student ->
                    arrayOf<Any>(groupId.value, clubId.value, student.value, ROLE_ALUMNO, eventId, timestamp)
                },
            )
        }
        return true
    }

    @NoAuthScope(
        justificacion =
            "Escrito por un listener de eventos sin principal (fuera de una petición HTTP); el club_id de la fila " +
                "lo trae el propio evento, no hay contra qué verificarlo.",
    )
    override fun upsert(
        clubId: ClubId,
        groupId: GroupId,
        personId: PersonId,
        role: String,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean =
        jdbc.update(
            UPSERT_SQL,
            groupId.value,
            clubId.value,
            personId.value,
            role,
            eventId,
            Timestamp.from(occurredAt),
            Timestamp.from(occurredAt),
        ) == 1

    @NoAuthScope(
        justificacion =
            "Escrito por un listener de eventos sin principal (fuera de una petición HTTP); el club_id de la fila " +
                "lo trae el propio evento, no hay contra qué verificarlo.",
    )
    override fun remove(
        clubId: ClubId,
        groupId: GroupId,
        personId: PersonId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean =
        jdbc.update(
            REMOVE_SQL,
            groupId.value,
            clubId.value,
            personId.value,
            Timestamp.from(occurredAt),
        ) == 1

    @AuthScope(Scope.CLUB)
    override fun findStudents(
        clubId: ClubId,
        groupId: GroupId,
    ): Set<PersonId> =
        jdbc
            .query(
                FIND_STUDENTS_SQL,
                { rs, _ -> PersonId.of(rs.getObject("persona_id", UUID::class.java)) },
                groupId.value,
                clubId.value,
                ROLE_ALUMNO,
            ).toSet()
}

private const val ROLE_ALUMNO = "ALUMNO"

/**
 * Guarda de orden **por grupo**, no por persona: a diferencia de [UPSERT_SQL]/[REMOVE_SQL], un snapshot que deja
 * el grupo sin alumnos no puede perder la referencia contra la que comparar el siguiente evento. Misma mecánica
 * `ON CONFLICT ... DO UPDATE ... WHERE`: si ya había un snapshot más reciente, la fila no se actualiza y
 * `jdbc.update` devuelve `0`.
 */
private const val UPSERT_VERSION_SQL =
    """
    INSERT INTO planificacion.miembro_grupo_version (grupo_id, club_id, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?)
    ON CONFLICT (grupo_id) DO UPDATE
        SET club_id = EXCLUDED.club_id,
            last_processed_event_id = EXCLUDED.last_processed_event_id,
            last_processed_event_ts = EXCLUDED.last_processed_event_ts
        WHERE planificacion.miembro_grupo_version.last_processed_event_ts <= ?
    """

/** Borra las filas ALUMNO del grupo antes de reinsertar el snapshot -- deja intactas las de ENTRENADOR. */
private const val DELETE_STUDENTS_SQL =
    "DELETE FROM planificacion.miembro_grupo WHERE grupo_id = ? AND club_id = ? AND rol = 'ALUMNO'"

/** Inserción simple, sin `ON CONFLICT`: [DELETE_STUDENTS_SQL] ya dejó limpia la tabla para este grupo. */
private const val INSERT_STUDENT_SQL =
    """
    INSERT INTO planificacion.miembro_grupo
        (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?, ?)
    """

/**
 * `ON CONFLICT ... DO UPDATE ... WHERE` es la guarda de orden: si la fila ya tenía un evento con `occurredAt` más
 * reciente, la cláusula `WHERE` del `DO UPDATE` no casa y Postgres deja la fila como estaba — `jdbc.update`
 * devuelve `0`, no `1`, y el llamante lo interpreta como "descartado por orden".
 */
private const val UPSERT_SQL =
    """
    INSERT INTO planificacion.miembro_grupo
        (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?, ?)
    ON CONFLICT (grupo_id, persona_id) DO UPDATE
        SET rol = EXCLUDED.rol,
            last_processed_event_id = EXCLUDED.last_processed_event_id,
            last_processed_event_ts = EXCLUDED.last_processed_event_ts
        WHERE planificacion.miembro_grupo.last_processed_event_ts <= ?
    """

/**
 * Mismo criterio de orden que [UPSERT_SQL]: no borra si la fila recogía un evento más reciente que [occurredAt].
 * Si la fila no existe, `jdbc.update` devuelve `0` filas afectadas igual que si la guarda de orden la hubiera
 * descartado -- ambos casos son "nada que hacer", no un error.
 */
private const val REMOVE_SQL =
    """
    DELETE FROM planificacion.miembro_grupo
    WHERE grupo_id = ? AND club_id = ? AND persona_id = ? AND last_processed_event_ts <= ?
    """

private const val FIND_STUDENTS_SQL =
    """
    SELECT persona_id FROM planificacion.miembro_grupo
    WHERE grupo_id = ? AND club_id = ? AND rol = ?
    """
