package com.runcriticon.identidad.infrastructure.seguridad

import com.runcriticon.identidad.application.ports.HashDePassword
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Implementación del puerto [HashDePassword] sobre el [PasswordEncoder] de Spring Security
 * (Argon2id, ADR-0003 D13), configurado en [SeguridadConfig].
 */
@Component
class CodificadorArgon2(
    private val encoder: PasswordEncoder,
) : HashDePassword {
    override fun coincide(
        raw: CharSequence,
        hash: String,
    ): Boolean = encoder.matches(raw, hash)
}
