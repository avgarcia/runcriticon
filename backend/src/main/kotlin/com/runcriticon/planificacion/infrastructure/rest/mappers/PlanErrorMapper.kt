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

        PlanificacionError.SessionNotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "SESSION_NOT_FOUND", field = null, message = "La sesión no existe en el plan"),
            )

        PlanificacionError.DuplicateSessionDay ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(code = "DUPLICATE_SESSION_DAY", field = "dia", message = "Ya hay una sesión ese día"),
            )
    }

/**
 * `reason` llega como `String`, así que este `when` no es exhaustivo y necesita `else`. El `else` **devuelve** el
 * código genérico: una razón nueva sin traducir debe salir como `INVALID_INPUT`, nunca tumbar la petición.
 *
 * Los literales de `Session.create`/`WeeklyPlan.addSession` (LAL-24) están fijados en el propio dominio — si se
 * tocan ahí sin tocar aquí, el `else` los degrada en silencio a `INVALID_INPUT` sin fallar ningún test.
 */
private fun invalidInput(
    reason: String,
    field: String,
): ResponseEntity<ErrorResponse> =
    when (reason) {
        "debe ser el lunes de la semana" ->
            badRequest("WEEK_NOT_MONDAY", field, "La semana debe empezar en lunes")

        "debe caer dentro de la semana del plan" ->
            badRequest("DAY_OUTSIDE_WEEK", field, "El día debe caer dentro de la semana del plan")

        "una sesión de descanso no lleva volumen ni ritmo" ->
            badRequest("REST_WITH_LOAD", field, "Una sesión de descanso no lleva volumen ni ritmo")

        "debe ser mayor que cero" ->
            badRequest("INVALID_VOLUME", field, "El volumen debe ser mayor que cero")

        "no puede pasar de 1000 caracteres" ->
            badRequest("NOTES_TOO_LONG", field, "Las notas no pueden pasar de 1000 caracteres")

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
