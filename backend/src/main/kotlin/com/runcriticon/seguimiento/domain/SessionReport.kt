package com.runcriticon.seguimiento.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import java.time.Instant

private const val MAX_NOTES_LENGTH = 1000
private const val MIN_RATING = 1
private const val MAX_RATING = 5

/**
 * Lo que el alumno registra sobre una sesión ejecutada (LAL-30, `docs/glosario.md` §Seguimiento): estado,
 * valoración de sensaciones, motivo si no la hizo, notas y marca de dolor.
 *
 * **Sin `painDescription`**: el glosario lo permite (marca de dolor booleana), pero el texto libre de
 * ubicación/intensidad del dolor es un dato médico derivado con pregunta jurídica abierta
 * (`docs/arquitectura/rgpd-en-modulos.md` §9, pendiente del módulo) — queda fuera de esta historia. La
 * columna `descripcion_dolor` se crea en la migración pero nunca se rellena, mismo patrón que
 * `mensaje_al_alumno` en LAL-29.
 *
 * [painFlag] no es un input directo del alumno: [create] lo calcula, nunca lo recibe — se activa solo si
 * [reason] es [NotDoneReason.MOLESTIAS] ("que también se activa de forma automática al elegir «molestias»
 * como motivo", glosario). El constructor primario lo acepta para que la capa de lectura pueda reconstruir
 * el reporte tal cual está en la fila, sin volver a derivarlo.
 */
data class SessionReport(
    val status: ReportStatus,
    val rating: Int? = null,
    val reason: NotDoneReason? = null,
    val notes: String? = null,
    val painFlag: Boolean = false,
    val reportedAt: Instant,
) {
    companion object {
        /**
         * Valida los cuatro invariantes del glosario y calcula [painFlag]. No valida que exista una sesión
         * publicada ese día — eso lo comprueba el caso de uso contra la proyección, antes de llamar aquí.
         */
        fun create(
            status: ReportStatus,
            rating: Int?,
            reason: NotDoneReason?,
            notes: String?,
            reportedAt: Instant,
        ): Either<SeguimientoError, SessionReport> =
            either {
                ensure(notes == null || notes.length <= MAX_NOTES_LENGTH) {
                    SeguimientoError.InvalidInput(field = "notas", reason = "notes_too_long")
                }
                when (status) {
                    ReportStatus.HECHO, ReportStatus.PARCIAL -> {
                        ensure(rating != null) {
                            SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_required")
                        }
                        ensure(rating in MIN_RATING..MAX_RATING) {
                            SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_out_of_range")
                        }
                        ensure(reason == null) {
                            SeguimientoError.InvalidInput(field = "motivo", reason = "reason_not_allowed")
                        }
                    }

                    ReportStatus.NO_HECHO -> {
                        ensure(rating == null) {
                            SeguimientoError.InvalidInput(field = "valoracion", reason = "rating_not_allowed")
                        }
                        ensure(reason != null) {
                            SeguimientoError.InvalidInput(field = "motivo", reason = "reason_required")
                        }
                    }
                }
                SessionReport(
                    status = status,
                    rating = rating,
                    reason = reason,
                    notes = notes,
                    painFlag = reason == NotDoneReason.MOLESTIAS,
                    reportedAt = reportedAt,
                )
            }
    }
}
