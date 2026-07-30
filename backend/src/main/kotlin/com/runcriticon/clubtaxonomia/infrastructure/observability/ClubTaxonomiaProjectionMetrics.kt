package com.runcriticon.clubtaxonomia.infrastructure.observability

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Catálogo de métricas de las proyecciones locales de este módulo. Hoy una sola: el retraso de la proyección de
 * personas.
 *
 * Tags controlados `module` y `projection`, ambos de cardinalidad fija; nada de `user_id` ni de ids de club. El gauge
 * se declara explícitamente —en vez de anotar un método— para que el bean sea la lista legible de lo que el módulo
 * emite y los tests puedan leer su valor.
 *
 * Micrometer solo guarda una referencia débil al objeto medido, así que el `Gauge` se retiene en un campo: sin eso el
 * recolector podría llevárselo y la métrica pasaría a publicar `NaN`.
 */
@Component
class ClubTaxonomiaProjectionMetrics(
    registry: MeterRegistry,
    personProjection: PersonProjection,
) {
    /**
     * `club_taxonomia.projection_lag_seconds` — segundos transcurridos desde el evento más reciente ya aplicado a la
     * proyección de personas. Alarma por encima de 60 s: a partir de ahí la proyección se considera obsoleta y las
     * decisiones que dependan de ella dejan de ser fiables.
     */
    private val personProjectionLagSeconds: Gauge =
        Gauge
            .builder("club_taxonomia.projection_lag_seconds") { personProjection.lagSeconds().toDouble() }
            .description("Retraso en segundos de la proyección local de personas del club")
            .tag("module", "club_taxonomia")
            .tag("projection", "persona")
            .register(registry)

    /** Valor actual del gauge, para verificarlo en tests sin pasar por el scrape de Prometheus. */
    fun personProjectionLagSeconds(): Double = personProjectionLagSeconds.value()
}
