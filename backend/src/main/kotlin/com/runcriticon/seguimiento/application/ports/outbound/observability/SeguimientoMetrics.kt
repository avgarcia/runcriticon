package com.runcriticon.seguimiento.application.ports.outbound.observability

import com.runcriticon.seguimiento.domain.ReportStatus

/**
 * Puerto de métricas de negocio del módulo seguimiento. Primer contador del catálogo (LAL-30): reportes de
 * sesión registrados, con tag `estado` — cardinalidad fija (los 3 valores de [ReportStatus]).
 */
interface SeguimientoMetrics {
    fun reportRegistered(status: ReportStatus)

    /** Un intento de reporte se rechazó antes de persistir nada. [reason] es un tag de cardinalidad fija —
     * hoy solo `"consentimiento"` (LAL-128 PR2), preparado para sumar motivos futuros sin cambiar la firma. */
    fun reportRejected(reason: String)
}
