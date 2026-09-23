package com.runcriticon.clubtaxonomia.application.ports.outbound.observability

import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType

/** Puerto de métricas de negocio de las sugerencias de fusión de grupos. */
interface MergeSuggestionMetrics {
    /** Un recálculo confirmó ([created] = `true`) o descartó ([created] = `false`) una sugerencia de [type]. */
    fun recalculated(
        type: MergeSuggestionType,
        created: Boolean,
    )

    /** El admin o el entrenador descartaron una sugerencia de [type]. */
    fun dismissed(type: MergeSuggestionType)
}
