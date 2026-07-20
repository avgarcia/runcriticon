package com.runcriticon.identidad.infrastructure.rest.mappers

import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.infrastructure.rest.ErrorResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * Mapea [IdentidadError] a respuesta HTTP estructurada (ADR-0012 D19, ADR-0009 D12).
 * El 403 usa cuerpo neutro para no revelar el motivo de la denegación.
 */
fun IdentidadError.toErrorResponse(): ResponseEntity<ErrorResponse> =
    when (this) {
        IdentidadError.Forbidden ->
            ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ErrorResponse(code = "FORBIDDEN", field = null, message = "Acceso denegado"),
            )

        IdentidadError.NotFound ->
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse(code = "NOT_FOUND", field = null, message = "Recurso no encontrado"),
            )

        is IdentidadError.Conflict ->
            ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponse(code = "CONFLICT", field = null, message = reason),
            )

        is IdentidadError.InvalidInput ->
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponse(code = "INVALID_INPUT", field = field, message = reason),
            )

        is IdentidadError.RateLimited ->
            ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString())
                .body(
                    ErrorResponse(
                        code = "RATE_LIMITED",
                        field = null,
                        message = "Has superado el límite de peticiones; inténtalo de nuevo más tarde.",
                    ),
                )

        IdentidadError.InvalidCredentials, IdentidadError.AccountNotActive ->
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponse(code = "UNAUTHORIZED", field = null, message = "No autenticado"),
            )
    }
