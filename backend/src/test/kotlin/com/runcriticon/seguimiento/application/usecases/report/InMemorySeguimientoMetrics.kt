package com.runcriticon.seguimiento.application.usecases.report

import com.runcriticon.seguimiento.application.ports.outbound.observability.SeguimientoMetrics
import com.runcriticon.seguimiento.domain.ReportStatus

/** Doble en memoria del puerto de métricas, registrando con qué estado y con qué motivo de rechazo se le llamó. */
class InMemorySeguimientoMetrics : SeguimientoMetrics {
    val calls: MutableList<ReportStatus> = mutableListOf()
    val rejections: MutableList<String> = mutableListOf()

    override fun reportRegistered(status: ReportStatus) {
        calls += status
    }

    override fun reportRejected(reason: String) {
        rejections += reason
    }
}
