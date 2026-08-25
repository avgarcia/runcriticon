package com.runcriticon.seguimiento.application.usecases.report

import com.runcriticon.seguimiento.application.ports.outbound.observability.SeguimientoMetrics
import com.runcriticon.seguimiento.domain.ReportStatus

/** Doble en memoria del puerto de métricas, registrando con qué estado se le llamó. */
class InMemorySeguimientoMetrics : SeguimientoMetrics {
    val calls: MutableList<ReportStatus> = mutableListOf()

    override fun reportRegistered(status: ReportStatus) {
        calls += status
    }
}
