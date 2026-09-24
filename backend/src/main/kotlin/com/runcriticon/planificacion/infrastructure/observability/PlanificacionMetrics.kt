package com.runcriticon.planificacion.infrastructure.observability

import com.runcriticon.planificacion.application.ports.outbound.observability.PlanPublicationMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Implementación Micrometer de [PlanPublicationMetrics]. Expone `planificacion.plan_publicado_total`, tag
 * `module` (fijo) — sin `plan_id`, `club_id` ni ningún otro identificador de cardinalidad alta. Es el primer
 * bean de métricas del módulo (hasta ahora `planificacion` no exponía ninguna); mismo patrón que
 * [com.runcriticon.auditoria.infrastructure.observability.AuditoriaMetrics].
 */
@Component
class PlanificacionMetrics(
    registry: MeterRegistry,
) : PlanPublicationMetrics {
    private val planPublicadoCounter: Counter =
        Counter
            .builder("planificacion.plan_publicado_total")
            .description("Planes semanales publicados con éxito")
            .tag("module", "planificacion")
            .register(registry)

    override fun planPublished() {
        planPublicadoCounter.increment()
    }
}
