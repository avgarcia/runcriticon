package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkLookup
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

/**
 * Adaptador de [StudentMarkLookup] sobre `JdbcTemplate` (ritmos resueltos por alumno): lee `seguimiento.marca_alumno`
 * sin
 * principal, para los listeners que resuelven ritmos relativos (`ResolvedPlanProjectionListener`,
 * `PersonalizationProjectionListener`, `MarkPaceRecalculationListener`).
 *
 * No implementa `StudentMarkRepository` (el puerto de los casos de uso): ese sirve peticiones con `Principal`
 * y va `@AuthScope(Scope.CLUB)`; mezclar ambos en la misma interfaz obligaría a ese aspecto a fallar cerrado
 * en cuanto un listener llamara a `findMark`/`findMarks` sin principal.
 */
@Repository
class StudentMarkLookupJdbc(
    private val jdbc: JdbcTemplate,
) : StudentMarkLookup {
    @NoAuthScope(
        justificacion =
            "Lectura dirigida por integration events (PlanPublicado/PersonalizacionAplicada/MarcaActualizada): " +
                "sin principal en el listener; el club_id proviene del evento, no de entrada de usuario.",
    )
    override fun findMark(
        clubId: ClubId,
        studentId: StudentId,
        distance: RaceDistance,
    ): StudentMark? =
        jdbc
            .query(FIND_MARK_SQL, MARK_ROW_MAPPER, studentId.value, distance.literal, clubId.value)
            .firstOrNull()

    @NoAuthScope(
        justificacion =
            "Lectura dirigida por integration events (PlanPublicado): sin principal en el listener; el " +
                "club_id proviene del evento, no de entrada de usuario.",
    )
    override fun findMarks(
        clubId: ClubId,
        students: Set<StudentId>,
    ): Map<StudentId, Map<RaceDistance, StudentMark>> {
        if (students.isEmpty()) return emptyMap()
        val ids = students.map { it.value }.toTypedArray()
        return jdbc
            .query(
                FIND_MARKS_SQL,
                { statement: PreparedStatement ->
                    statement.setArray(1, statement.connection.createArrayOf("uuid", ids))
                    statement.setObject(2, clubId.value)
                },
                { rs: ResultSet, _: Int ->
                    StudentId.of(rs.getObject("alumno_id", UUID::class.java)) to
                        StudentMark(
                            distance = RaceDistance.fromLiteral(rs.getString("distancia")),
                            timeSeconds = rs.getInt("tiempo_segundos"),
                            modifiedAt = rs.getTimestamp("modificado_en").toInstant(),
                        )
                },
            ).groupBy({ it.first }, { it.second })
            .mapValues { (_, marks) -> marks.associateBy { it.distance } }
    }
}

private val MARK_ROW_MAPPER =
    RowMapper { rs, _ ->
        StudentMark(
            distance = RaceDistance.fromLiteral(rs.getString("distancia")),
            timeSeconds = rs.getInt("tiempo_segundos"),
            modifiedAt = rs.getTimestamp("modificado_en").toInstant(),
        )
    }

private const val FIND_MARK_SQL =
    """
    SELECT distancia, tiempo_segundos, modificado_en
    FROM seguimiento.marca_alumno
    WHERE alumno_id = ? AND distancia = ? AND club_id = ?
    """

private const val FIND_MARKS_SQL =
    """
    SELECT alumno_id, distancia, tiempo_segundos, modificado_en
    FROM seguimiento.marca_alumno
    WHERE alumno_id = ANY (?) AND club_id = ?
    """
