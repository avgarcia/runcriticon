package com.runcriticon.seguimiento.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Una alerta accionable del entrenador sobre un alumno de sus grupos (M17). Sin comportamiento —
 * value object de solo lectura, análogo a [ResolvedPace]: la regla que decide si una fila de
 * `reporte_sesion` genera alerta vive en [CoachAlertReader][com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachAlertReader]
 * y en [matchesPaceOffTargetHeuristic], no aquí.
 *
 * 4 tipos en el MVP (recorte deliberado frente a los 9 de `docs/wireframes/
 * 08-coach-alerts.md`): molestias reportadas, alumno sin reportar más de 7 días, ritmo muy fuera del
 * objetivo, y lesión declarada. Sin "descartar": el panel es de solo lectura, una alerta deja de
 * listarse sola cuando deja de cumplirse su condición — no hay estado propio que persistir.
 */
sealed interface CoachAlert {
    val studentId: StudentId
    val groupId: GroupId

    /** El alumno marcó la flag de dolor al reportar una sesión. */
    data class PainReported(
        override val studentId: StudentId,
        override val groupId: GroupId,
        val day: LocalDate,
        val notes: String?,
        val reportedAt: Instant,
    ) : CoachAlert

    /** El alumno lleva más de 7 días sin reportar ninguna sesión, con plan publicado en ese periodo. */
    data class NoReportInDays(
        override val studentId: StudentId,
        override val groupId: GroupId,
        val daysSinceLastReport: Long,
        val lastReportedAt: Instant?,
    ) : CoachAlert

    /** Sesión reportada con una nota que sugiere un ritmo muy distinto al objetivo — ver
     * [matchesPaceOffTargetHeuristic]. */
    data class PaceOffTarget(
        override val studentId: StudentId,
        override val groupId: GroupId,
        val day: LocalDate,
        val notes: String,
    ) : CoachAlert

    /** El alumno avisó de lesión desde el reajuste de día — URGENTE, se genere o no
     * [com.runcriticon.seguimiento.api.events.LesionDeclarada]: el aviso al entrenador no depende de la
     * confirmación del cambio de `estado`. */
    data class InjuryDeclared(
        override val studentId: StudentId,
        override val groupId: GroupId,
        val day: LocalDate,
        val message: String?,
    ) : CoachAlert
}
