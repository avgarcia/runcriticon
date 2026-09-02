package com.runcriticon.seguimiento.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_MESSAGE_LENGTH = 1000

/**
 * Un reajuste de día por el alumno (LAL-33): mueve la sesión de [plannedDay] a [targetDay], o la marca como
 * saltada sin moverla. [plannedDay] es siempre el día **planificado** — la identidad estable de la sesión
 * dentro del plan, igual que [SessionReport] la referencia en `reporte_sesion` — nunca el día efectivo tras el
 * reajuste.
 *
 * [operationId] correlaciona las filas que escribe una misma operación: un `REEMPLAZAR`/`INTERCAMBIAR` escribe
 * dos filas (dos [DayAdjustment] distintos) que comparten [operationId], para poder deshacer la operación
 * completa de una vez ([WithdrawDayAdjustmentCommand] borra por `operationId`, no por día).
 *
 * [painFlag] no es un input directo del alumno: [create] lo calcula, nunca lo recibe — mismo criterio que
 * [SessionReport.painFlag], se activa solo si [reason] es [AdjustmentReason.MOLESTIAS].
 */
data class DayAdjustment(
    val operationId: UUID,
    val action: AdjustmentAction,
    val plannedDay: LocalDate,
    val targetDay: LocalDate? = null,
    val reason: AdjustmentReason,
    val message: String? = null,
    val painFlag: Boolean = false,
    val createdAt: Instant,
) {
    companion object {
        /**
         * Valida los invariantes de forma del reajuste. No valida reglas que dependen de "hoy" (rango de
         * destino, día pasado) ni de conflicto con otra sesión — esas las comprueba el caso de uso contra la
         * proyección, antes de llamar aquí.
         */
        fun create(
            operationId: UUID,
            action: AdjustmentAction,
            plannedDay: LocalDate,
            targetDay: LocalDate?,
            reason: AdjustmentReason,
            message: String?,
            createdAt: Instant,
        ): Either<SeguimientoError, DayAdjustment> =
            either {
                ensure(message == null || message.length <= MAX_MESSAGE_LENGTH) {
                    SeguimientoError.InvalidInput(field = "mensaje", reason = "message_too_long")
                }
                when (action) {
                    AdjustmentAction.MOVIDA -> {
                        ensure(targetDay != null) {
                            SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_required")
                        }
                        ensure(targetDay != plannedDay) {
                            SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_same_as_origin")
                        }
                    }

                    AdjustmentAction.SALTADA -> {
                        ensure(targetDay == null) {
                            SeguimientoError.InvalidInput(field = "diaDestino", reason = "target_day_not_allowed")
                        }
                    }
                }
                DayAdjustment(
                    operationId = operationId,
                    action = action,
                    plannedDay = plannedDay,
                    targetDay = targetDay,
                    reason = reason,
                    message = message,
                    painFlag = reason == AdjustmentReason.MOLESTIAS,
                    createdAt = createdAt,
                )
            }
    }
}
