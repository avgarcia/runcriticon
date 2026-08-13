package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.planificacion.application.ports.outbound.persistence.GroupMembersProjection
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
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
}

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
