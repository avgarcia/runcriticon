package com.runcriticon.identidad.infrastructure.ratelimit

import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Adaptador de [RateLimitMetrics] (ADR-0011). Expone el counter `identidad.ratelimit.blocked` con
 * tags controlados `module`, `action` y `dimension`. Micrometer cachea el meter por nombre+tags, así
 * que registrar con tags variables de baja cardinalidad es seguro.
 */
@Component
class IdentidadRateLimitMetrics(
    private val registry: MeterRegistry,
) : RateLimitMetrics {
    override fun blocked(
        action: String,
        dimension: String,
    ) {
        registry
            .counter("identidad.ratelimit.blocked", "module", "identidad", "action", action, "dimension", dimension)
            .increment()
    }
}
