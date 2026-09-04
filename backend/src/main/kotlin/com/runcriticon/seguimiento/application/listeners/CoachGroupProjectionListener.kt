package com.runcriticon.seguimiento.application.listeners

import com.runcriticon.clubtaxonomia.api.events.EntrenadorAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.EntrenadorEliminadoDeGrupo
import com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachGroupProjection
import com.runcriticon.seguimiento.domain.CoachId
import com.runcriticon.seguimiento.domain.GroupId
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Mantiene la proyección local `grupo_entrenador` a partir de los eventos de asignación de `club_taxonomia`
 * (LAL-116). Es la base de `CoachAlertReader` para acotar "solo mis grupos" en el panel de alertas.
 *
 * Solo el lado ENTRENADOR (delta, `upsert`/`remove`) — sin `MembresiaDeGrupoCambiada` (alumnos): ver el
 * KDoc de [CoachGroupProjection] para el porqué de la asimetría frente a
 * `planificacion.GroupMembersProjectionListener`, que sí escucha ambos.
 *
 * Mismo patrón que `GroupMembersProjectionListener`: `@ApplicationModuleListener` corre tras el commit del
 * publicador, async, en transacción propia; idempotencia por `event_id` vía [ProcessedEventTracker]; guarda
 * de orden por `occurredAt` en [CoachGroupProjection], porque el outbox no garantiza el orden entre un
 * `Asignado` y un `Eliminado` del mismo entrenador.
 */
@Component
class CoachGroupProjectionListener(
    private val projection: CoachGroupProjection,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de
    // `application` dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("seguimientoProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
    private val mdcRestorer: MdcRestorerForEvents,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Siembra al entrenador en el grupo. */
    @ApplicationModuleListener
    fun on(event: EntrenadorAsignadoAGrupo) {
        withIdempotency(event) {
            projection.upsert(
                clubId = ClubId.of(event.clubId),
                groupId = GroupId.of(event.groupId),
                coachId = CoachId.of(event.aggregateId),
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
                coachId = CoachId.of(event.aggregateId),
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
        const val MODULE = "seguimiento"
        const val LISTENER = "CoachGroupProjectionListener"
    }
}
