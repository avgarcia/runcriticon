package com.runcriticon.identidad.infrastructure.rest

import com.fasterxml.jackson.annotation.JsonProperty

/** Cuerpo de la petición para dar de alta un entrenador (POST /api/entrenadores). */
data class InviteCoachRequest(
    @JsonProperty("nombre") val name: String,
    val email: String,
)

/** Respuesta al alta: devuelve el ID del usuario creado. */
data class InviteCoachResponse(
    val id: String,
)

/**
 * Error estructurado para respuestas 4xx (ADR-0012 D19).
 * El frontend traduce [code] a mensaje localizado; [field] solo aparece en errores de entrada.
 */
data class ErrorResponse(
    val code: String,
    val field: String?,
    val message: String,
)
