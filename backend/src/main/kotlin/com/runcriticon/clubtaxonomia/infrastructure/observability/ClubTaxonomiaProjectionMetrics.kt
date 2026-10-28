package com.runcriticon.clubtaxonomia.infrastructure.observability

import com.runcriticon.clubtaxonomia.application.listeners.HealthConsentListener
import com.runcriticon.clubtaxonomia.application.listeners.PersonProjectionListener
import com.runcriticon.shared.events.PendingDeliveryLag
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Catálogo de métricas de las proyecciones locales de este módulo: el retraso de `persona` y el de
 * `consentimiento_alumno`, ambos como `club_taxonomia.projection_lag_seconds` con su tag `projection`.
 *
 * Tags controlados `module` y `projection`, ambos de cardinalidad fija; nada de `user_id` ni de ids de club. Los
 * gauges se declaran explícitamente —en vez de anotar un método— para que el bean sea la lista legible de lo que el
 * módulo emite y los tests puedan leer su valor.
 *
 * Micrometer solo guarda una referencia débil al objeto medido, así que cada `Gauge` se retiene en un campo: sin eso el
 * recolector podría llevárselo y la métrica pasaría a publicar `NaN`.
 */
@Component
class ClubTaxonomiaProjectionMetrics(
    registry: MeterRegistry,
    pendingDeliveries: PendingDeliveryLag,
) {
    /**
     * Segundos que lleva pendiente en el outbox la entrega más antigua a [PersonProjectionListener], el único que
     * escribe en `persona` ([PendingDeliveryLag]). Alarma por encima de 60 s: a partir de ahí la proyección se
     * considera obsoleta y las decisiones que dependan de ella dejan de ser fiables.
     */
    private val personProjectionLagSeconds: Gauge =
        Gauge
            .builder("club_taxonomia.projection_lag_seconds") {
                pendingDeliveries.oldestPendingSeconds(PERSON_PROJECTION_WRITERS).toDouble()
            }.description("Retraso en segundos de la proyección local de personas del club")
            .tag("module", "club_taxonomia")
            .tag("projection", "persona")
            .register(registry)

    /**
     * Lo mismo para `consentimiento_alumno`, que solo escribe [HealthConsentListener]. Es la proyección que impide
     * asignar valores de salud a quien no ha consentido y la que, al revocar, dispara su retirada (ADR-0014 D18): con
     * retraso, un alumno que ya revocó sigue mostrando esos valores al club.
     */
    private val healthConsentLagSeconds: Gauge =
        Gauge
            .builder("club_taxonomia.projection_lag_seconds") {
                pendingDeliveries.oldestPendingSeconds(HEALTH_CONSENT_WRITERS).toDouble()
            }.description("Retraso en segundos de la proyección local de consentimientos de datos de salud")
            .tag("module", "club_taxonomia")
            .tag("projection", "consentimiento_alumno")
            .register(registry)

    /** Valor actual del gauge de `persona`, para verificarlo en tests sin pasar por el scrape de Prometheus. */
    fun personProjectionLagSeconds(): Double = personProjectionLagSeconds.value()

    /** Valor actual del gauge de `consentimiento_alumno`, para tests. */
    fun healthConsentLagSeconds(): Double = healthConsentLagSeconds.value()

    private companion object {
        val PERSON_PROJECTION_WRITERS = setOf(PersonProjectionListener::class)
        val HEALTH_CONSENT_WRITERS = setOf(HealthConsentListener::class)
    }
}
