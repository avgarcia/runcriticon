package com.runcriticon.identidad.application.ratelimit

import java.time.Duration

/**
 * Throttling **progresivo**: retardo creciente en función de cuántas veces seguidas ha ocurrido un evento, en lugar de
 * un bloqueo duro. Se usa para dos casos:
 *
 *  - [ThrottleProfile.LOGIN]: retardo tras fallos de login (1s, 5s, 15s, 60s…). Evita el brute force sin permitir que
 *    un atacante bloquee a la víctima (no hay bloqueo de cuenta).
 *  - [ThrottleProfile.EMAIL_COOLDOWN]: intervalo mínimo creciente entre peticiones del mismo email (30s → 2min → 5min),
 *    independiente de los límites de ventana fija del [RateLimiter].
 *
 * Abstracción migrable igual que [RateLimiter]: el adaptador cuenta en memoria (Caffeine + reloj inyectable); a >1
 * instancia pasa a Redis sin tocar los llamadores.
 */
interface ProgressiveThrottle {
    /** Retardo pendiente si la clave está en periodo de espera; `null` si puede proceder ya. */
    fun check(
        profile: ThrottleProfile,
        key: String,
    ): Duration?

    /** Avanza un paso el backoff de la clave (fallo de login o petición de email consumida). */
    fun penalize(
        profile: ThrottleProfile,
        key: String,
    )

    /** Reinicia el backoff de la clave (login correcto). */
    fun reset(
        profile: ThrottleProfile,
        key: String,
    )
}

/**
 * Perfil de backoff. Fija la escalera de esperas (índice = nº de eventos previos); el paso 0 nunca espera. Los valores
 * concretos viven en la configuración.
 */
enum class ThrottleProfile {
    LOGIN,
    EMAIL_COOLDOWN,
}
