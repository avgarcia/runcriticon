package com.runcriticon.seguimiento.infrastructure.observability

import com.runcriticon.seguimiento.application.listeners.CoachGroupProjectionListener
import com.runcriticon.seguimiento.application.listeners.ConsentProjectionListener
import com.runcriticon.seguimiento.application.listeners.MarkPaceRecalculationListener
import com.runcriticon.seguimiento.application.listeners.PersonalizationProjectionListener
import com.runcriticon.seguimiento.application.listeners.ResolvedPlanProjectionListener
import com.runcriticon.seguimiento.application.ports.outbound.observability.SeguimientoMetrics
import com.runcriticon.seguimiento.application.ports.outbound.persistence.PersonalizedSessionWrite
import com.runcriticon.seguimiento.domain.AdjustmentAction
import com.runcriticon.seguimiento.domain.ReportStatus
import com.runcriticon.shared.events.PendingDeliveryLag
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Catálogo de métricas de este módulo: el retraso de sus tres proyecciones locales (`plan_resuelto_por_alumno`,
 * `consentimiento_alumno` y `grupo_entrenador`, todas como `seguimiento.projection_lag_seconds` con su tag
 * `projection`) y, desde el reporte de sesión, el contador `seguimiento.reportes_total` (implementación del puerto
 * [SeguimientoMetrics]). Tags controlados
 * `module`/`projection`/`estado` — cardinalidad fija (`estado` son los 3 valores de [ReportStatus]), nada de
 * `alumno_id`.
 *
 * Cada `Gauge` se retiene en un campo: Micrometer solo guarda una referencia débil al objeto medido, y sin esto
 * el recolector podría llevárselo y la métrica pasaría a publicar `NaN`. Los `Counter` por estado se registran
 * una vez en el constructor por el mismo motivo, en vez de en cada llamada a `reportRegistered`.
 */
@Component
class SeguimientoProjectionMetrics(
    private val registry: MeterRegistry,
    pendingDeliveries: PendingDeliveryLag,
) : SeguimientoMetrics {
    /**
     * Segundos que lleva pendiente en el outbox la entrega más antigua a cualquiera de los tres listeners que escriben
     * en `plan_resuelto_por_alumno` ([PendingDeliveryLag]). Una personalización que llega antes que su plan queda
     * `FAILED` hasta el siguiente arranque y mantiene el gauge en rojo todo ese tiempo: la vista del alumno va atrasada
     * de verdad.
     */
    private val resolvedPlanLagSeconds: Gauge =
        Gauge
            .builder("seguimiento.projection_lag_seconds") {
                pendingDeliveries.oldestPendingSeconds(RESOLVED_PLAN_WRITERS).toDouble()
            }.description("Retraso en segundos de la proyección local de planes resueltos por alumno")
            .tag("module", "seguimiento")
            .tag("projection", "plan_resuelto_por_alumno")
            .register(registry)

    /**
     * Lo mismo para `consentimiento_alumno`, que solo escribe [ConsentProjectionListener]. Es la puerta del
     * consentimiento explícito de datos de salud (Art. 9.2.a, ADR-0014): con retraso, una revocación aún no aplicada
     * deja pasar reportes y lecturas de salud que ya no tienen base legal.
     */
    private val consentLagSeconds: Gauge =
        Gauge
            .builder("seguimiento.projection_lag_seconds") {
                pendingDeliveries.oldestPendingSeconds(CONSENT_WRITERS).toDouble()
            }.description("Retraso en segundos de la proyección local de consentimientos de datos de salud")
            .tag("module", "seguimiento")
            .tag("projection", "consentimiento_alumno")
            .register(registry)

    /**
     * Lo mismo para `grupo_entrenador`, que solo escribe [CoachGroupProjectionListener]. Acota el panel de alertas a
     * los grupos del entrenador: con retraso, un entrenador recién asignado no ve las alertas de su grupo y uno
     * retirado sigue viéndolas.
     */
    private val coachGroupLagSeconds: Gauge =
        Gauge
            .builder("seguimiento.projection_lag_seconds") {
                pendingDeliveries.oldestPendingSeconds(COACH_GROUP_WRITERS).toDouble()
            }.description("Retraso en segundos de la proyección local de entrenadores por grupo")
            .tag("module", "seguimiento")
            .tag("projection", "grupo_entrenador")
            .register(registry)

    private val reportesTotal: Map<ReportStatus, Counter> =
        ReportStatus.entries.associateWith { status ->
            Counter
                .builder("seguimiento.reportes_total")
                .description("Reportes de sesión registrados por el alumno, por estado")
                .tag("module", "seguimiento")
                .tag("estado", status.name)
                .register(registry)
        }

    private val reajustesTotal: Map<AdjustmentAction, Counter> =
        AdjustmentAction.entries.associateWith { action ->
            Counter
                .builder("seguimiento.reajustes_total")
                .description("Reajustes de día aplicados por el alumno, por acción")
                .tag("module", "seguimiento")
                .tag("accion", action.name)
                .register(registry)
        }

    /** Valor actual del gauge de `plan_resuelto_por_alumno`, para tests sin pasar por el scrape de Prometheus. */
    fun resolvedPlanLagSeconds(): Double = resolvedPlanLagSeconds.value()

    /** Valor actual del gauge de `consentimiento_alumno`, para tests. */
    fun consentLagSeconds(): Double = consentLagSeconds.value()

    /** Valor actual del gauge de `grupo_entrenador`, para tests. */
    fun coachGroupLagSeconds(): Double = coachGroupLagSeconds.value()

    override fun reportRegistered(status: ReportStatus) {
        reportesTotal.getValue(status).increment()
    }

    override fun reportRejected(reason: String) {
        registry
            .counter("seguimiento.reportes_rechazados_total", "module", "seguimiento", "motivo", reason)
            .increment()
    }

    override fun dayRescheduled(action: AdjustmentAction) {
        reajustesTotal.getValue(action).increment()
    }

    override fun personalizationNotApplied(outcome: PersonalizedSessionWrite) {
        registry
            .counter(
                "seguimiento.personalizaciones_no_aplicadas_total",
                "module",
                "seguimiento",
                "motivo",
                outcome.name,
            ).increment()
    }

    private companion object {
        val RESOLVED_PLAN_WRITERS =
            setOf(
                ResolvedPlanProjectionListener::class,
                PersonalizationProjectionListener::class,
                MarkPaceRecalculationListener::class,
            )
        val CONSENT_WRITERS = setOf(ConsentProjectionListener::class)
        val COACH_GROUP_WRITERS = setOf(CoachGroupProjectionListener::class)
    }
}
