package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachGroupProjection
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Adaptador de [CoachGroupProjection] sobre `JdbcTemplate`, contra `seguimiento.grupo_entrenador`.
 *
 * Se invoca desde `CoachGroupProjectionListener`, que corre sin principal (fuera de una petición HTTP) —
 * mismo motivo que `@NoAuthScope` en `GroupMembersProjectionJdbc` de `planificacion`: el `club_id` de la
 * fila lo trae el propio evento, no hay principal contra el que verificarlo.
 */
@Repository
class CoachGroupProjectionJdbc(
    private val jdbc: JdbcTemplate,
) : CoachGroupProjection {
    @NoAuthScope(
        justificacion =
            "Escrito por un listener de eventos sin principal (fuera de una petición HTTP); el club_id de la " +
                "fila lo trae el propio evento, no hay contra qué verificarlo.",
    )
    override fun upsert(
        clubId: ClubId,
        groupId: GroupId,
        coachId: CoachId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean =
        jdbc.update(
            UPSERT_SQL,
            groupId.value,
            clubId.value,
            coachId.value,
            eventId,
            Timestamp.from(occurredAt),
            Timestamp.from(occurredAt),
        ) == 1

    @NoAuthScope(
        justificacion =
            "Escrito por un listener de eventos sin principal (fuera de una petición HTTP); el club_id de la " +
                "fila lo trae el propio evento, no hay contra qué verificarlo.",
    )
    override fun remove(
        clubId: ClubId,
        groupId: GroupId,
        coachId: CoachId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean =
        jdbc.update(
            REMOVE_SQL,
            groupId.value,
            clubId.value,
            coachId.value,
            Timestamp.from(occurredAt),
        ) == 1
}

/**
 * `ON CONFLICT ... DO UPDATE ... WHERE` es la guarda de orden: si la fila ya tenía un evento con `occurredAt`
 * más reciente, la cláusula `WHERE` del `DO UPDATE` no casa y Postgres deja la fila como estaba —
 * `jdbc.update` devuelve `0`, no `1`. Mismo patrón que `GroupMembersProjectionJdbc.UPSERT_SQL`.
 */
private const val UPSERT_SQL =
    """
    INSERT INTO seguimiento.grupo_entrenador
        (grupo_id, club_id, entrenador_id, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (grupo_id, entrenador_id) DO UPDATE
        SET last_processed_event_id = EXCLUDED.last_processed_event_id,
            last_processed_event_ts = EXCLUDED.last_processed_event_ts
        WHERE seguimiento.grupo_entrenador.last_processed_event_ts <= ?
    """

/**
 * Mismo criterio de orden que [UPSERT_SQL]: no borra si la fila recogía un evento más reciente que
 * [occurredAt]. Si la fila no existe, `jdbc.update` devuelve `0` igual que si la guarda de orden la hubiera
 * descartado — ambos casos son "nada que hacer", no un error.
 */
private const val REMOVE_SQL =
    """
    DELETE FROM seguimiento.grupo_entrenador
    WHERE grupo_id = ? AND club_id = ? AND entrenador_id = ? AND last_processed_event_ts <= ?
    """
