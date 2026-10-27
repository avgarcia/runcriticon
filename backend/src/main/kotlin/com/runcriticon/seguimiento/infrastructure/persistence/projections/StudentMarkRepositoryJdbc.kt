package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkRepository
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.Timestamp

/**
 * Adaptador de [StudentMarkRepository] sobre `JdbcTemplate` (marcas del alumno). Sin `@Entity`: este módulo
 * va 100 % JDBC, mismo criterio que el resto de `seguimiento`.
 *
 * Sin `Scope.OWNED`: el aspecto de autorización no lo implementa todavía y falla cerrado (lección de la vista
 * "hoy" del alumno y el reporte de sesión) — el `studentId` nunca llega de un parámetro de entrada, siempre
 * de `actor.userId` en el caso de uso.
 */
@Repository
class StudentMarkRepositoryJdbc(
    private val jdbc: JdbcTemplate,
) : StudentMarkRepository {
    @AuthScope(Scope.CLUB)
    override fun findAll(
        clubId: ClubId,
        studentId: StudentId,
    ): Map<RaceDistance, StudentMark> =
        jdbc
            .query(FIND_ALL_SQL, MARK_ROW_MAPPER, studentId.value, clubId.value)
            .associateBy { it.distance }

    @AuthScope(Scope.CLUB)
    override fun upsert(
        clubId: ClubId,
        studentId: StudentId,
        mark: StudentMark,
    ) {
        jdbc.update(
            UPSERT_SQL,
            studentId.value,
            mark.distance.literal,
            mark.timeSeconds,
            clubId.value,
            Timestamp.from(mark.modifiedAt),
        )
    }

    @AuthScope(Scope.CLUB)
    override fun delete(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
    ): Boolean = jdbc.update(DELETE_SQL, studentId.value, distance.literal, clubId.value) > 0
}

// A nivel de fichero, no en `companion object`: un val de companion genera un accesor sintético público que
// AuthorizationArchTest contaría como método del `@Repository` sin `@AuthScope`/`@NoAuthScope`.
private val MARK_ROW_MAPPER =
    RowMapper { rs, _ ->
        StudentMark(
            distance = RaceDistance.fromLiteral(rs.getString("distancia")),
            timeSeconds = rs.getInt("tiempo_segundos"),
            modifiedAt = rs.getTimestamp("modificado_en").toInstant(),
        )
    }

private const val FIND_ALL_SQL =
    "SELECT distancia, tiempo_segundos, modificado_en FROM seguimiento.marca_alumno WHERE alumno_id = ? AND club_id = ?"

private val UPSERT_SQL =
    """
    INSERT INTO seguimiento.marca_alumno (alumno_id, distancia, tiempo_segundos, club_id, modificado_en)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT (alumno_id, distancia) DO UPDATE SET
        tiempo_segundos = EXCLUDED.tiempo_segundos,
        club_id         = EXCLUDED.club_id,
        modificado_en   = EXCLUDED.modificado_en
    """.trimIndent()

private const val DELETE_SQL =
    "DELETE FROM seguimiento.marca_alumno WHERE alumno_id = ? AND distancia = ? AND club_id = ?"
