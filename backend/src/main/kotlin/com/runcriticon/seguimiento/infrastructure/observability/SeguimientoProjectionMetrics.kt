package com.runcriticon.seguimiento.infrastructure.observability

import com.runcriticon.seguimiento.application.ports.outbound.observability.SeguimientoMetrics
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ResolvedPlanProjection
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.ReportStatus
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Catálogo de métricas de este módulo: el retraso de la proyección `plan_resuelto_por_alumno` y, desde LAL-30,
 * el contador `seguimiento.reportes_total` (implementación del puerto [SeguimientoMetrics]). Tags controlados
 * `module`/`projection`/`estado` — cardinalidad fija (`estado` son los 3 valores de [ReportStatus]), nada de
 * `alumno_id`.
 *
 * El `Gauge` se retiene en un campo: Micrometer solo guarda una referencia débil al objeto medido, y sin esto
 * el recolector podría llevárselo y la métrica pasaría a publicar `NaN`. Los `Counter` por estado se registran
 * una vez en el constructor por el mismo motivo, en vez de en cada llamada a `reportRegistered`.
 */
@Component
class SeguimientoProjectionMetrics(
    private val registry: MeterRegistry,
    resolvedPlanProjection: ResolvedPlanProjection,
) : SeguimientoMetrics {
    private val resolvedPlanLagSeconds: Gauge =
        Gauge
            .builder("seguimiento.projection_lag_seconds") { resolvedPlanProjection.lagSeconds().toDouble() }
            .description("Retraso en segundos de la proyección local de planes resueltos por alumno")
            .tag("module", "seguimiento")
            .tag("projection", "plan_resuelto_por_alumno")
            .register(registry)

    private val reportesTotal: Map<ReportStatus, Counter> =
        ReportStatus.entries.associateWith { status ->
            Counter
                .builder("seguimiento.reportes_total")
                .description("Reportes de sesión registrados por el alumno, por estado")
                .tag("module", "seguimiento")
                .tag("estado", status.name)
                .register(registry)
        }

    private val reajustesTotal: Map<AdjustmentAction, Counter> =
        AdjustmentAction.entries.associateWith { action ->
            Counter
                .builder("seguimiento.reajustes_total")
                .description("Reajustes de día aplicados por el alumno, por acción (LAL-33)")
                .tag("module", "seguimiento")
                .tag("accion", action.name)
                .register(registry)
        }

    /** Valor actual del gauge, para verificarlo en tests sin pasar por el scrape de Prometheus. */
    fun resolvedPlanLagSeconds(): Double = resolvedPlanLagSeconds.value()

    override fun reportRegistered(status: ReportStatus) {
        reportesTotal.getValue(status).increment()
    }

    override fun reportRejected(reason: String) {
        registry
            .counter("seguimiento.reportes_rechazados_total", "module", "seguimiento", "motivo", reason)
            .increment()
    }

    override fun dayRescheduled(action: AdjustmentAction) {
        reajustesTotal.getValue(action).increment()
    }
}
