package com.runcriticon.identidad.application.ratelimit

/**
 * Puerto de métricas de rate-limiting. El caso de uso lo invoca al rechazar una petición por límite alcanzado. Tags
 * controlados (baja cardinalidad): [action] ∈ {login, magic_link, reseteo, invitacion}; [dimension] ∈ {ip, cuenta,
 * actor, cooldown}.
 * Nunca se pasan `user_id` ni emails como tag.
 */
interface RateLimitMetrics {
    fun blocked(
        action: String,
        dimension: String,
    )
}
