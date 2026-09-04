package com.runcriticon.seguimiento.application.ports.outbound.persistence

import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.StudentId

/**
 * Borrado físico de todo lo que este módulo guarda de un alumno o un entrenador, al ejercer su derecho de
 * supresión. Mismo criterio que `PlanificacionErasure`/`PersonErasure`: idempotente, válido también sobre
 * alguien que este módulo nunca llegó a proyectar.
 */
interface SeguimientoErasure {
    fun erase(studentId: StudentId): ErasedRows

    /** Borra las filas de `grupo_entrenador` de [coachId] (LAL-116). Método aparte de [erase]: agregados de
     * sujeto distinto, no tiene sentido bundlearlos en [ErasedRows]. */
    fun eraseCoach(coachId: CoachId): Int
}

/** Recuento de lo borrado, para el log del listener. */
data class ErasedRows(
    val resolvedSessions: Int,
    val sessionReports: Int = 0,
    val consentRows: Int = 0,
    val markRows: Int = 0,
    val adjustmentRows: Int = 0,
)
