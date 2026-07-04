package com.runcriticon.identidad.application.usecases

import com.runcriticon.identidad.application.ratelimit.ProgressiveThrottle
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision
import com.runcriticon.identidad.application.ratelimit.RateLimitScope
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.application.ratelimit.ThrottleProfile
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration

/**
 * Neutraliza el rate-limiting (ADR-0003 D12) en los tests de integración que **no** lo ejercitan. Sin
 * esto, el estado en memoria del limiter (singleton del contexto) se acumularía entre métodos de test
 * y el cooldown por email rompería flujos legítimos (p. ej. dos peticiones de magic link al mismo
 * email en tests distintos). Los tests que sí verifican el rate-limiting usan el adaptador real.
 */
@TestConfiguration
class UnlimitedRateLimitConfig {
    @Bean
    @Primary
    fun unlimitedRateLimiter(): RateLimiter =
        object : RateLimiter {
            override fun tryConsume(
                scope: RateLimitScope,
                key: String,
            ): RateLimitDecision = RateLimitDecision.Allowed
        }

    @Bean
    @Primary
    fun noProgressiveThrottle(): ProgressiveThrottle =
        object : ProgressiveThrottle {
            override fun check(
                profile: ThrottleProfile,
                key: String,
            ): Duration? = null

            override fun penalize(
                profile: ThrottleProfile,
                key: String,
            ) = Unit

            override fun reset(
                profile: ThrottleProfile,
                key: String,
            ) = Unit
        }
}
