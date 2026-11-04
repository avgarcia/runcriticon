package com.runcriticon.seguimiento.application.ports.outbound.observability

/** Métricas del job de retención de `seguimiento` (ADR-0017 D2): filas borradas por tabla y ejecución. */
interface RetentionMetrics {
    fun purged(
        table: String,
        rows: Int,
    )
}
