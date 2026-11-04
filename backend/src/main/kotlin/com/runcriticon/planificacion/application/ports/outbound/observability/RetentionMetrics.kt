package com.runcriticon.planificacion.application.ports.outbound.observability

/** Métricas del job de retención de `planificacion` (ADR-0017 D2): filas borradas por tabla y ejecución. */
interface RetentionMetrics {
    fun purged(
        table: String,
        rows: Int,
    )
}
