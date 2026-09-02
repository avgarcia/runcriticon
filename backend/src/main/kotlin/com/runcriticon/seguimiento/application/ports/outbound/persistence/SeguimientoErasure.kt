package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.StudentId

/**
 * Borrado físico de todo lo que este módulo guarda de un alumno, al ejercer su derecho de supresión. Mismo
 * criterio que `PlanificacionErasure`/`PersonErasure`: idempotente, válido también sobre un alumno que este
 * módulo nunca llegó a proyectar.
 */
interface SeguimientoErasure {
    fun erase(studentId: StudentId): ErasedRows
}

/** Recuento de lo borrado, para el log del listener. */
data class ErasedRows(
    val resolvedSessions: Int,
    val sessionReports: Int = 0,
    val consentRows: Int = 0,
    val markRows: Int = 0,
    val adjustmentRows: Int = 0,
)
