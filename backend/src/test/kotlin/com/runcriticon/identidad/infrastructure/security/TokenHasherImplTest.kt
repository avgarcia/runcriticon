package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.identidad.domain.invitation.RawToken
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test

/**
 * Test unitario del adaptador [TokenHasherImpl] (ADR-0003 D13). Cubre el fail-fast del secreto
 * (un despliegue mal configurado debe fallar al construir el bean, no en la primera invitación con
 * un "Empty key" opaco) y el determinismo del HMAC-SHA256.
 */
class TokenHasherImplTest {
    @Test
    fun `un secreto en blanco hace fallar la construccion del bean con mensaje claro`() {
        val ex = shouldThrow<IllegalArgumentException> { TokenHasherImpl("") }
        ex.message shouldContain "token-hmac-secret"

        // Solo espacios también cuenta como "en blanco": el fail-fast usa isNotBlank, no isNotEmpty.
        shouldThrow<IllegalArgumentException> { TokenHasherImpl("   ") }
    }

    @Test
    fun `hash con secreto valido es determinista y produce 64 caracteres hex`() {
        val hasher = TokenHasherImpl(SECRET)
        val token = RawToken("token-de-prueba-256-bits")

        hasher.hash(token).value shouldBe hasher.hash(token).value
        hasher.hash(token).value shouldMatch "[0-9a-f]{64}"
    }

    @Test
    fun `tokens distintos producen hashes distintos`() {
        val hasher = TokenHasherImpl(SECRET)

        hasher.hash(RawToken("token-a")).value shouldNotBe hasher.hash(RawToken("token-b")).value
    }

    private companion object {
        const val SECRET = "test-hmac-secret-not-prod"
    }
}
