package com.runcriticon.identidad.application.ports.outbound.security

/**
 * Puerto de verificación de contraseñas hasheadas con Argon2id.
 */
interface PasswordHasher {
    fun matches(
        raw: CharSequence,
        hash: String,
    ): Boolean

    /** Hashea una contraseña nueva con Argon2id para persistirla en la activación/reseteo. */
    fun encode(raw: CharSequence): String

    /**
     * Indica si [hash] se generó con parámetros más débiles que los vigentes y debe re-hashearse
     * (upgrade-on-login, LAL-58). Solo inspecciona los parámetros codificados en el propio hash.
     */
    fun needsRehash(hash: String): Boolean
}
