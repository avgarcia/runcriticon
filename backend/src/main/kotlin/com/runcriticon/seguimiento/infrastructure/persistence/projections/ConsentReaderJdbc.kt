package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentReader
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Adaptador de [ConsentReader] sobre `JdbcTemplate`. Sin `Scope.OWNED`: el aspecto de autorización no lo
 * implementa todavía y falla cerrado (lección de LAL-29/LAL-30) — el `studentId` nunca llega de un parámetro
 * de entrada, siempre de `actor.userId`.
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
}

private const val IS_GRANTED_SQL =
    "SELECT vigente FROM seguimiento.consentimiento_alumno WHERE club_id = ? AND alumno_id = ?"
