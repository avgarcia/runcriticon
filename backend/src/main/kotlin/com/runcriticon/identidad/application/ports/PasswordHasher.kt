package com.runcriticon.identidad.application.ports

/**
 * Puerto de verificación de contraseñas hasheadas con Argon2id (ADR-0003 D13).
 */
interface PasswordHasher {
    fun matches(
        raw: CharSequence,
        hash: String,
    ): Boolean

    /** Hashea una contraseña nueva con Argon2id (ADR-0003 D13) para persistirla en la activación/reseteo. */
    fun encode(raw: CharSequence): String
}
