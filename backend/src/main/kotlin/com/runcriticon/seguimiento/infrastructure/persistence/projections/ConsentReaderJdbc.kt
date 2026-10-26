package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentReader
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

/**
 * Adaptador de [ConsentReader] sobre `JdbcTemplate`. Sin `Scope.OWNED`: el aspecto de autorización no lo
 * implementa todavía y falla cerrado (lección de la vista "hoy" del alumno y el reporte de sesión) — el
 * `studentId` nunca llega de un parámetro de entrada, siempre de `actor.userId`.
 */
@Repository
class ConsentReaderJdbc(
    private val jdbc: JdbcTemplate,
) : ConsentReader {
    @AuthScope(Scope.CLUB)
    override fun isGranted(
        clubId: ClubId,
        studentId: StudentId,
    ): Boolean =
        jdbc
            .query(IS_GRANTED_SQL, { rs, _ -> rs.getBoolean("vigente") }, clubId.value, studentId.value)
            .firstOrNull() ?: false

    @AuthScope(Scope.CLUB)
    override fun grantedAmong(
        clubId: ClubId,
        students: Set<StudentId>,
    ): Set<StudentId> {
        if (students.isEmpty()) return emptySet()
        val ids = students.map { it.value }.toTypedArray()
        return jdbc
            .query(
                GRANTED_AMONG_SQL,
                { statement: PreparedStatement ->
                    statement.setObject(1, clubId.value)
                    statement.setArray(2, statement.connection.createArrayOf("uuid", ids))
                },
                { rs: ResultSet, _: Int -> StudentId.of(rs.getObject("alumno_id", UUID::class.java)) },
            ).toSet()
    }
}

private const val IS_GRANTED_SQL =
    "SELECT vigente FROM seguimiento.consentimiento_alumno WHERE club_id = ? AND alumno_id = ?"

private const val GRANTED_AMONG_SQL =
    "SELECT alumno_id FROM seguimiento.consentimiento_alumno WHERE club_id = ? AND alumno_id = ANY (?) AND vigente"
