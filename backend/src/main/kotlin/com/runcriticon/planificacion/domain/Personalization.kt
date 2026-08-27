package com.runcriticon.planificacion.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

/**
 * Personalización de una sesión para un alumno concreto (ADR-0002 D9, LAL-26). [override] sustituye por
 * completo a la sesión base que referencia [sessionId] — mismo shape ([SessionOverride]), sin patch
 * parcial —, más un [messageToStudent] opcional que solo ve ese alumno.
 *
 * Única por (plan, sesión, alumno) en persistencia (`personalizacion_plan_sesion_alumno_uk`); la mitad de
 * dominio de esa regla vive en `WeeklyPlan.setPersonalization` (editar reemplaza, no acumula).
 */
data class Personalization(
    val id: PersonalizationId,
    val sessionId: SessionId,
    val studentId: PersonId,
    val override: SessionOverride,
    val messageToStudent: String? = null,
) {
    companion object {
        fun create(
            sessionId: SessionId,
            studentId: PersonId,
            override: SessionOverride,
            messageToStudent: String? = null,
            id: PersonalizationId = PersonalizationId.new(),
        ): Either<PlanificacionError, Personalization> =
            either {
                ensure(messageToStudent == null || messageToStudent.length <= MAX_NOTES_LENGTH) {
                    PlanificacionError.InvalidInput(
                        field = "mensajeAlAlumno",
                        reason = "no puede pasar de 1000 caracteres",
                    )
                }
                Personalization(
                    id = id,
                    sessionId = sessionId,
                    studentId = studentId,
                    override = override,
                    messageToStudent = messageToStudent,
                )
            }
    }
}
