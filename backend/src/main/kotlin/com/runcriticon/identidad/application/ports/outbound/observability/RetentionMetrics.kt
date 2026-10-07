package com.runcriticon.identidad.application.ports.outbound.observability

/** Métricas del job de retención de `identidad` (ADR-0017 D2): filas borradas por tabla y ejecución. */
interface RetentionMetrics {
    fun purged(
        table: String,
        rows: Int,
    )
}
