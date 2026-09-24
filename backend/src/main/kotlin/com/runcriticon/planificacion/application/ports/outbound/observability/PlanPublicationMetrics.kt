package com.runcriticon.planificacion.application.ports.outbound.observability

/** Puerto de métricas de negocio del módulo: cuenta planes publicados. */
interface PlanPublicationMetrics {
    /** Registra un plan publicado con éxito. */
    fun planPublished()
}
