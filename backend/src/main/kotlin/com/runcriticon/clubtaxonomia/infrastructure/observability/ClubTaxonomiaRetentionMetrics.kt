package com.runcriticon.clubtaxonomia.infrastructure.observability

import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.RetentionMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [RetentionMetrics]. Expone `club_taxonomia.retention_purge.rows_deleted`, tags
 * `module` (fijo) y `table` (`persona_eliminada`/`evento_procesado`) — cardinalidad fija (2), sin ids.
 */
@Component
class ClubTaxonomiaRetentionMetrics(
    private val registry: MeterRegistry,
) : RetentionMetrics {
    override fun purged(
        table: String,
        rows: Int,
    ) {
        Counter
            .builder("club_taxonomia.retention_purge.rows_deleted")
            .description("Filas borradas por el job de retención de club_taxonomia (ADR-0017)")
            .tag("module", "club_taxonomia")
            .tag("table", table)
            .register(registry)
            .increment(rows.toDouble())
    }
}
