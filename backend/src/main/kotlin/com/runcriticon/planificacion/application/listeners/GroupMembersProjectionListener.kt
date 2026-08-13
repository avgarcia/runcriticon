package com.runcriticon.planificacion.application.listeners

import com.runcriticon.clubtaxonomia.api.events.AlumnoAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.AlumnoEliminadoDeGrupo
import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.EntrenadorEliminadoDeGrupo
import com.runcriticon.planificacion.application.ports.outbound.persistence.GroupMembersProjection
import com.runcriticon.planificacion.domain.GroupId
import com.runcriticon.planificacion.domain.PersonId
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Mantiene la proyección local de pertenencia a grupo a partir de los cuatro eventos que publica
 * `club_taxonomia` (LAL-94). Es la base de `CoachGroupLookup` (AC4) y de donde LAL-25 sacará el snapshot de
 * membresía al publicar (AC5) — los eventos de alumno se consumen ya, aunque este ticket no tenga caso de uso
 * que los lea todavía, mismo criterio que `Personalization` en el dominio.
 *
 * Mismo patrón que `PersonProjectionListener` de `club_taxonomia`: `@ApplicationModuleListener` corre tras el
 * commit del publicador, async, en transacción propia; idempotencia por `event_id` vía [ProcessedEventTracker];
 * guarda de orden por `occurredAt` en [GroupMembersProjection], porque el outbox no garantiza el orden entre un
 * `Asignado` y un `Eliminado` de la misma persona.
 */
@Component
class GroupMembersProjectionListener(
    private val projection: GroupMembersProjection,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de `application`
    // dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("planificacionProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Siembra al alumno en el grupo. */
    @ApplicationModuleListener
    fun on(event: AlumnoAsignadoAGrupo) {
        withIdempotency(event) {
            projection.upsert(
                clubId = ClubId.of(event.clubId),
                groupId = GroupId.of(event.groupId),
                personId = PersonId.of(event.aggregateId),
                role = ROLE_ALUMNO,
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        }
    }

    /** Quita al alumno del grupo. */
    @ApplicationModuleListener
    fun on(event: AlumnoEliminadoDeGrupo) {
        withIdempotency(event) {
            projection.remove(
                clubId = ClubId.of(event.clubId),
                groupId = GroupId.of(event.groupId),
                personId = PersonId.of(event.aggregateId),
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        }
    }

    /** Siembra al entrenador en el grupo. */
    @ApplicationModuleListener
    fun on(event: EntrenadorAsignadoAGrupo) {
        withIdempotency(event) {
            projection.upsert(
                clubId = ClubId.of(event.clubId),
                groupId = GroupId.of(event.groupId),
                personId = PersonId.of(event.aggregateId),
                role = ROLE_ENTRENADOR,
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        }
    }

    /** Quita al entrenador del grupo. */
    @ApplicationModuleListener
    fun on(event: EntrenadorEliminadoDeGrupo) {
        withIdempotency(event) {
            projection.remove(
                clubId = ClubId.of(event.clubId),
                groupId = GroupId.of(event.groupId),
                personId = PersonId.of(event.aggregateId),
                eventId = event.eventId,
                occurredAt = event.occurredAt,
            )
        }
    }

    /** Restaura el MDC, filtra reentregas por `event_id` y avisa si la escritura se descartó por orden. */
    private inline fun withIdempotency(
        event: IntegrationEvent,
        applyToProjection: () -> Boolean,
    ) {
        mdcRestorer.restore(
            module = MODULE,
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )
        try {
            if (!processedEvents.markIfNew(LISTENER, event.eventId)) {
                log.debug("Evento {} ya procesado por {}; se descarta", event.eventId, LISTENER)
                return
            }
            if (!applyToProjection()) {
                log.info(
                    "Evento {} descartado por la guarda de orden: la fila ya recogía un evento más reciente",
                    event.eventId,
                )
            }
        } finally {
            mdcRestorer.clear()
        }
    }

    private companion object {
        const val MODULE = "planificacion"
        const val LISTENER = "GroupMembersProjectionListener"
        const val ROLE_ALUMNO = "ALUMNO"
        const val ROLE_ENTRENADOR = "ENTRENADOR"
    }
}
