package com.runcriticon.shared.config

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/**
 * Test unitario de [LocalProfileGuard] (ADR-0013 D13): el perfil local nunca debe alcanzar
 * SSM/Secrets Manager reales. Cubre el fail-fast al arrancar con credenciales AWS reales presentes.
 */
class LocalProfileGuardTest {
    @Test
    fun `sin credenciales AWS arranca sin lanzar`() {
        val guard = LocalProfileGuard(MockEnvironment())

        shouldNotThrowAny { guard.verify() }
    }

    @Test
    fun `con un valor fake corto arranca sin lanzar`() {
        val env = MockEnvironment().withProperty("AWS_ACCESS_KEY_ID", "fake")
        val guard = LocalProfileGuard(env)

        shouldNotThrowAny { guard.verify() }
    }

    @Test
    fun `access key real (mas de 16 chars) hace fallar el arranque`() {
        val env = MockEnvironment().withProperty("AWS_ACCESS_KEY_ID", "AKIAABCDEFGHIJKLMNOP")
        val guard = LocalProfileGuard(env)

        val ex = shouldThrow<IllegalStateException> { guard.verify() }
        ex.message shouldContain "Credenciales AWS reales detectadas"
    }

    @Test
    fun `session token real hace fallar el arranque`() {
        val env = MockEnvironment().withProperty("AWS_SESSION_TOKEN", "x".repeat(200))
        val guard = LocalProfileGuard(env)

        shouldThrow<IllegalStateException> { guard.verify() }
    }
}
