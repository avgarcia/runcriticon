package com.runcriticon.identidad.infrastructure.ratelimit

import com.runcriticon.identidad.application.ratelimit.ThrottleProfile
import com.runcriticon.testing.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

/**
 * Tests de tiempo controlado del backoff progresivo (ADR-0003 D12): verifican la escalera de login
 * (1s, 5s, 15s, 60s), el desbloqueo al pasar la ventana, el reset y el cooldown de email — todo con
 * un reloj mutable, sin esperas reales.
 */
class CaffeineProgressiveThrottleTest :
    FunSpec({
        val clock = MutableClock(Instant.parse("2026-07-04T10:00:00Z"))
        val throttle = CaffeineProgressiveThrottle(RateLimitProperties(), clock)

        test("login: sin eventos previos no hay espera") {
            throttle.check(ThrottleProfile.LOGIN, "ip:a").shouldBeNull()
        }

        test("login: escalera 1s, 5s, 15s, 60s y desbloqueo al cumplir la ventana") {
            val key = "ip:escalera"
            val steps = listOf(1L, 5L, 15L, 60L)
            steps.forEach { seconds ->
                throttle.penalize(ThrottleProfile.LOGIN, key)
                // Justo tras el fallo, la espera es la del peldaño actual.
                throttle.check(ThrottleProfile.LOGIN, key).shouldNotBeNull() shouldBe Duration.ofSeconds(seconds)
                // Un segundo antes de cumplirse sigue bloqueado; al cumplirse, se libera.
                clock.instant = clock.instant.plusSeconds(seconds - 1)
                throttle.check(ThrottleProfile.LOGIN, key).shouldNotBeNull()
                clock.instant = clock.instant.plusSeconds(1)
                throttle.check(ThrottleProfile.LOGIN, key).shouldBeNull()
            }
        }

        test("login: el reset limpia el backoff") {
            val key = "ip:reset"
            throttle.penalize(ThrottleProfile.LOGIN, key)
            throttle.check(ThrottleProfile.LOGIN, key).shouldNotBeNull()
            throttle.reset(ThrottleProfile.LOGIN, key)
            throttle.check(ThrottleProfile.LOGIN, key).shouldBeNull()
        }

        test("cooldown de email: 30s tras la 1a petición, 2min tras la 2a") {
            val key = "magiclink:ana@club.local"
            throttle.penalize(ThrottleProfile.EMAIL_COOLDOWN, key)
            throttle.check(ThrottleProfile.EMAIL_COOLDOWN, key).shouldNotBeNull() shouldBe Duration.ofSeconds(30)

            clock.instant = clock.instant.plusSeconds(30)
            throttle.check(ThrottleProfile.EMAIL_COOLDOWN, key).shouldBeNull()

            throttle.penalize(ThrottleProfile.EMAIL_COOLDOWN, key)
            throttle.check(ThrottleProfile.EMAIL_COOLDOWN, key).shouldNotBeNull() shouldBe Duration.ofMinutes(2)
        }
    })
