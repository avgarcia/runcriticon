package com.runcriticon.identidad.application

/**
 * Puerto de verificación de contraseñas. La implementación usa Argon2id (ADR-0003 D13) en
 * infraestructura; el dominio/aplicación no conoce el algoritmo concreto.
 */
interface HashDePassword {
    fun coincide(
        raw: CharSequence,
        hash: String,
    ): Boolean
}
