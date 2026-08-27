package com.runcriticon.planificacion.infrastructure.rest.mappers

import com.runcriticon.planificacion.domain.PlanificacionError
import com.runcriticon.shared.api.rest.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/** Mapea [PlanificacionError] a respuesta HTTP estructurada. Mismo criterio que `ErrorMapper` de `club_taxonomia`. */
fun PlanificacionError.toErrorResponse(): ResponseEntity<ErrorResponse> =
    when (this) {
        PlanificacionError.Forbidden -> forbidden("FORBIDDEN", "Acceso denegado")
        is PlanificacionError.InvalidInput -> invalidInput(reason, field)
        PlanificacionError.SessionNotFound -> notFound("SESSION_NOT_FOUND", "La sesión no existe en el plan")
        PlanificacionError.DuplicateSessionDay ->
            conflict("DUPLICATE_SESSION_DAY", "dia", "Ya hay una sesión ese día")
        PlanificacionError.PlanAlreadyPublished ->
            conflict("PLAN_ALREADY_PUBLISHED", null, "El plan ya está publicado")
        PlanificacionError.NoSessions ->
            conflict("PLAN_WITHOUT_SESSIONS", null, "El plan no tiene ninguna sesión")
        // 503, no 409/500: es indisponibilidad temporal de la proyección, no un conflicto del cliente ni un
        // fallo del servidor — reintentar en unos segundos suele resolverlo.
        is PlanificacionError.ProjectionStale ->
            serviceUnavailable(
                "PROJECTION_STALE",
                "La membresía del grupo está desactualizada; inténtalo de nuevo en unos segundos",
            )
        // LAL-26: no hay personalización de ese alumno en esa sesión (RemovePersonalizationCommand).
        PlanificacionError.PersonalizationNotFound ->
            notFound("PERSONALIZATION_NOT_FOUND", "El alumno no tiene personalización en esa sesión")
        // LAL-26 AC2/AC3: el alumno no pertenece al grupo (BORRADOR) o no está en el snapshot (PUBLICADO).
        PlanificacionError.StudentNotInPlan ->
            conflict("STUDENT_NOT_IN_PLAN", "alumnoId", "El alumno no pertenece a este plan")
    }

private fun forbidden(
    code: String,
    message: String,
): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse(code = code, field = null, message = message))

private fun notFound(
    code: String,
    message: String,
): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(code = code, field = null, message = message))

private fun conflict(
    code: String,
    field: String?,
    message: String,
): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(code = code, field = field, message = message))

private fun serviceUnavailable(
    code: String,
    message: String,
): ResponseEntity<ErrorResponse> =
    ResponseEntity
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ErrorResponse(code = code, field = null, message = message))

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
