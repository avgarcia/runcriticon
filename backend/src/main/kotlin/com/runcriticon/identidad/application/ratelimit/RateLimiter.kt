package com.runcriticon.identidad.application.ratelimit

import java.time.Duration

/**
 * Puerto de rate-limiting de ventana fija. Abstracción migrable: en MVP el adaptador cuenta en memoria (bucket4j +
 * Caffeine); al activar >1 instancia se sustituye por un backend Redis **sin tocar los casos de uso**. Cubre las
 * "cifras concretas" (p. ej. magic * link 3/h·10/día por cuenta, 20/h·100/día por IP).
 *
 * El [scope] fija las bandas (de la configuración); la [key] es la identidad lógica dentro del scope (email
 * normalizado, IP, id del actor). El adaptador nunca persiste la clave: vive en memoria.
 */
interface RateLimiter {
    fun tryConsume(
        scope: RateLimitScope,
        key: String,
    ): RateLimitDecision
}

/**
 * Resultado de consultar el [RateLimiter]. [Allowed] cuando quedaba cupo; [Limited] cuando la banda está agotada, con
 * el tiempo estimado hasta que vuelva a haber cupo (para `Retry-After`).
 */
sealed interface RateLimitDecision {
    data object Allowed : RateLimitDecision

    data class Limited(
        val retryAfter: Duration,
    ) : RateLimitDecision
}
