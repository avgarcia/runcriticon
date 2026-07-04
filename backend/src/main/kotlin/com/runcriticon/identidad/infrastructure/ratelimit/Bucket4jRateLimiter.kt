package com.runcriticon.identidad.infrastructure.ratelimit

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.runcriticon.identidad.application.ratelimit.RateLimitDecision
import com.runcriticon.identidad.application.ratelimit.RateLimitScope
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.TimeMeter
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Adaptador de [RateLimiter] con bucket4j (token-bucket) y contadores **en memoria** (ADR-0003 D12,
 * MVP mono-instancia). Los buckets viven en una caché Caffeine con evicción por inactividad — al
 * pasar a >1 instancia (ADR-0006) se sustituye este adaptador por uno con `ProxyManager` Redis, sin
 * tocar los casos de uso (el puerto no cambia).
 *
 * NO es `@ApplicationService`: es infraestructura transversal de protección, no un caso de uso; no
 * consulta la matriz de autorización.
 */
@Component
class Bucket4jRateLimiter(
    private val props: RateLimitProperties,
    private val timeMeter: TimeMeter,
) : RateLimiter {
    // Ventana máxima configurada = 1 día; +1h de margen antes de evictar un bucket ocioso.
    private val buckets: Cache<String, Bucket> =
        Caffeine
            .newBuilder()
            .maximumSize(MAX_BUCKETS)
            .expireAfterAccess(Duration.ofDays(1).plusHours(1))
            .build()

    override fun tryConsume(
        scope: RateLimitScope,
        key: String,
    ): RateLimitDecision {
        val bucket = buckets.get("$scope:$key") { newBucket(scope) }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        return if (probe.isConsumed) {
            RateLimitDecision.Allowed
        } else {
            RateLimitDecision.Limited(Duration.ofNanos(probe.nanosToWaitForRefill))
        }
    }

    private fun newBucket(scope: RateLimitScope): Bucket {
        val builder = Bucket.builder().withCustomTimePrecision(timeMeter)
        bandwidthsFor(scope).forEach { builder.addLimit(it) }
        return builder.build()
    }

    private fun bandwidthsFor(scope: RateLimitScope): List<Bandwidth> =
        when (scope) {
            RateLimitScope.MAGIC_LINK_ACCOUNT ->
                listOf(hourly(props.magicLink.accountHourly), daily(props.magicLink.accountDaily))
            RateLimitScope.MAGIC_LINK_IP ->
                listOf(hourly(props.magicLink.ipHourly), daily(props.magicLink.ipDaily))
            RateLimitScope.PASSWORD_RESET_ACCOUNT ->
                listOf(hourly(props.passwordReset.accountHourly), daily(props.passwordReset.accountDaily))
            RateLimitScope.PASSWORD_RESET_IP ->
                listOf(hourly(props.passwordReset.ipHourly), daily(props.passwordReset.ipDaily))
            RateLimitScope.INVITATION_ACTOR ->
                listOf(hourly(props.invitationPerActorHourly))
        }

    private fun hourly(capacity: Long): Bandwidth =
        Bandwidth
            .builder()
            .capacity(capacity)
            .refillGreedy(capacity, Duration.ofHours(1))
            .build()

    private fun daily(capacity: Long): Bandwidth =
        Bandwidth
            .builder()
            .capacity(capacity)
            .refillGreedy(capacity, Duration.ofDays(1))
            .build()

    private companion object {
        const val MAX_BUCKETS = 100_000L
    }
}
