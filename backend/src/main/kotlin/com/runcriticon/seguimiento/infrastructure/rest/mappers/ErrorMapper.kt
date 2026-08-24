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
    }

/**
 * `reason` es `String`, no una sealed class, así que este `when` no es exhaustivo y necesita `else`. El `else`
 * degrada al código genérico en vez de lanzar: mismo criterio que el resto de `ErrorMapper` del repo.
 */
private fun invalidInput(
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

        else ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "INVALID_INPUT", field = field, message = "Revisa los datos introducidos"),
            )
    }
