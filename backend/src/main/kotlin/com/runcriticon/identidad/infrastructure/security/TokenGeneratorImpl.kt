package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.identidad.application.ports.TokenGenerator
import com.runcriticon.identidad.domain.invitation.RawToken
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/**
 * Adaptador del puerto [TokenGenerator] (ADR-0003 D13): 32 bytes (256 bits) de [SecureRandom]
 * codificados en Base64 URL-safe sin padding. El texto claro solo viaja al email; nunca se persiste.
 */
@Component
class TokenGeneratorImpl : TokenGenerator {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    override fun generate(): RawToken {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return RawToken(encoder.encodeToString(bytes))
    }

    private companion object {
        const val TOKEN_BYTES = 32
    }
}
