package com.runcriticon.seguimiento.domain

import java.time.LocalDate

/**
 * La sesión de un día, ya resuelta para un alumno concreto — una fila de `plan_resuelto_por_alumno`.
 *
 * [messageToStudent] y [isPersonalized] existen en el esquema desde el día 1 (LAL-29) pero esta historia nunca
 * los rellena: no hay evento de personalización todavía (llega con LAL-26). [isPersonalized] es "uso interno,
 * NO se muestra al alumno" (docs/plan-implementacion-mvp.md) — el mapeador REST no debe traducir esta clase
 * directamente al DTO de respuesta sin excluirlo explícitamente.
 */
data class ResolvedSession(
    val day: LocalDate,
    val type: SessionType,
    val volume: SessionVolume? = null,
    val pace: ResolvedPace? = null,
    val notes: String? = null,
    val messageToStudent: String? = null,
    val isPersonalized: Boolean = false,
)
