package com.runcriticon.seguimiento.infrastructure.persistence.projections

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ConsentProjection
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
    private val consentProjection: ConsentProjection,
) : SeguimientoErasure {
    @NoAuthScope(
        justificacion =
            "Invocado por un listener de eventos sin principal; studentId ya viene resuelto del aggregateId " +
                "del evento, no hay clubId de principal contra el que verificar.",
    )
    override fun erase(studentId: StudentId): ErasedRows {
        // Los reportes primero: sin FK entre las dos tablas (ninguna cruza esquema ni siquiera dentro del
        // mismo), pero borrar en este orden evita dejar un reporte huérfano visible si el proceso se
        // interrumpe entre las dos sentencias — el read model desaparece antes que el detalle que lo adjunta.
        val reports = jdbc.update(DELETE_REPORTS_BY_STUDENT_SQL, studentId.value)
        val resolved = jdbc.update(DELETE_BY_STUDENT_SQL, studentId.value)
        // Marcas (LAL-31): categoría 1, dato de salud (ADR-0014 D5) — borrado físico igual que el resto.
        val marks = jdbc.update(DELETE_MARKS_BY_STUDENT_SQL, studentId.value)
        // Sin datos personales (SIN_PII), pero se limpia igualmente: un alumno eliminado no debe dejar rastro
        // en ninguna proyección de este módulo, y evita una fila fantasma si el club reactiva el mismo email.
        val consent = consentProjection.deleteByStudentId(studentId)
        return ErasedRows(
            resolvedSessions = resolved,
            sessionReports = reports,
            consentRows = consent,
            markRows = marks,
        )
    }
}

private const val DELETE_REPORTS_BY_STUDENT_SQL = "DELETE FROM seguimiento.reporte_sesion WHERE alumno_id = ?"
private const val DELETE_BY_STUDENT_SQL = "DELETE FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ?"
private const val DELETE_MARKS_BY_STUDENT_SQL = "DELETE FROM seguimiento.marca_alumno WHERE alumno_id = ?"
