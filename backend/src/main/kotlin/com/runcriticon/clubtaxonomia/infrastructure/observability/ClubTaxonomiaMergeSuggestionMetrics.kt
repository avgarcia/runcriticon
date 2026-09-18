package com.runcriticon.clubtaxonomia.infrastructure.observability

import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.MergeSuggestionMetrics
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [MergeSuggestionMetrics]. Expone `club_taxonomia.merge_suggestion.total`, tags
 * `module`, `type` (`MICRO`/`DUPLICADO`) y `event` (`created`/`cleared`/`dismissed`) -- las tres son de
 * cardinalidad fija (2×3), nada de `group_id` ni de ningún otro identificador.
 */
@Component
class ClubTaxonomiaMergeSuggestionMetrics(
    private val registry: MeterRegistry,
) : MergeSuggestionMetrics {
    override fun recalculated(
        type: MergeSuggestionType,
        created: Boolean,
    ) {
        counter(type, if (created) EVENT_CREATED else EVENT_CLEARED).increment()
    }

    override fun dismissed(type: MergeSuggestionType) {
        counter(type, EVENT_DISMISSED).increment()
    }

    private fun counter(
        type: MergeSuggestionType,
        event: String,
    ): Counter =
        Counter
            .builder("club_taxonomia.merge_suggestion.total")
            .description("Sugerencias de fusión de grupos por tipo y evento (LAL-96)")
            .tag("module", "club_taxonomia")
            .tag("type", type.name)
            .tag("event", event)
            .register(registry)

    private companion object {
        const val EVENT_CREATED = "created"
        const val EVENT_CLEARED = "cleared"
        const val EVENT_DISMISSED = "dismissed"
    }
}
