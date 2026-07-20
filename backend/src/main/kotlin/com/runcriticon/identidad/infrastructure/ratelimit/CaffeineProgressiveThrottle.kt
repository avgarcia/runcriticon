package com.runcriticon.identidad.infrastructure.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.runcriticon.identidad.application.ratelimit.ProgressiveThrottle
import com.runcriticon.identidad.application.ratelimit.ThrottleProfile
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Adaptador de [ProgressiveThrottle] con estado **en memoria** (Caffeine + reloj inyectable). Guarda por clave el paso
 * de backoff y el instante del último evento; el tiempo de espera se lee de la escalera configurada
 * ([RateLimitProperties.login] / [RateLimitProperties.emailCooldown]).
 *
 * El reloj es [Clock] inyectado: en producción `Clock.systemUTC()`, en tests un reloj mutable que verifica el backoff
 * sin esperas reales. Migrable a Redis a >1 instancia igual que [Bucket4jRateLimiter]. No es un caso de uso.
 */
@Component
class CaffeineProgressiveThrottle(
    private val props: RateLimitProperties,
    private val clock: Clock,
) : ProgressiveThrottle {
    private data class Attempt(
        val step: Int,
        val lastAt: Instant,
    )

    private val store: Cache<String, Attempt> =
        Caffeine
            .newBuilder()
            .maximumSize(MAX_KEYS)
            .expireAfterAccess(Duration.ofHours(2))
            .build()

    override fun check(
        profile: ThrottleProfile,
        key: String,
    ): Duration? {
        val attempt = store.getIfPresent(compositeKey(profile, key)) ?: return null
        val required = waitFor(profile, attempt.step)
        val elapsed = Duration.between(attempt.lastAt, clock.instant())
        val remaining = required.minus(elapsed)
        return if (remaining.isNegative || remaining.isZero) null else remaining
    }

    override fun penalize(
        profile: ThrottleProfile,
        key: String,
    ) {
        store.asMap().compute(compositeKey(profile, key)) { _, current ->
            Attempt(step = (current?.step ?: 0) + 1, lastAt = clock.instant())
        }
    }

    override fun reset(
        profile: ThrottleProfile,
        key: String,
    ) {
        store.invalidate(compositeKey(profile, key))
    }

    private fun waitFor(
        profile: ThrottleProfile,
        step: Int,
    ): Duration {
        val ladder =
            when (profile) {
                ThrottleProfile.LOGIN -> props.login
                ThrottleProfile.EMAIL_COOLDOWN -> props.emailCooldown
            }
        return when {
            step <= 0 || ladder.isEmpty() -> Duration.ZERO
            else -> ladder[(step - 1).coerceAtMost(ladder.size - 1)]
        }
    }

    private fun compositeKey(
        profile: ThrottleProfile,
        key: String,
    ): String = "$profile:$key"

    private companion object {
        const val MAX_KEYS = 100_000L
    }
}
