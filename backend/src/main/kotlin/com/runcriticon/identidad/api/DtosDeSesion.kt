package com.runcriticon.identidad.api

/** Credenciales de login (ADR-0003 D5). */
data class CredencialesRequest(
    val email: String,
    val password: String,
)

/** Representación pública de la sesión: nunca incluye datos sensibles ni el hash. */
data class SesionResponse(
    val userId: String,
    val clubId: String,
    val rol: String,
)
