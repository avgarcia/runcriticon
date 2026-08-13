package com.runcriticon.planificacion.infrastructure.rest.mappers

import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.shared.api.rest.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/** Mapea [PlanificacionError] a respuesta HTTP estructurada. Mismo criterio que `ErrorMapper` de `club_taxonomia`. */
fun PlanificacionError.toErrorResponse(): ResponseEntity<ErrorResponse> =
    when (this) {
        PlanificacionError.Forbidden ->
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse(code = "FORBIDDEN", field = null, message = "Acceso denegado"),
            )

        is PlanificacionError.InvalidInput -> invalidInput(reason, field)
    }

/**
 * `reason` llega como `String`, así que este `when` no es exhaustivo y necesita `else`. El `else` **devuelve** el
 * código genérico: una razón nueva sin traducir debe salir como `INVALID_INPUT`, nunca tumbar la petición.
 */
private fun invalidInput(
    reason: String,
    field: String,
): ResponseEntity<ErrorResponse> =
    when (reason) {
        "debe ser el lunes de la semana" ->
            badRequest("WEEK_NOT_MONDAY", field, "La semana debe empezar en lunes")

        else ->
            badRequest("INVALID_INPUT", field, "Revisa los datos introducidos")
    }

private fun badRequest(
    code: String,
    field: String,
    message: String,
): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
        ErrorResponse(code = code, field = field, message = message),
    )
