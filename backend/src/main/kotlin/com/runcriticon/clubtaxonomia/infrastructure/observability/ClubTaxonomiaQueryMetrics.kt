package com.runcriticon.clubtaxonomia.infrastructure.observability

import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.GroupQueryMetrics
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Implementación Micrometer de [GroupQueryMetrics]. Expone `club_taxonomia.group_query.duration` (percentiles p50,
 * p95, p99 -- el umbral de LAL-95/ADR-0001 es p95 < 400 ms), tags `module` y `endpoint` con valores cerrados
 * (`resolve_members` / `list_summaries`); sin `group_id` ni ningún otro identificador de cardinalidad alta.
 *
 * Un `Timer` por endpoint, igual que [IdentidadBusinessMetrics][com.runcriticon.identidad.infrastructure.observability.IdentidadBusinessMetrics]
 * pre-crea un counter por rol: los valores de `endpoint` son un conjunto cerrado de dos, no hace falta el registro
 * dinámico de [io.micrometer.core.instrument.MeterRegistry.timer].
 */
@Component
class ClubTaxonomiaQueryMetrics(
    registry: MeterRegistry,
) : GroupQueryMetrics {
    private val resolveMembersTimer: Timer = timer(registry, "resolve_members")
    private val listSummariesTimer: Timer = timer(registry, "list_summaries")

    override fun resolveMembersRecorded(duration: Duration) {
        resolveMembersTimer.record(duration)
    }

    override fun listSummariesRecorded(duration: Duration) {
        listSummariesTimer.record(duration)
    }

    private fun timer(
        registry: MeterRegistry,
        endpoint: String,
    ): Timer =
        Timer
            .builder("club_taxonomia.group_query.duration")
            .description("Latencia de la resolución de grupo sobre tags (ADR-0002 D3)")
            .tag("module", "club_taxonomia")
            .tag("endpoint", endpoint)
            .publishPercentiles(PERCENTILE_P50, PERCENTILE_P95, PERCENTILE_P99)
            .register(registry)

    private companion object {
        const val PERCENTILE_P50 = 0.5
        const val PERCENTILE_P95 = 0.95
        const val PERCENTILE_P99 = 0.99
    }
}
