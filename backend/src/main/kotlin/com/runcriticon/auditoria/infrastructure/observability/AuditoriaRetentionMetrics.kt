package com.runcriticon.auditoria.infrastructure.observability

import com.runcriticon.auditoria.application.ports.outbound.observability.RetentionMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [RetentionMetrics]. Expone `auditoria.retention_purge.rows_deleted`, tags
 * `module` (fijo) y `table` (`evento`) — cardinalidad fija, sin ids.
 */
@Component
class AuditoriaRetentionMetrics(
    private val registry: MeterRegistry,
) : RetentionMetrics {
    override fun purged(
        table: String,
        rows: Int,
    ) {
        Counter
            .builder("auditoria.retention_purge.rows_deleted")
            .description("Filas borradas por el job de retención de auditoria (ADR-0017)")
            .tag("module", "auditoria")
            .tag("table", table)
            .register(registry)
            .increment(rows.toDouble())
    }
}
