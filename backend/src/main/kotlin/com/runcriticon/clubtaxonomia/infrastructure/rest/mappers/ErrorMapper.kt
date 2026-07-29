package com.runcriticon.clubtaxonomia.infrastructure.rest.mappers

import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.shared.api.rest.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * Mapea [ClubTaxonomiaError] a respuesta HTTP estructurada. Los `code` son granulares —`TAG_KEY_NOT_FOUND` en vez de
 * un `NOT_FOUND` genérico— porque el editor de taxonomía necesita explicar cada caso con precisión; el frontend los
 * traduce por `code`, nunca muestra el `message` que viaja aquí.
 *
 * **El `reason` del dominio no se reenvía al `message`.** A diferencia de `identidad`, donde `reason` es prosa
 * castellana, aquí es un código estable en inglés (`"blank"`, `"too_long"`, `"tag_key_archived"`): filtrarlo al
 * usuario sería jerga. Este mapeador traduce.
 *
 * `reason` es `String` y no una sealed class, así que los `when` anidados necesitan `else`. Ese `else` **devuelve** el
 * código genérico y nunca lanza: un `reason` nuevo debe degradar a un error correcto, no tumbar la petición.
 */
fun ClubTaxonomiaError.toErrorResponse(): ResponseEntity<ErrorResponse> =
    when (this) {
        ClubTaxonomiaError.Forbidden ->
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse(code = "FORBIDDEN", field = null, message = "Acceso denegado"),
            )

        is ClubTaxonomiaError.InvalidInput -> invalidInput(reason, field)

        is ClubTaxonomiaError.DuplicateLabel ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(
                    code = "DUPLICATE_LABEL",
                    field = field,
                    message = "Ya existe un elemento con ese nombre",
                ),
            )

        ClubTaxonomiaError.TagKeyNotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "TAG_KEY_NOT_FOUND", field = null, message = "El eje no existe"),
            )

        ClubTaxonomiaError.TagValueNotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "TAG_VALUE_NOT_FOUND", field = null, message = "El valor no existe"),
            )

        is ClubTaxonomiaError.Conflict -> conflict(reason)
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
        "blank" ->
            badRequest("LABEL_BLANK", field, "El nombre no puede estar vacío")

        "too_long" ->
            badRequest("LABEL_TOO_LONG", field, "El nombre supera la longitud máxima")

        else ->
            badRequest("INVALID_INPUT", field, "Revisa los datos introducidos")
    }

/** Mismo criterio de `else` defensivo que [invalidInput]. */
private fun conflict(reason: String): ResponseEntity<ErrorResponse> =
    when (reason) {
        "tag_key_archived" ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(code = "TAG_KEY_ARCHIVED", field = null, message = "El eje está archivado"),
            )

        else ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(code = "CONFLICT", field = null, message = "La operación choca con el estado actual"),
            )
    }

private fun badRequest(
    code: String,
    field: String,
    message: String,
): ResponseEntity<ErrorResponse> =
    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
        ErrorResponse(code = code, field = field, message = message),
    )
