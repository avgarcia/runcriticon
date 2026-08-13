package com.runcriticon.planificacion.domain

/**
 * Personalización de una sesión para un alumno concreto (ADR-0002 D9). Ciudadano de primera del agregado desde
 * el día 1, sin caso de uso que la construya todavía (LAL-26) — no se añade después.
 *
 * [override] guarda el JSON del ajuste tal cual llega; su shape estructurado no está definido hasta LAL-26, así
 * que se mantiene como texto en vez de inventar un modelo que probablemente no sobreviva a esa historia.
 */
data class Personalization(
    val id: PersonalizationId,
    val sessionId: SessionId,
    val studentId: PersonId,
    val override: String = "{}",
    val messageToStudent: String? = null,
)
