package com.runcriticon.planificacion.infrastructure.observability

import com.runcriticon.planificacion.application.ports.outbound.ProjectionFreshness
import com.runcriticon.planificacion.application.ports.outbound.observability.PlanPublicationMetrics
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Catálogo de métricas de este módulo: el contador `planificacion.plan_publicado_total` (implementación de
 * [PlanPublicationMetrics]) y el retraso de la proyección `miembro_grupo`. Tags controlados `module` y
 * `projection`, de cardinalidad fija — sin `plan_id`, `club_id` ni ningún otro identificador de cardinalidad alta.
 *
 * El `Gauge` se retiene en un campo: Micrometer solo guarda una referencia débil al objeto medido, y sin esto el
 * recolector podría llevárselo y la métrica pasaría a publicar `NaN`.
 */
@Component
class PlanificacionMetrics(
    registry: MeterRegistry,
    freshness: ProjectionFreshness,
) : PlanPublicationMetrics {
    private val planPublicadoCounter: Counter =
        Counter
            .builder("planificacion.plan_publicado_total")
            .description("Planes semanales publicados con éxito")
            .tag("module", "planificacion")
            .register(registry)

    /**
     * `planificacion.projection_lag_seconds` — segundos que lleva pendiente la entrega más antigua a la proyección
     * `miembro_grupo` ([ProjectionFreshness.membersProjectionLagSeconds]). Alarma por encima de 60 s (ADR-0009 D9).
     *
     * Se mide sobre el outbox y no como `now() - MAX(last_processed_event_ts)`: esa fórmula crece en cuanto nadie
     * toca la membresía de un grupo, aunque la proyección esté al día, y la alarma saltaría tras cualquier fin de
     * semana tranquilo. Pendiente desde hace más de 60 s sí es retraso real, y una entrega `FAILED` lo mantiene
     * creciendo hasta que se reprocesa.
     */
    private val membersProjectionLagSeconds: Gauge =
        Gauge
            .builder("planificacion.projection_lag_seconds") { freshness.membersProjectionLagSeconds().toDouble() }
            .description("Antigüedad de la entrega pendiente más antigua a la proyección de miembros de grupo")
            .tag("module", "planificacion")
            .tag("projection", "miembro_grupo")
            .register(registry)

    /** Valor actual del gauge, para verificarlo en tests sin pasar por el scrape de Prometheus. */
    fun membersProjectionLagSeconds(): Double = membersProjectionLagSeconds.value()

    override fun planPublished() {
        planPublicadoCounter.increment()
    }
}
