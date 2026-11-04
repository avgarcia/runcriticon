package com.runcriticon.planificacion.infrastructure.observability

import com.runcriticon.planificacion.application.ports.outbound.observability.RetentionMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [RetentionMetrics]. Expone `planificacion.retention_purge.rows_deleted`, tags
 * `module` (fijo) y `table` (`evento_procesado`) — cardinalidad fija, sin ids.
 */
@Component
class PlanificacionRetentionMetrics(
    private val registry: MeterRegistry,
) : RetentionMetrics {
    override fun purged(
        table: String,
        rows: Int,
    ) {
        Counter
            .builder("planificacion.retention_purge.rows_deleted")
            .description("Filas borradas por el job de retención de planificacion (ADR-0017)")
            .tag("module", "planificacion")
            .tag("table", table)
            .register(registry)
            .increment(rows.toDouble())
    }
}
