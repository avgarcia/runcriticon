package com.runcriticon.shared.autorizacion.spring

import java.io.Serializable
import java.time.Instant

/**
 * Detalles de la autenticación que viajan en `Authentication.details` dentro del `SecurityContext`
 * que Spring Session JDBC persiste en Postgres (de ahí [Serializable], mismo motivo que `Principal`).
 *
 * [authenticatedAt] es el instante de la última autenticación: [AbsoluteSessionTimeoutFilter] lo
 * contrasta con el tope absoluto de 90 días (ADR-0003 D10, LAL-57). Va aquí y no como atributo de
 * sesión porque nadie fuera del contexto de seguridad usa `HttpSession` (lo verifica
 * `AuthorizationArchTest`).
 */
data class SessionAuthenticationDetails(
    val authenticatedAt: Instant,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
