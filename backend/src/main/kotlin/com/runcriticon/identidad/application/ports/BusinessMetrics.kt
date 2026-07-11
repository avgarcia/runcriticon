package com.runcriticon.identidad.application.ports

import com.runcriticon.shared.autorizacion.model.Role

/**
 * Puerto de métricas de negocio del módulo identidad (ADR-0011, catálogo en
 * `docs/arquitectura/observabilidad-por-modulo.md` §7).
 */
interface BusinessMetrics {
    fun accountActivated(role: Role)
}
