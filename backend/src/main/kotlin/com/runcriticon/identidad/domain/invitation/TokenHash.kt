package com.runcriticon.identidad.domain.invitation

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Hash del token de invitación (SHA-256 + HMAC, ADR-0003 D13). Es el único representante del token
 * que el dominio y la base de datos conocen; el texto claro ([RawToken]) nunca se persiste.
 *
 * La comparación se hace **siempre** vía [matches] (tiempo constante con [MessageDigest.isEqual]);
 * nunca con `==`/`equals`, que cortocircuita y filtra información por timing (ADR-0003 D13).
 */
@JvmInline
value class TokenHash(
    val value: String,
) {
    fun matches(other: TokenHash): Boolean =
        MessageDigest.isEqual(
            value.toByteArray(StandardCharsets.UTF_8),
            other.value.toByteArray(StandardCharsets.UTF_8),
        )
}
