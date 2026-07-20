package com.runcriticon.identidad.application.ports.outbound.observability

import com.runcriticon.shared.autorizacion.model.Role

/**
 * Puerto de métricas de negocio del módulo identidad.
 */
interface BusinessMetrics {
    fun accountActivated(role: Role)
}
