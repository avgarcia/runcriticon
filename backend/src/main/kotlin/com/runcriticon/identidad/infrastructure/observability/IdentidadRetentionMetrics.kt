package com.runcriticon.identidad.infrastructure.observability

import com.runcriticon.identidad.application.ports.outbound.observability.RetentionMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [RetentionMetrics]. Expone `identidad.retention_purge.rows_deleted`, tags
 * `module` (fijo) y `table` (`evento_auditoria`) — cardinalidad fija, sin ids.
 */
@Component
class IdentidadRetentionMetrics(
    private val registry: MeterRegistry,
) : RetentionMetrics {
    override fun purged(
        table: String,
        rows: Int,
    ) {
        Counter
            .builder("identidad.retention_purge.rows_deleted")
            .description("Filas borradas por el job de retención de identidad (ADR-0017)")
            .tag("module", "identidad")
            .tag("table", table)
            .register(registry)
            .increment(rows.toDouble())
    }
}
