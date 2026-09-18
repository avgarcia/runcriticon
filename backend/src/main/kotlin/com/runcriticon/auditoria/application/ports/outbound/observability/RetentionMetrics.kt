package com.runcriticon.auditoria.application.ports.outbound.observability

/** Métricas del job de retención de `auditoria` (ADR-0017 D2): filas borradas por tabla y ejecución. */
interface RetentionMetrics {
    fun purged(
        table: String,
        rows: Int,
    )
}
