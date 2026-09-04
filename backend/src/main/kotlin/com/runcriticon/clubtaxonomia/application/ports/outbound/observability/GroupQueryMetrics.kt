package com.runcriticon.clubtaxonomia.application.ports.outbound.observability

import java.time.Duration

/**
 * Puerto de métricas de latencia de las consultas de resolución de grupo sobre tags (ADR-0002 D3, RNF de LAL-95).
 */
interface GroupQueryMetrics {
    fun resolveMembersRecorded(duration: Duration)

    fun listSummariesRecorded(duration: Duration)
}
