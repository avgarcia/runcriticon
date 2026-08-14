package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.planificacion.application.ports.outbound.ProjectionFreshness
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

/**
 * Adaptador de [ProjectionFreshness] sobre el outbox de Spring Modulith (`event_publication`, tabla compartida
 * del framework — sin `club_id`, de ahí [NoAuthScope]).
 *
 * Filtra por `event_type` con el nombre de clase de [MembresiaDeGrupoCambiada] en vez de por `listener_id`: el
 * primero es el nombre totalmente cualificado de la clase del evento (estable, verificable en compilación vía
 * `::class.java.name`); el segundo es un formato interno de Spring Modulith sin garantía documentada — un
 * literal adivinado que no casara ninguna fila haría que la puerta fail-closed de ADR-0009 D9 **fallara
 * abierta** (lag siempre 0), justo lo contrario de lo que exige. `PublishPlanIntegrationTest` verifica contra
 * Postgres real que la fila queda como se espera.
 */
@Repository
class ProjectionFreshnessJdbc(
    private val jdbc: JdbcTemplate,
) : ProjectionFreshness {
    @NoAuthScope(
        justificacion = "event_publication es la tabla compartida del outbox de Spring Modulith; no tiene club_id.",
    )
    override fun membersProjectionLagSeconds(): Long {
        val oldestPending: Timestamp? =
            jdbc.queryForObject(OLDEST_PENDING_PUBLICATION_SQL, Timestamp::class.java, EVENT_TYPE)
        return oldestPending?.let { Duration.between(it.toInstant(), Instant.now()).seconds } ?: 0L
    }
}

// Top-level, no en un companion object: un val de companion genera un accesor sintético público en la clase
// (`access$getEVENT_TYPE$cp`) que `AuthorizationArchTest` marca como método público de `@Repository` sin
// `@AuthScope`/`@NoAuthScope`.
private val EVENT_TYPE: String = MembresiaDeGrupoCambiada::class.java.name

private const val OLDEST_PENDING_PUBLICATION_SQL =
    """
    SELECT MIN(publication_date) FROM event_publication
    WHERE completion_date IS NULL AND event_type = ?
    """
