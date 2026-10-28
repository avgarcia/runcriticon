package com.runcriticon.planificacion.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada
import com.runcriticon.planificacion.application.listeners.GroupMembersProjectionListener
import com.runcriticon.planificacion.application.ports.outbound.MembersProjectionLag
import com.runcriticon.planificacion.application.ports.outbound.ProjectionFreshness
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
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
 *
 * **La puerta va acotada al grupo** por el `aggregateId` del evento serializado (el grupo cuya membresía cambió),
 * leído como JSON. El mismo test verifica que una entrega real lo serializa con esa clave: si cambiara, el filtro
 * dejaría de casar y la puerta quedaría abierta en silencio. El agregado del gauge no filtra por grupo ni por evento,
 * sino por el prefijo de `listener_id` común a los handlers del listener.
 */
@Repository
class ProjectionFreshnessJdbc(
    private val jdbc: JdbcTemplate,
) : ProjectionFreshness {
    @NoAuthScope(
        justificacion = "event_publication es la tabla compartida del outbox de Spring Modulith; no tiene club_id.",
    )
    override fun membersProjectionLag(groupId: GroupId): MembersProjectionLag =
        jdbc.queryForObject(
            PENDING_PUBLICATIONS_OF_GROUP_SQL,
            { rs: ResultSet, _: Int ->
                val oldestPending: Timestamp? = rs.getTimestamp("mas_antigua")
                MembersProjectionLag(
                    seconds = oldestPending?.let { Duration.between(it.toInstant(), Instant.now()).seconds } ?: 0L,
                    failed = rs.getBoolean("alguna_fallida"),
                )
            },
            EVENT_TYPE,
            LISTENER_ID,
            groupId.value.toString(),
        )!!

    @NoAuthScope(
        justificacion =
            "Agregado de sistema para el gauge projection_lag_seconds sobre event_publication, la tabla compartida " +
                "del outbox: no tiene club_id, no devuelve datos de cliente y lo invoca Micrometer, sin principal.",
    )
    override fun membersProjectionLagSeconds(): Long =
        jdbc
            .queryForObject(OLDEST_PENDING_PUBLICATION_SQL, Timestamp::class.java, LISTENER_ID_PREFIX)
            ?.let { Duration.between(it.toInstant(), Instant.now()).seconds }
            ?: 0L
}

// Top-level, no en un companion object: un val de companion genera un accesor sintético público en la clase
// (`access$getEVENT_TYPE$cp`) que `AuthorizationArchTest` marca como método público de `@Repository` sin
// `@AuthScope`/`@NoAuthScope`.
private val EVENT_TYPE: String = MembresiaDeGrupoCambiada::class.java.name

// Formato de Spring Modulith para el id de un `@ApplicationModuleListener`: `<listener>.on(<evento>)`.
private val LISTENER_ID: String = "${GroupMembersProjectionListener::class.java.name}.on($EVENT_TYPE)"

// Prefijo común a los tres handlers del listener (`MembresiaDeGrupoCambiada` y los dos de entrenadores): todos
// escriben en `miembro_grupo`, así que todos cuentan para su lag. Deja fuera a los demás listeners de esos mismos
// eventos, como `MergeSuggestionListener` de `club_taxonomia`, y a `PlanificacionDeletionListener`, que no proyecta.
private val LISTENER_ID_PREFIX: String = "${GroupMembersProjectionListener::class.java.name}.on("

/**
 * Un agregado sin filas devuelve una fila con `NULL`: de ahí el `COALESCE` en `alguna_fallida`. `status = 'FAILED'` lo
 * pone el propio Spring Modulith cuando una entrega supera `spring.modulith.events.staleness.published`.
 */
private const val PENDING_PUBLICATIONS_OF_GROUP_SQL =
    """
    SELECT MIN(publication_date) AS mas_antigua, COALESCE(BOOL_OR(status = 'FAILED'), FALSE) AS alguna_fallida
    FROM event_publication
    WHERE completion_date IS NULL AND event_type = ? AND listener_id = ?
      AND serialized_event::jsonb ->> 'aggregateId' = ?
    """

/**
 * Sin filtro de `status` (una `FAILED` sigue pendiente) ni de grupo. `starts_with` y no `LIKE`: en un patrón `LIKE`
 * el `_` es comodín, y el prefijo es un nombre de clase.
 */
private const val OLDEST_PENDING_PUBLICATION_SQL =
    """
    SELECT MIN(publication_date)
    FROM event_publication
    WHERE completion_date IS NULL AND starts_with(listener_id, ?)
    """
