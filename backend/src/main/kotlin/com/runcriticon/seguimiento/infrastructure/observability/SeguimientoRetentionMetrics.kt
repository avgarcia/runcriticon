package com.runcriticon.seguimiento.infrastructure.observability

import com.runcriticon.seguimiento.application.ports.outbound.observability.RetentionMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [RetentionMetrics]. Expone `seguimiento.retention_purge.rows_deleted`, tags
 * `module` (fijo) y `table` (`evento_procesado`) — cardinalidad fija, sin ids.
 */
@Component
class SeguimientoRetentionMetrics(
    private val registry: MeterRegistry,
) : RetentionMetrics {
    override fun purged(
        table: String,
        rows: Int,
    ) {
        Counter
            .builder("seguimiento.retention_purge.rows_deleted")
            .description("Filas borradas por el job de retención de seguimiento (ADR-0017)")
            .tag("module", "seguimiento")
            .tag("table", table)
            .register(registry)
            .increment(rows.toDouble())
    }
}
