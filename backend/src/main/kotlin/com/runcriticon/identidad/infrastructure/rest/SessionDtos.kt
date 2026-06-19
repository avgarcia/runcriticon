package com.runcriticon.identidad.infrastructure.rest

/** Credenciales de login (ADR-0003 D5). */
data class CredentialsRequest(
    val email: String,
    val password: String,
)

/** Representación pública de la sesión: nunca incluye datos sensibles ni el hash. */
data class SessionResponse(
    val userId: String,
    val clubId: String,
    val role: String,
)
