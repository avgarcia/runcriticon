package com.runcriticon.auditoria.infrastructure.observability

import com.runcriticon.auditoria.application.ports.outbound.observability.AuditEventMetrics
import com.runcriticon.auditoria.domain.AuditEventType
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [AuditEventMetrics]. Expone `auditoria.eventos_total`, tag `module` (fijo) +
 * `event_type` (cardinalidad fija: los dos valores de [AuditEventType]) — sin `user_id` ni ids de club.
 */
@Component
class AuditoriaMetrics(
    registry: MeterRegistry,
) : AuditEventMetrics {
    private val denegadoCounter: Counter = counter(registry, AuditEventType.ACCESO_DENEGADO)
    private val datosSensiblesCounter: Counter = counter(registry, AuditEventType.ACCESO_DATOS_SENSIBLES)

    override fun recorded(type: AuditEventType) {
        when (type) {
            AuditEventType.ACCESO_DENEGADO -> denegadoCounter.increment()
            AuditEventType.ACCESO_DATOS_SENSIBLES -> datosSensiblesCounter.increment()
        }
    }

    private fun counter(
        registry: MeterRegistry,
        type: AuditEventType,
    ): Counter =
        Counter
            .builder("auditoria.eventos_total")
            .description("Asientos persistidos en auditoria.evento por tipo")
            .tag("module", "auditoria")
            .tag("event_type", type.name)
            .register(registry)
}
