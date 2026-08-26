package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentProjection
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Adaptador de [ConsentProjection] sobre `JdbcTemplate`. Se invoca desde `ConsentProjectionListener`, que
 * corre sin principal — el `studentId` ya viene resuelto del `aggregateId` del evento de consentimiento.
 */
@Repository
class ConsentProjectionJdbc(
    private val jdbc: JdbcTemplate,
) : ConsentProjection {
    @NoAuthScope(
        justificacion =
            "Invocado por un listener de eventos sin principal; studentId ya viene resuelto del aggregateId " +
                "del evento, no hay clubId de principal contra el que verificar.",
    )
    override fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        granted: Boolean,
        textVersion: String,
        eventId: UUID,
        occurredAt: Instant,
    ) {
        val timestamp = Timestamp.from(occurredAt)
        jdbc.update(UPSERT_SQL, studentId.value, clubId.value, granted, textVersion, eventId, timestamp, timestamp)
    }

    @NoAuthScope(
        justificacion =
            "Invocado por SeguimientoDeletionListener sin principal; studentId ya viene resuelto del " +
                "aggregateId del evento AlumnoEliminado.",
    )
    override fun deleteByStudentId(studentId: StudentId): Int = jdbc.update(DELETE_SQL, studentId.value)
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método del `@Repository` sin `@AuthScope`/`@NoAuthScope`.
private val UPSERT_SQL =
    """
    INSERT INTO seguimiento.consentimiento_alumno
        (alumno_id, club_id, vigente, version_texto, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?, ?)
    ON CONFLICT (alumno_id) DO UPDATE SET
        club_id                  = EXCLUDED.club_id,
        vigente                  = EXCLUDED.vigente,
        version_texto            = EXCLUDED.version_texto,
        last_processed_event_id  = EXCLUDED.last_processed_event_id,
        last_processed_event_ts  = EXCLUDED.last_processed_event_ts
    WHERE seguimiento.consentimiento_alumno.last_processed_event_ts <= ?
    """.trimIndent()

private const val DELETE_SQL = "DELETE FROM seguimiento.consentimiento_alumno WHERE alumno_id = ?"
