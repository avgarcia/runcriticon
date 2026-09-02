package com.runcriticon.seguimiento.infrastructure.rest.mappers

import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.api.rest.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/** Mapea [SeguimientoError] a respuesta HTTP estructurada `{code, field?, message}` (ADR-0012 D19). */
fun SeguimientoError.toErrorResponse(): ResponseEntity<ErrorResponse> =
    when (this) {
        SeguimientoError.Forbidden ->
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse(code = "FORBIDDEN", field = null, message = "Acceso denegado"),
            )

        is SeguimientoError.InvalidInput -> invalidInput(reason, field)

        SeguimientoError.SessionNotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(
                    code = "NO_SESSION_THAT_DAY",
                    field = null,
                    message = "No hay ninguna sesión publicada ese día",
                ),
            )

        SeguimientoError.ConsentNotGranted ->
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse(
                    code = "CONSENTIMIENTO_NO_VIGENTE",
                    field = null,
                    message = "Necesitas dar tu consentimiento de datos de salud antes de reportar",
                ),
            )

        // Reajuste de día (LAL-33, RescheduleDayCommand): el día destino ya tiene una sesión efectiva y la
        // petición no trajo `resolucionConflicto`. El diálogo del alumno ofrece Reemplazar/Intercambiar/
        // Cancelar y reintenta con la resolución elegida.
        SeguimientoError.TargetDayOccupied ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(
                    code = "DIA_DESTINO_OCUPADO",
                    field = null,
                    message = "Ese día ya tiene una sesión",
                ),
            )
    }

/**
 * `reason` es `String`, no una sealed class, así que este `when` no es exhaustivo y necesita `else`. El `else`
 * degrada al código genérico en vez de lanzar: mismo criterio que el resto de `ErrorMapper` del repo.
 */
private fun invalidInput(
    reason: String,
    field: String,
): ResponseEntity<ErrorResponse> = rescheduleInvalidInput(reason, field) ?: reportInvalidInput(reason, field)

private fun reportInvalidInput(
    reason: String,
    field: String,
): ResponseEntity<ErrorResponse> =
    when (reason) {
        // Mismo `code` que ya traduce el frontend para planificacion (error-codes.ts): la semántica es
        // idéntica ("la semana debe empezar en lunes"), reutilizarlo evita duplicar el catálogo.
        "week_not_monday" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "WEEK_NOT_MONDAY", field = field, message = "La semana debe empezar en lunes"),
            )

        // Códigos propios del reporte de sesión (LAL-30, SubmitSessionReportCommand/SessionReport.create):
        // el frontend distingue estos cuatro para mensajes específicos en el diálogo; el resto de invariantes
        // (rating_out_of_range, reason_not_allowed, rating_not_allowed) degradan al genérico INVALID_INPUT,
        // porque el formulario ya impide llegar a ellos por UI (escala fija, motivo oculto según estado).
        "future_day" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "FUTURE_DAY", field = field, message = "No se puede reportar un día futuro"),
            )

        "rating_required" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "VALORACION_REQUERIDA", field = field, message = "Indica cómo te has sentido"),
            )

        "reason_required" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "MOTIVO_REQUERIDO", field = field, message = "Indica el motivo"),
            )

        "notes_too_long" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "NOTES_TOO_LONG", field = field, message = "La nota es demasiado larga"),
            )

        // Marca del alumno (LAL-31, StudentMark.create): único invariante de dominio, tiempoSegundos > 0.
        "not_positive" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "TIEMPO_INVALIDO", field = field, message = "El tiempo debe ser mayor que cero"),
            )

        else ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "INVALID_INPUT", field = field, message = "Revisa los datos introducidos"),
            )
    }

/**
 * Códigos propios del reajuste de día (LAL-33, RescheduleDayCommand/DayAdjustment.create), aparte de
 * [reportInvalidInput] para no superar el límite de longitud de función (detekt `LongMethod`). El frontend
 * distingue estos dos para el mensaje del diálogo; el resto de invariantes (target_day_required,
 * target_day_same_as_origin, target_day_not_allowed, message_too_long) degradan al genérico INVALID_INPUT,
 * porque el diálogo ya impide llegar a ellos por UI (solo ofrece días del rango, y MOVER/SALTAR fijan si pide
 * destino o no). `null` cuando [reason] no es de este catálogo, para que [invalidInput] siga con el genérico.
 */
private fun rescheduleInvalidInput(
    reason: String,
    field: String,
): ResponseEntity<ErrorResponse>? =
    when (reason) {
        "past_day" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "DIA_PASADO", field = field, message = "No se puede reajustar un día pasado"),
            )

        "target_day_out_of_range" ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(
                    code = "DESTINO_FUERA_DE_RANGO",
                    field = field,
                    message = "El día destino debe estar dentro de los próximos 7 días",
                ),
            )

        else -> null
    }
