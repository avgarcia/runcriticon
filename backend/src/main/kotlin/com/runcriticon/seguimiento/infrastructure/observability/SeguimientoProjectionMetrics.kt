package com.runcriticon.seguimiento.infrastructure.observability

import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Catálogo de métricas de las proyecciones locales de este módulo. Hoy una sola: el retraso de
 * `plan_resuelto_por_alumno`. Tags controlados `module`/`projection`, cardinalidad fija — nada de `alumno_id`.
 *
 * El `Gauge` se retiene en un campo: Micrometer solo guarda una referencia débil al objeto medido, y sin esto
 * el recolector podría llevárselo y la métrica pasaría a publicar `NaN`.
 */
@Component
class SeguimientoProjectionMetrics(
    registry: MeterRegistry,
    resolvedPlanProjection: ResolvedPlanProjection,
) {
    private val resolvedPlanLagSeconds: Gauge =
        Gauge
            .builder("seguimiento.projection_lag_seconds") { resolvedPlanProjection.lagSeconds().toDouble() }
            .description("Retraso en segundos de la proyección local de planes resueltos por alumno")
            .tag("module", "seguimiento")
            .tag("projection", "plan_resuelto_por_alumno")
            .register(registry)

    /** Valor actual del gauge, para verificarlo en tests sin pasar por el scrape de Prometheus. */
    fun resolvedPlanLagSeconds(): Double = resolvedPlanLagSeconds.value()
}
