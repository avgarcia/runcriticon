package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.identidad.application.ports.PasswordHasher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Implementación del puerto [PasswordHasher] sobre el [PasswordEncoder] de Spring Security
 * (Argon2id, ADR-0003 D13), configurado en [SecurityConfig].
 */
@Component
class Argon2PasswordHasher(
    private val encoder: PasswordEncoder,
) : PasswordHasher {
    override fun matches(
        raw: CharSequence,
        hash: String,
    ): Boolean = encoder.matches(raw, hash)

    override fun encode(raw: CharSequence): String = requireNotNull(encoder.encode(raw))
}
