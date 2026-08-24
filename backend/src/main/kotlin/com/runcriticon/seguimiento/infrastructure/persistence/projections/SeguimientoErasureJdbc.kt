package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ErasedRows
import com.runcriticon.seguimiento.application.ports.outbound.persistence.SeguimientoErasure
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Adaptador de [SeguimientoErasure] sobre `JdbcTemplate`. Se invoca desde `SeguimientoDeletionListener`, que
 * corre sin principal — el `studentId` ya viene resuelto del `aggregateId` del evento `AlumnoEliminado`.
 */
@Repository
class SeguimientoErasureJdbc(
    private val jdbc: JdbcTemplate,
) : SeguimientoErasure {
    @NoAuthScope(
        justificacion =
            "Invocado por un listener de eventos sin principal; studentId ya viene resuelto del aggregateId " +
                "del evento, no hay clubId de principal contra el que verificar.",
    )
    override fun erase(studentId: StudentId): ErasedRows {
        val deleted = jdbc.update(DELETE_BY_STUDENT_SQL, studentId.value)
        return ErasedRows(resolvedSessions = deleted)
    }
}

private const val DELETE_BY_STUDENT_SQL = "DELETE FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ?"
