package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.planificacion.application.listeners.GroupMembersProjectionListener
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
 * `event_publication` tiene una fila por cada par (listener, evento), no una por evento. Desde que se añadió
 * la sugerencia de fusión de micro-grupos,
 * [com.runcriticon.clubtaxonomia.application.listeners.MergeSuggestionListener] también escucha
 * [MembresiaDeGrupoCambiada]; filtrar solo por `event_type` contaba su fila como lag de la proyección de
 * `planificacion`, sin relación con lo que protege ADR-0009 D9 -- bajo carga (su recálculo de duplicados
 * recorre todos los grupos del club) esa fila ajena podía quedar pendiente más de 60 s y bloquear
 * `PublishPlanCommand` sin que la proyección de `planificacion` estuviera realmente atrasada.
 *
 * Por eso el filtro añade `listener_id`, construido en compilación a partir de [GroupMembersProjectionListener]
 * y [MembresiaDeGrupoCambiada] (`::class.java.name`, igual que ya se hacía con `event_type`) en vez de un
 * literal adivinado -- si el formato interno de Spring Modulith cambiara, un literal sin ancla de compilación
 * podría no casar ninguna fila y dejar la puerta fail-closed **abierta** en silencio. `PublishPlanIntegrationTest`
 * verifica contra Postgres real que una entrega real de [GroupMembersProjectionListener] deja este `listener_id`
 * exacto.
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
            jdbc.queryForObject(OLDEST_PENDING_PUBLICATION_SQL, Timestamp::class.java, EVENT_TYPE, LISTENER_ID)
        return oldestPending?.let { Duration.between(it.toInstant(), Instant.now()).seconds } ?: 0L
    }
}

// Top-level, no en un companion object: un val de companion genera un accesor sintético público en la clase
// (`access$getEVENT_TYPE$cp`) que `AuthorizationArchTest` marca como método público de `@Repository` sin
// `@AuthScope`/`@NoAuthScope`.
private val EVENT_TYPE: String = MembresiaDeGrupoCambiada::class.java.name

// Formato de Spring Modulith para el id de un `@ApplicationModuleListener`: `<listener>.on(<evento>)`.
private val LISTENER_ID: String = "${GroupMembersProjectionListener::class.java.name}.on($EVENT_TYPE)"

private const val OLDEST_PENDING_PUBLICATION_SQL =
    """
    SELECT MIN(publication_date) FROM event_publication
    WHERE completion_date IS NULL AND event_type = ? AND listener_id = ?
    """
