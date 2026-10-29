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
 *
 * La guarda de orden vive en `grupo_entrenador_version`, no en la fila: la baja borra la fila, y una asignación
 * anterior entregada después la volvería a insertar sin nada contra lo que compararse. Cada escritura avanza
 * primero la marca de agua y solo toca la fila si la marca avanzó, dentro de la transacción del listener: el
 * bloqueo de la fila de marca de agua serializa además dos entregas en paralelo del mismo par.
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
    ): Boolean {
        if (!advanceWatermark(clubId, groupId, coachId, eventId, occurredAt)) return false
        jdbc.update(UPSERT_SQL, groupId.value, clubId.value, coachId.value, eventId, Timestamp.from(occurredAt))
        return true
    }

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
    ): Boolean {
        if (!advanceWatermark(clubId, groupId, coachId, eventId, occurredAt)) return false
        jdbc.update(REMOVE_SQL, groupId.value, clubId.value, coachId.value)
        return true
    }

    private fun advanceWatermark(
        clubId: ClubId,
        groupId: GroupId,
        coachId: CoachId,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean {
        val timestamp = Timestamp.from(occurredAt)
        return jdbc.update(
            ADVANCE_WATERMARK_SQL,
            groupId.value,
            coachId.value,
            clubId.value,
            eventId,
            timestamp,
            timestamp,
        ) == 1
    }
}

/**
 * `ON CONFLICT ... DO UPDATE ... WHERE` es la guarda de orden: si el par ya había recogido un evento más reciente,
 * la cláusula `WHERE` no casa, Postgres deja la marca como estaba y `jdbc.update` devuelve `0`. Mismo patrón que
 * `GroupMembersProjectionJdbc.UPSERT_VERSION_SQL`.
 */
private const val ADVANCE_WATERMARK_SQL =
    """
    INSERT INTO seguimiento.grupo_entrenador_version
        (grupo_id, entrenador_id, club_id, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (grupo_id, entrenador_id) DO UPDATE
        SET club_id = EXCLUDED.club_id,
            last_processed_event_id = EXCLUDED.last_processed_event_id,
            last_processed_event_ts = EXCLUDED.last_processed_event_ts
        WHERE seguimiento.grupo_entrenador_version.last_processed_event_ts <= ?
    """

/** Sin guarda propia: solo se ejecuta si [ADVANCE_WATERMARK_SQL] avanzó la marca. */
private const val UPSERT_SQL =
    """
    INSERT INTO seguimiento.grupo_entrenador
        (grupo_id, club_id, entrenador_id, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (grupo_id, entrenador_id) DO UPDATE
        SET last_processed_event_id = EXCLUDED.last_processed_event_id,
            last_processed_event_ts = EXCLUDED.last_processed_event_ts
    """

/** Sin guarda propia, igual que [UPSERT_SQL]. Si la fila no existe no hay nada que quitar: no es un error. */
private const val REMOVE_SQL =
    "DELETE FROM seguimiento.grupo_entrenador WHERE grupo_id = ? AND club_id = ? AND entrenador_id = ?"
