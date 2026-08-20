package com.runcriticon.auditoria.application.ports.outbound.observability

import com.runcriticon.auditoria.domain.AuditEventType

/** Puerto de métricas de negocio del módulo — separa `application` de Micrometer (infraestructura). */
interface AuditEventMetrics {
    /** Un asiento de [type] se acaba de persistir en `auditoria.evento`. */
    fun recorded(type: AuditEventType)
}
