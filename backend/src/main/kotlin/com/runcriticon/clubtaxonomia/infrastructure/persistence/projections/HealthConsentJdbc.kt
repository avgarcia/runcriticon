package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.HealthConsentProjection
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.HealthConsentReader
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Adaptador de [HealthConsentProjection] sobre `JdbcTemplate`. Misma guarda de supresión que `PersonProjectionJdbc`:
 * lock por persona ([lockPerson]) y comprobación de la lápida antes de escribir.
 */
@Repository
class HealthConsentProjectionJdbc(
    private val jdbc: JdbcTemplate,
) : HealthConsentProjection {
    @NoAuthScope(
        justificacion =
            "Invocado por HealthConsentListener sin principal; el alumno y el club los trae el evento de " +
                "identidad, no entrada de usuario.",
    )
    override fun upsert(
        clubId: ClubId,
        studentId: PersonId,
        granted: Boolean,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean {
        lockPerson(jdbc, studentId.value)
        val erased = jdbc.queryForObject(IS_ERASED_SQL, Boolean::class.java, studentId.value) ?: false
        if (erased) return false
        val timestamp = Timestamp.from(occurredAt)
        return jdbc.update(UPSERT_SQL, studentId.value, clubId.value, granted, eventId, timestamp, timestamp) > 0
    }
}

/**
 * Adaptador de [HealthConsentReader]: la guarda de asignación corre siempre con principal (una petición, o un listener
 * dentro de `ListenerPrincipalScope`), de ahí `@AuthScope(Scope.CLUB)`.
 */
@Repository
class HealthConsentReaderJdbc(
    private val jdbc: JdbcTemplate,
) : HealthConsentReader {
    @AuthScope(Scope.CLUB)
    override fun isGranted(
        clubId: ClubId,
        studentId: PersonId,
    ): Boolean =
        jdbc
            .query(IS_GRANTED_SQL, { rs, _ -> rs.getBoolean("vigente") }, clubId.value, studentId.value)
            .firstOrNull() ?: false

    @AuthScope(Scope.CLUB)
    override fun grantedAmong(
        clubId: ClubId,
        students: Set<PersonId>,
    ): Set<PersonId> {
        if (students.isEmpty()) return emptySet()
        val ids = students.map { it.value }.toTypedArray()
        return jdbc
            .query(
                GRANTED_AMONG_SQL,
                { statement: PreparedStatement ->
                    statement.setObject(1, clubId.value)
                    statement.setArray(2, statement.connection.createArrayOf("uuid", ids))
                },
                { rs: ResultSet, _: Int -> PersonId.of(rs.getObject("alumno_id", UUID::class.java)) },
            ).toSet()
    }
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método del `@Repository` sin `@AuthScope`/`@NoAuthScope`.

/** `WHERE ... <= ?` es la guarda de orden: si la fila ya refleja un evento posterior no se toca y devuelve 0 filas. */
private val UPSERT_SQL =
    """
    INSERT INTO club_taxonomia.consentimiento_alumno
        (alumno_id, club_id, vigente, last_processed_event_id, last_processed_event_ts)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (alumno_id) DO UPDATE SET
        club_id                  = EXCLUDED.club_id,
        vigente                  = EXCLUDED.vigente,
        last_processed_event_id  = EXCLUDED.last_processed_event_id,
        last_processed_event_ts  = EXCLUDED.last_processed_event_ts
    WHERE club_taxonomia.consentimiento_alumno.last_processed_event_ts <= ?
    """.trimIndent()

private const val IS_ERASED_SQL = "SELECT EXISTS(SELECT 1 FROM club_taxonomia.persona_eliminada WHERE id = ?)"

private const val IS_GRANTED_SQL =
    "SELECT vigente FROM club_taxonomia.consentimiento_alumno WHERE club_id = ? AND alumno_id = ?"

private const val GRANTED_AMONG_SQL =
    "SELECT alumno_id FROM club_taxonomia.consentimiento_alumno WHERE club_id = ? AND alumno_id = ANY (?) AND vigente"
