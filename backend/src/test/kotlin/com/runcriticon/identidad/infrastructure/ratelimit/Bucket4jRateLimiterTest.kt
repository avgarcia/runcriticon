package com.runcriticon.identidad.infrastructure.ratelimit

import com.runcriticon.identidad.application.ratelimit.RateLimitDecision
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision.Allowed
import com.runcriticon.identidad.application.ratelimit.RateLimitScope.MAGIC_LINK_ACCOUNT
import io.github.bucket4j.TimeMeter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Tests de tiempo controlado de los límites de ventana fija (ADR-0003 D12): agotar la banda por hora
 * del magic link (3/h por cuenta), comprobar que se recarga al avanzar el reloj y que cada clave
 * tiene su propio bucket. Usa un [TimeMeter] falso para no depender del reloj real.
 */
class Bucket4jRateLimiterTest :
    FunSpec({
        val hourNanos = 3_600_000_000_000L

        test("magic link por cuenta: admite 3/h y rechaza la 4a") {
            val time = FakeTimeMeter()
            val limiter = Bucket4jRateLimiter(RateLimitProperties(), time)

            repeat(3) {
                limiter.tryConsume(MAGIC_LINK_ACCOUNT, "ana@club.local") shouldBe Allowed
            }
            limiter
                .tryConsume(MAGIC_LINK_ACCOUNT, "ana@club.local")
                .shouldBeInstanceOf<RateLimitDecision.Limited>()
        }

        test("magic link por cuenta: la banda por hora se recarga al pasar una hora") {
            val time = FakeTimeMeter()
            val limiter = Bucket4jRateLimiter(RateLimitProperties(), time)

            repeat(3) { limiter.tryConsume(MAGIC_LINK_ACCOUNT, "ana@club.local") }
            limiter
                .tryConsume(MAGIC_LINK_ACCOUNT, "ana@club.local")
                .shouldBeInstanceOf<RateLimitDecision.Limited>()

            time.nanos += hourNanos
            limiter.tryConsume(MAGIC_LINK_ACCOUNT, "ana@club.local") shouldBe Allowed
        }

        test("cada clave tiene su propio bucket") {
            val time = FakeTimeMeter()
            val limiter = Bucket4jRateLimiter(RateLimitProperties(), time)

            repeat(3) { limiter.tryConsume(MAGIC_LINK_ACCOUNT, "ana@club.local") }
            limiter
                .tryConsume(MAGIC_LINK_ACCOUNT, "ana@club.local")
                .shouldBeInstanceOf<RateLimitDecision.Limited>()
            // Otra cuenta no está afectada.
            limiter.tryConsume(MAGIC_LINK_ACCOUNT, "otra@club.local") shouldBe Allowed
        }
    })

/** [TimeMeter] falso: el tiempo solo avanza cuando el test mueve [nanos]. */
private class FakeTimeMeter(
    var nanos: Long = 0,
) : TimeMeter {
    override fun currentTimeNanos(): Long = nanos

    override fun isWallClockBased(): Boolean = true
}
